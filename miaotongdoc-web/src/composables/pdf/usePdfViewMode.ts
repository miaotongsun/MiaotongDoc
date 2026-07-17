/**
 * usePdfViewMode —— V3 视图模式(单页 / 连续 / 双页)
 *
 * 职责:
 *   - 切换视图模式,通知 PdfCanvas 重排
 *   - 自定义缩放 + 适合宽度 / 适合页面 / 实际大小
 *   - 提供 effectiveScale 给 canvas / thumbnail 使用
 *
 * 与 usePdfRenderer 配合:调用 zoom-in/out/fit-* 时实际触发 renderer 重渲染
 */

import { ref, computed } from 'vue'

export type ViewMode = 'single' | 'continuous' | 'facing'
export type ZoomMode = 'fit-width' | 'fit-page' | 'actual' | 'custom'

export function usePdfViewMode() {
  const viewMode = ref<ViewMode>('continuous')
  const zoomMode = ref<ZoomMode>('fit-width')
  /** 当前缩放比例(custom 模式时直接使用;fit-* 时由 fit 计算) */
  const scale = ref(1.2)

  const isCustomZoom = computed(() => zoomMode.value === 'custom')

  function setViewMode(mode: ViewMode) {
    viewMode.value = mode
  }

  function setZoom(mode: ZoomMode, value?: number) {
    zoomMode.value = mode
    if (mode === 'custom' && value) {
      scale.value = Math.max(0.25, Math.min(value, 4))
    } else if (mode === 'actual') {
      scale.value = 1.0
    }
  }

  function setScale(newScale: number) {
    zoomMode.value = 'custom'
    scale.value = Math.max(0.25, Math.min(newScale, 4))
  }

  /** 循环切换 single → continuous → facing → single */
  function cycleViewMode() {
    const order: ViewMode[] = ['single', 'continuous', 'facing']
    const idx = order.indexOf(viewMode.value)
    viewMode.value = order[(idx + 1) % order.length]
  }

  return {
    viewMode,
    zoomMode,
    scale,
    isCustomZoom,
    setViewMode,
    setZoom,
    setScale,
    cycleViewMode,
  }
}

export type UsePdfViewModeReturn = ReturnType<typeof usePdfViewMode>