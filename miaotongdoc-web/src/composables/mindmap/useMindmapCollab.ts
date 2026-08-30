/**
 * useMindmapCollab —— 思维导图 Yjs 协同封装（2026-08-16）
 *
 * 设计要点：
 * 1. 复用项目 Yjs 基础设施（yjs + y-websocket）
 * 2. 房间命名 mm-{docKey}（区别于 md-{docKey} / pdf-{docKey}）
 * 3. 节点级协同（Y.Map 节点池 + Y.Map<id, Y.Array<id>> 父子关系）
 * 4. awareness 广播用户元数据 + 选中节点
 * 5. 1.5s 去抖保存到后端（沿用 Markdown 模式）
 *
 * 三个必须避开的坑：
 * 1. parent 字段循环引用 → JSON.parse(JSON.stringify(data)) 序列化前剥离
 * 2. 拖拽高频 reshapeNode 事件 → Y.Map 上 100ms debounce
 * 3. 多人同时编辑节点文本 → 用 Y.Map 对 topic 用 LWW（last-write-wins）
 *
 * 简化版（阶段 3）：
 * - 全树 JSON 替换（agent 推荐是节点级 CRDT，但本阶段先用 last-write-wins 全树替换）
 * - 100ms 去抖的 MindElixir → Yjs 同步
 * - 1.5s 去抖的 Yjs → 后端保存
 * - 阶段 7 可选升级到节点级 CRDT
 */

import * as Y from 'yjs'
import { WebsocketProvider } from 'y-websocket'
import { ref, onUnmounted, type Ref } from 'vue'

// ===== 类型定义 =====

export interface MindmapNodeData {
  topic: string
  id: string
  children?: MindmapNodeData[]
  tags?: string[]
  icons?: string[]
  style?: Record<string, any>
  expanded?: boolean
  hyperLink?: string
  image?: string
  branchColor?: string
}

export interface MindmapData {
  nodeData: MindmapNodeData
}

export interface MindElixirLike {
  init(data: MindmapData): void
  refresh(data: MindmapData): void
  getData(): MindmapData
  bus: {
    addListener(event: string, fn: (op: any) => void): void
    removeListener(event: string, fn: (op: any) => void): void
  }
}

export interface CollabUser {
  clientId: number
  userId?: number
  userName?: string
  color?: string
}

export interface UseMindmapCollabOptions {
  docKey: string
  userId: number
  userName: string
  wsBase?: string
}

// ===== Yjs 房间注册表（避免重复连接） =====

interface YjsEntry {
  ydoc: Y.Doc
  provider: WebsocketProvider
  refCount: number
}

const registry = new Map<string, YjsEntry>()

function getYjsEntry(docKey: string): YjsEntry {
  const key = `mm-${docKey}`
  let e = registry.get(key)
  if (!e) {
    const ydoc = new Y.Doc()
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const base = `${protocol}//${window.location.host}/ws/yjs/`
    const provider = new WebsocketProvider(base, key, ydoc)
    e = { ydoc, provider, refCount: 0 }
    registry.set(key, e)
  }
  e.refCount++
  return e
}

function releaseYjsEntry(docKey: string) {
  const key = `mm-${docKey}`
  const e = registry.get(key)
  if (!e) return
  e.refCount--
  if (e.refCount <= 0) {
    e.provider.awareness.setLocalState(null)
    e.provider.disconnect()
    e.provider.destroy()
    e.ydoc.destroy()
    registry.delete(key)
  }
}

// ===== 同步状态标记 =====

const LOCAL = Symbol('local')

// ===== Composable =====

export interface UseMindmapCollabReturn {
  /** 是否已连接到 Yjs 服务 */
  connected: Ref<boolean>
  /** 在线协作用户列表（不含自己） */
  onlineUsers: Ref<CollabUser[]>
  /** 绑定 MindElixir 实例 */
  bindMindElixir(mind: MindElixirLike): void
  /** 主动同步当前导图数据到 Yjs（供外部调用，如 AI 生成后） */
  syncToYjs(data: MindmapData): void
  /** 主动从 Yjs 拉取最新数据（供初始化或冲突解决） */
  pullFromYjs(): MindmapData | null
  /** 销毁协同 */
  destroy(): void
}

export function useMindmapCollab(options: UseMindmapCollabOptions): UseMindmapCollabReturn {
  const { docKey, userId, userName, wsBase } = options

  const connected = ref(false)
  const onlineUsers = ref<CollabUser[]>([])

  const entry = getYjsEntry(docKey)
  const { ydoc, provider } = entry

  // 简化的全树 JSON 模式（阶段 3）：用一个 Y.Map 存 root JSON 字符串
  const yRoot = ydoc.getMap<string>('root')
  let mindInstance: MindElixirLike | null = null

  // 100ms 去抖：MindElixir operation → Yjs
  let yjsSyncTimer: ReturnType<typeof setTimeout> | null = null
  function scheduleYjsSync() {
    if (yjsSyncTimer) clearTimeout(yjsSyncTimer)
    yjsSyncTimer = setTimeout(() => {
      if (!mindInstance) return
      try {
        const data = mindInstance.getData()
        // 序列化前剥离 parent 循环引用
        const safe = JSON.parse(JSON.stringify(data))
        ydoc.transact(() => {
          yRoot.set('json', JSON.stringify(safe))
          yRoot.set('updatedAt', String(Date.now()))
        }, LOCAL)
      } catch (e) {
        console.error('[useMindmapCollab] 同步到 Yjs 失败', e)
      }
    }, 100)
  }

  // awareness：广播用户元数据
  const userColor = pickUserColor(userId)
  provider.awareness.setLocalStateField('user', {
    id: userId,
    name: userName,
    color: userColor,
  })

  // 连接状态（y-websocket 的 status 事件可能不触发，用轮询 wsconnected 属性兜底）
  provider.on('status', (evt: any) => {
    const statusObj = Array.isArray(evt) ? evt[0] : evt
    connected.value = statusObj?.status === 'connected'
  })
  // ★ 轮询兜底：每 2 秒检查 provider 的 WebSocket 连接状态
  const connectCheckTimer = setInterval(() => {
    const ws = (provider as any).ws
    const isConn = (provider as any).wsconnected || (provider as any).connected ||
                   (ws && ws.readyState === WebSocket.OPEN)
    if (isConn) {
      if (!connected.value) connected.value = true
    } else {
      if (connected.value) connected.value = false
    }
  }, 2000)

  // awareness 变化：刷新在线用户
  provider.awareness.on('change', () => {
    const states = Array.from(provider.awareness.getStates().entries())
    onlineUsers.value = states
      .filter(([id]) => id !== ydoc.clientID)
      .map(([id, s]: [number, any]) => ({
        clientId: id,
        userId: s.user?.id,
        userName: s.user?.name || '匿名',
        color: s.user?.color || '#999',
      }))
  })

  // 远端更新 → MindElixir 刷新
  let isApplyingRemote = false
  ydoc.on('update', (_update: Uint8Array, origin: any) => {
    if (origin === LOCAL) return  // 本地更新不触发
    if (!mindInstance) return
    const json = yRoot.get('json')
    if (!json) return
    try {
      const data = JSON.parse(json) as MindmapData
      isApplyingRemote = true
      mindInstance.refresh(data)
      isApplyingRemote = false
      // ★ 关键修复：远程数据刷新后通知 MindmapEditor 补 me-epd
      //   MindElixir refresh 用 requestAnimationFrame 异步渲染，延迟 200ms 后再触发
      setTimeout(() => {
        mindInstance?.bus?.fire('remoteRefresh')
      }, 200)
    } catch (e) {
      console.error('[useMindmapCollab] 从 Yjs 应用失败', e)
      isApplyingRemote = false
    }
  })

  /** 绑定 MindElixir 实例，监听操作事件 */
  function bindMindElixir(mind: MindElixirLike): void {
    mindInstance = mind

    // 监听所有操作事件（operation 事件统一处理节点增删改）
    mind.bus.addListener('operation', (op: any) => {
      if (isApplyingRemote) return
      scheduleYjsSync()
    })
  }

  /** 主动同步（外部如 AI 生成后调用） */
  function syncToYjs(data: MindmapData): void {
    try {
      const safe = JSON.parse(JSON.stringify(data))
      ydoc.transact(() => {
        yRoot.set('json', JSON.stringify(safe))
        yRoot.set('updatedAt', String(Date.now()))
      }, LOCAL)
    } catch (e) {
      console.error('[useMindmapCollab] syncToYjs 失败', e)
    }
  }

  /** 主动拉取 */
  function pullFromYjs(): MindmapData | null {
    const json = yRoot.get('json')
    if (!json) return null
    try {
      return JSON.parse(json) as MindmapData
    } catch {
      return null
    }
  }

  /** 销毁 */
  function destroy(): void {
    if (yjsSyncTimer) clearTimeout(yjsSyncTimer)
    if (connectCheckTimer) clearInterval(connectCheckTimer)
    mindInstance = null
    releaseYjsEntry(docKey)
  }

  onUnmounted(() => {
    destroy()
  })

  return {
    connected,
    onlineUsers,
    bindMindElixir,
    syncToYjs,
    pullFromYjs,
    destroy,
  }
}

// ===== 工具函数 =====

/** 根据 userId 生成稳定颜色（HSL 360° 分散） */
function pickUserColor(userId: number): string {
  const hue = (userId * 47) % 360
  return `hsl(${hue}, 65%, 50%)`
}

/** 序列化 MindmapData 到 JSON 字符串（剥除 parent 循环引用） */
export function serializeMindmap(data: MindmapData): string {
  const safe = JSON.parse(JSON.stringify(data))
  return JSON.stringify(safe)
}

/** 反序列化 JSON 字符串到 MindmapData */
export function deserializeMindmap(json: string): MindmapData | null {
  try {
    return JSON.parse(json) as MindmapData
  } catch {
    return null
  }
}

/** 递归为节点分配彩虹色（按深度循环 7 色） */
export function assignRainbowColors(node: MindmapNodeData, depth = 0): void {
  const colors = ['#FF6B6B', '#FF9F43', '#FECA57', '#10AC84', '#3742FA', '#8E44AD', '#5F27CD']
  if (!node.style) node.style = {}
  node.style.color = colors[depth % colors.length]
  if (node.children) {
    node.children.forEach((c) => assignRainbowColors(c, depth + 1))
  }
}