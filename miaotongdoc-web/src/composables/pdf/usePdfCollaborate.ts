/**
 * usePdfCollaborate —— PDF 协同（Yjs 封装）
 *
 * 职责：
 *   - Yjs Doc + WebsocketProvider 管理
 *   - 标注数据共享（Y.Array<PdfAnnotation>）
 *   - 连接 / 断开协同
 *   - 感知状态：connectionStatus / onlineUsers（可扩展）
 *
 * 设计原则：
 *   - 不在 composable 内部缓存标注的可读 ref，只暴露 Y.Array
 *   - 主组件（或 usePdfAnnotation）通过 observe 派生响应式数据
 *   - 单例：每次 usePdfCollaborate() 创建独立 Doc 实例（防止状态污染）
 *
 * Sprint 1 状态：骨架（TODO 标记），不接 PdfEditor.vue
 */

import * as Y from 'yjs'
import { WebsocketProvider } from 'y-websocket'
import { ref, onUnmounted } from 'vue'

export type AnnotationType =
  | 'highlight'
  | 'comment'
  | 'draw'
  // Phase 10: 形状工具
  | 'rectangle'
  | 'ellipse'
  | 'arrow'
  | 'line'
  | 'underline'
  | 'strikethrough'
  // Phase 10: 图章(用 SVG 文本图章)
  | 'stamp'

export interface PdfAnnotation {
  id: string
  type: AnnotationType
  pageNumber: number
  rect?: { x: number; y: number; width: number; height: number }
  color: string
  content?: string
  points?: number[]
  /** 形状宽度(stroke width,用于 rectangle/ellipse/arrow/line) */
  strokeWidth?: number
  /** 图章文本(仅 stamp 类型) */
  stampText?: string
  userId: number
  userName: string
  createdAt: string
}

export interface PdfAnnotationRect {
  x: number
  y: number
  width: number
  height: number
}

export type ConnectionStatus = 'disconnected' | 'connecting' | 'connected'

export interface UsePdfCollaborateOptions {
  /** 文档唯一标识（用于 Yjs room 名） */
  docKey: string
  /** 当前用户 ID */
  userId: number
  /** 当前用户名（用于 awareness） */
  userName: string
  /** Yjs WebSocket URL（默认 /ws/yjs/） */
  wsBase?: string
  /** JWT Token（用于 WebSocket 鉴权） */
  token?: string | null
}

/**
 * PDF 协同 composable
 *
 * @example
 * ```ts
 * const collab = usePdfCollaborate({ docKey, userId, userName })
 * collab.connect()
 * // 监听变化
 * collab.yAnnotations.observe(() => { ... })
 * ```
 */
export function usePdfCollaborate(options: UsePdfCollaborateOptions) {
  const ydoc = new Y.Doc()
  const yAnnotations = ydoc.getArray<PdfAnnotation>('annotations')

  let provider: WebsocketProvider | null = null
  const connectionStatus = ref<ConnectionStatus>('disconnected')
  const onlineUsers = ref<Array<{ clientId: number; userId?: number; userName?: string }>>([])

  /** 建立 Yjs WebSocket 连接 */
  function connect(): void {
    if (provider) return
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const base = options.wsBase ?? `${protocol}//${window.location.host}/ws/yjs/`
    connectionStatus.value = 'connecting'
    provider = new WebsocketProvider(base, `pdf-${options.docKey}`, ydoc)

    // 设置 awareness 用户信息
    provider.awareness.setLocalStateField('user', {
      id: options.userId,
      name: options.userName,
    })

    provider.on('status', (evt: { status: 'connecting' | 'connected' | 'disconnected' }) => {
      connectionStatus.value = evt.status
    })

    // 监听其他用户 awareness 变化
    provider.awareness.on('change', () => {
      if (!provider) return
      const states = Array.from(provider.awareness.getStates().values())
      onlineUsers.value = states
        .map((s: any) => ({
          clientId: s.clientId ?? 0,
          userId: s.user?.id,
          userName: s.user?.name,
        }))
        .filter((u: any) => u.userId != null)
    })
  }

  /** 断开连接（保留 Y.Doc 用于本地编辑） */
  function disconnect(): void {
    if (provider) {
      provider.destroy()
      provider = null
    }
    connectionStatus.value = 'disconnected'
  }

  /** 完全销毁（包括 Y.Doc） */
  function destroy(): void {
    disconnect()
    ydoc.destroy()
  }

  onUnmounted(destroy)

  return {
    ydoc,
    yAnnotations,
    connectionStatus,
    onlineUsers,
    provider,
    connect,
    disconnect,
    destroy,
  }
}

export type UsePdfCollaborateReturn = ReturnType<typeof usePdfCollaborate>