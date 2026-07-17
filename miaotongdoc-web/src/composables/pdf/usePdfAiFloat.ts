/**
 * usePdfAiFloat —— PDF AI 浮窗逻辑编排
 *
 * Phase 4 核心:把 useAiChat + usePdfAiVision + usePdfExtractTerms + usePdfOptimizeOcr
 * 串成一个 composable,直接喂给现有的 <PdfAiFloatPanel> 组件。
 *
 * 职责:
 *   - 管理 chat 流(useAiChat,chat-stream 端点)
 *   - 管理 VQA(usePdfAiVision, vision/stream 端点)
 *   - 提供抽取条款 / 优化 OCR 入口
 *   - 暴露 PdfAiFloatPanel 所需的 props 形状
 *
 * 来源:plans/twinkly-knitting-waterfall.md § 场景 S3/S6/S7
 */

import { computed } from 'vue'
import { useAiChat } from '@/composables/useAiChat'
import { usePdfAiVision } from '@/composables/pdf/usePdfAiVision'
import { usePdfExtractTerms } from '@/composables/pdf/usePdfExtractTerms'
import { usePdfOptimizeOcr } from '@/composables/pdf/usePdfOptimizeOcr'

export interface UsePdfAiFloatOptions {
  docId: number
  /** 是否启用(默认 true) */
  enabled?: boolean
}

export function usePdfAiFloat(options: UsePdfAiFloatOptions) {
  const { docId, enabled = true } = options

  // 通用流式聊天
  const chat = useAiChat({
    docId: computed(() => (enabled ? docId : null)),
    endpoint: 'chat-stream',
  })

  // 视觉问答(VLM)
  const vision = usePdfAiVision({ docId })

  // 抽取合同条款(SSE 流式)
  const extractTerms = usePdfExtractTerms({ docId })

  // OCR 结果优化
  const optimizeOcr = usePdfOptimizeOcr({ docId })

  const isStreaming = computed(
    () =>
      chat.status.value === 'streaming' ||
      chat.status.value === 'submitted' ||
      vision.status.value === 'streaming',
  )

  return {
    /** 直接交给 PdfAiFloatPanel */
    chat,
    vision,
    extractTerms,
    optimizeOcr,
    isStreaming,
    // 控制方法(父组件按需调用)
    clear: chat.clear,
    stop: chat.stop,
  }
}

export type UsePdfAiFloatReturn = ReturnType<typeof usePdfAiFloat>
