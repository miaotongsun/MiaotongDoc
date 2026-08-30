/**
 * useMindmapAi —— 思维导图 AI 4 个能力封装（2026-08-16）
 *
 * 复用项目现有 AI 基础设施：
 * - 后端 `/api/ai/chat-stream` (AiChatSseController, OpenAI 兼容 SSE)
 * - 前端 `useAiChat` composable (流式 + rAF 节流 + AbortController + DOMPurify)
 * - 前端 `AiPanel.vue` 组件（消息气泡 + 快速 chips）
 *
 * 后端**零改动**，仅前端封装 3 个能力 + 1 个并入生成的智能图标：
 * 1. generate-mindmap: 主题 → 完整节点树
 * 2. expand-node: 选中节点 → 3-5 子节点
 * 3. summarize-mindmap: 节点树 → 200 字总结
 * 4. suggest-icons: 并入 generate/expand
 */

import { ref, computed, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAiChat } from '@/composables/useAiChat'
import { MindmapPrompts, MINDMAP_ICONS } from './mindmapPrompts'
import {
  parseAiJson,
  extractText,
  aiJsonToMindmapData,
  flattenToText,
  getParentChain,
  getSiblings,
} from './aiJsonUtils'

export interface UseMindmapAiOptions {
  docId: Ref<number | string | null | undefined>
}

export function useMindmapAi(options: UseMindmapAiOptions) {
  const { docId } = options

  // 3 个独立 chat 流（生成 / 扩写 / 总结）
  const chatGenerate = useAiChat({ docId, endpoint: 'chat-stream' })
  const chatExpand = useAiChat({ docId, endpoint: 'chat-stream' })
  const chatSummarize = useAiChat({ docId, endpoint: 'chat-stream' })

  const summary = ref('')
  const showAiPanel = ref(false)

  /** 1. AI 生成思维导图（替换整个导图） */
  async function generate(topic: string, applyToMind: (data: any) => void): Promise<boolean> {
    if (!topic?.trim()) {
      ElMessage.warning('请输入主题')
      return false
    }
    const { system, user } = MindmapPrompts.GENERATE(topic.trim())
    try {
      await chatGenerate.sendUserMessage(user, system)
      const text = extractText(chatGenerate.messages.value)
      const ai = parseAiJson(text)
      const data = aiJsonToMindmapData(ai)
      applyToMind(data)
      ElMessage.success('已生成思维导图')
      return true
    } catch (e: any) {
      console.error('generate failed', e)
      ElMessage.error(`生成失败: ${e?.message || '未知错误'}`)
      return false
    }
  }

  /** 2. AI 节点扩写（添加子节点到当前选中节点） */
  async function expand(currentNode: any, root: any, applyToMind: (children: any[]) => void): Promise<boolean> {
    if (!currentNode) {
      ElMessage.warning('请先选中一个节点')
      return false
    }
    const parentChain = getParentChain(root, currentNode.id).join(' > ')
    const siblings = getSiblings(root, currentNode.id).join(', ')
    const { system, user } = MindmapPrompts.EXPAND(currentNode.topic, parentChain, siblings)
    try {
      await chatExpand.sendUserMessage(user, system)
      const text = extractText(chatExpand.messages.value)
      const ai = parseAiJson(text)
      const newChildren = aiJsonToMindmapData(ai).nodeData.children || []
      if (newChildren.length === 0) {
        ElMessage.warning('AI 未生成有效节点')
        return false
      }
      applyToMind(newChildren)
      ElMessage.success(`已扩写 ${newChildren.length} 个子节点`)
      return true
    } catch (e: any) {
      console.error('expand failed', e)
      ElMessage.error(`扩写失败: ${e?.message || '未知错误'}`)
      return false
    }
  }

  /** 3. AI 总结（展示在 AiPanel 中） */
  async function summarize(root: any, showPanel = true): Promise<boolean> {
    const flat = flattenToText(root)
    if (!flat.trim()) {
      ElMessage.warning('导图为空，无法总结')
      return false
    }
    if (showPanel) showAiPanel.value = true
    const { system, user } = MindmapPrompts.SUMMARIZE(flat)
    try {
      await chatSummarize.sendUserMessage(user, system)
      summary.value = extractText(chatSummarize.messages.value).slice(0, 220)
      ElMessage.success('总结完成')
      return true
    } catch (e: any) {
      console.error('summarize failed', e)
      ElMessage.error(`总结失败: ${e?.message || '未知错误'}`)
      return false
    }
  }

  function stopAll() {
    chatGenerate.stop()
    chatExpand.stop()
    chatSummarize.stop()
  }

  function clearAll() {
    chatGenerate.clear()
    chatExpand.clear()
    chatSummarize.clear()
    summary.value = ''
  }

  const generateStatus = computed(() => chatGenerate.status.value)
  const expandStatus = computed(() => chatExpand.status.value)
  const summarizeStatus = computed(() => chatSummarize.status.value)

  return {
    // 流式聊天（用于渲染 AiPanel）
    chatGenerate,
    chatExpand,
    chatSummarize,
    // 状态
    generateStatus,
    expandStatus,
    summarizeStatus,
    summary,
    showAiPanel,
    // 能力
    generate,
    expand,
    summarize,
    // 控制
    stopAll,
    clearAll,
    // 枚举
    icons: MINDMAP_ICONS,
  }
}