/**
 * 思维导图 AI 提示词模板（2026-08-16）
 *
 * 复用现有 /api/ai/chat-stream 端点（OpenAI 兼容 SSE）。
 * 后端**零改动**，仅前端封装。
 */

/** MindElixir v5 内置 8 个图标枚举 */
export const MINDMAP_ICONS = [
  'priority', 'star', 'task', 'flag',
  'calendar', 'message', 'idea', 'heart',
] as const

export type MindmapIcon = typeof MINDMAP_ICONS[number]

export const MindmapPrompts = {
  /** 1. generate-mindmap: 主题 → 完整节点树 JSON（图标并入生成） */
  GENERATE: (topic: string) => ({
    system: `你是思维导图生成助手。只输出 JSON,不要 Markdown 包裹或解释文字。

JSON Schema (MindElixir 兼容):
{
  "root": {
    "topic": "string (中文, ≤ 12 字)",
    "icons": ["${MINDMAP_ICONS.join('|')}"],
    "children": [
      { "topic": "string (≤ 12 字)", "icons": [...], "children": [...] }
    ]
  }
}

约束:
- 一级子节点 4-7 个
- 总节点数 15-40
- 层级深度 2-3 级
- 节点 topic 用中文短语,简洁准确
- icons 选择: 重要→priority/star; 任务→task/calendar; 风险→flag/message; 创意→idea/heart`,
    user: `主题: ${topic}`,
  }),

  /** 2. expand-node: 选中节点 + 上下文 → 3-5 子节点 */
  EXPAND: (topic: string, parentChain: string, siblingTopics: string) => ({
    system: `你是思维导图节点扩写助手。只输出 JSON。

返回格式:
{
  "root": {
    "topic": "${topic}",
    "icons": ["${MINDMAP_ICONS.join('|')}"],
    "children": [
      { "topic": "string (≤ 10 字)", "icons": [...], "children": [] }
    ]
  }
}

约束:
- 3-5 个子节点
- 与父节点语义相关
- 与兄弟节点互斥不重复
- 中文短语`,
    user: `父链: ${parentChain || '(无)'}
兄弟: ${siblingTopics || '(无)'}
扩展节点: ${topic}`,
  }),

  /** 3. summarize-mindmap: 节点树 → 200 字纯文本总结 */
  SUMMARIZE: (nodeTreeText: string) => ({
    system: `你是思维导图总结助手。输出纯文本,不要 JSON,不要 Markdown 包裹。

要求:
- 一段话,不分点
- 覆盖一级分支的核心内容
- 字数 180-220 之间
- 用中文`,
    user: `请总结以下思维导图:\n\n${nodeTreeText}`,
  }),

  /** 4. suggest-icons 已并入 GENERATE/EXPAND, 不独立 */
}

/** 可用 AI 模型说明（前端展示用） */
export const AI_HINT = {
  GENERATE: '输入主题，AI 自动生成完整思维导图',
  EXPAND: '选中节点后扩展子节点',
  SUMMARIZE: '总结当前思维导图核心内容',
}