/**
 * AI JSON 工具函数（2026-08-16）
 *
 * 防御 LLM 输出不稳定的 3 层解析兜底：
 * 1. 直接 JSON.parse
 * 2. 去掉 ```json ... ``` Markdown 包裹
 * 3. 切片首对花括号
 *
 * 加上 AI JSON → MindElixir v5 数据映射。
 */

const VALID_ICONS = new Set([
  'priority', 'star', 'task', 'flag',
  'calendar', 'message', 'idea', 'heart',
])

/** JSON 解析 3 层兜底 */
export function parseAiJson(raw: string): any {
  if (!raw) throw new Error('AI 输出为空')

  // 1. 直接解析
  try {
    return JSON.parse(raw)
  } catch { /* fall through */ }

  // 2. 去掉 ```json ... ``` Markdown 包裹
  const mdMatch = raw.match(/```(?:json)?\s*([\s\S]+?)\s*```/)
  if (mdMatch) {
    try {
      return JSON.parse(mdMatch[1])
    } catch { /* fall through */ }
  }

  // 3. 切片首对花括号
  const start = raw.indexOf('{')
  const end = raw.lastIndexOf('}')
  if (start !== -1 && end > start) {
    try {
      return JSON.parse(raw.slice(start, end + 1))
    } catch { /* fall through */ }
  }

  throw new Error('AI 输出非合法 JSON')
}

/** 提取 useAiChat 累积的文本 */
export function extractText(messages: any[]): string {
  if (!messages?.length) return ''
  const last = messages[messages.length - 1]
  if (!last?.parts) return ''
  return last.parts.map((p: any) => p.text || '').join('')
}

/** 限制图标枚举到合法值 */
function normalizeIcons(arr: any): string[] {
  if (!Array.isArray(arr)) return []
  return arr
    .filter((x: any) => typeof x === 'string' && VALID_ICONS.has(x))
    .slice(0, 2)  // MindElixir 限制 ≤2
}

/** AI JSON → MindElixir v5 数据（递归, id 用 uuid, icons ≤2） */
export function aiJsonToMindmapData(ai: any): any {
  const walk = (n: any): any => ({
    topic: String(n?.topic || '').slice(0, 50),
    id: crypto.randomUUID(),
    icons: normalizeIcons(n?.icons),
    children: Array.isArray(n?.children) ? n.children.map(walk).filter((c: any) => c.topic) : [],
  })
  const root = ai?.root
  if (!root?.topic) {
    throw new Error('AI 输出缺 root.topic')
  }
  return { nodeData: walk(root) }
}

/** MindElixirData → 扁平文本（用于 AI 总结 prompt 输入） */
export function flattenToText(node: any, depth = 0): string {
  if (!node) return ''
  const indent = '  '.repeat(depth)
  const topic = String(node.topic || '(空)')
  let s = `${indent}- ${topic}\n`
  if (Array.isArray(node.children)) {
    for (const c of node.children) {
      s += flattenToText(c, depth + 1)
    }
  }
  return s
}

/** 获取节点的父链（用于 expand 上下文） */
export function getParentChain(root: any, targetId: string): string[] {
  const path: string[] = []
  function walk(node: any, ancestors: string[]): boolean {
    if (!node) return false
    const next = [...ancestors, node.topic]
    if (node.id === targetId) {
      path.push(...next)
      return true
    }
    if (Array.isArray(node.children)) {
      for (const c of node.children) {
        if (walk(c, next)) return true
      }
    }
    return false
  }
  walk(root, [])
  return path
}

/** 获取同级兄弟节点主题（用于 expand 上下文） */
export function getSiblings(root: any, targetId: string): string[] {
  const result: string[] = []
  function walk(node: any): boolean {
    if (!node) return false
    if (node.id === targetId) return true
    if (Array.isArray(node.children)) {
      for (const c of node.children) {
        if (walk(c)) {
          // 找到目标后，把同级主题收集
          result.push(...node.children.filter((x: any) => x.id !== targetId).map((x: any) => x.topic))
          return true
        }
      }
    }
    return false
  }
  walk(root)
  return result
}