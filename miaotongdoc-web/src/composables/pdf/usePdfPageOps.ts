/**
 * usePdfPageOps —— PDF 页面操作(merge/delete/rotate/extract/reorder)
 *
 * Phase 3 核心:
 *   - 包装 pdfApi 5 个方法,统一返回 {success, message, filePath}
 *   - 后端原子化覆盖当前文档,前端只需 reload 渲染
 *   - 配合 usePdfTextEditor.flush() 在操作前 flush 未保存编辑
 *   - 配合 usePdfRenderer.load() 重载新文件
 *
 * 设计原则:
 *   - 每个操作返回 boolean + message,失败 toast 提示
 *   - busy 状态防双击
 *   - onSaved 回调:父组件触发 renderer.load + textEditor.clearCache
 */

import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pdfApi, type PageOpResult } from '@/api/pdf'

export interface UsePdfPageOpsOptions {
  docId: number
  /** 当前 fileUrl(返回时用于 cache-bust) */
  fileUrl: string
  /** 操作前 hook(可在此 flush 未保存编辑,返回 false 阻止操作) */
  beforeOp?: () => boolean | Promise<boolean>
  /** 操作后 hook(父组件触发 renderer 重载) */
  onSaved?: (result: PageOpResult) => void
  /** 错误回调 */
  onError?: (err: Error, op: string) => void
}

export function usePdfPageOps(options: UsePdfPageOpsOptions) {
  const { docId, beforeOp, onSaved, onError } = options

  const busy = ref(false)
  const lastResult = ref<PageOpResult | null>(null)

  /** 通用执行包装 */
  async function run(opName: string, fn: () => Promise<PageOpResult>) {
    if (busy.value) {
      ElMessage.warning('操作进行中,请稍候')
      return null
    }

    // 触发前 hook
    if (beforeOp) {
      const proceed = await beforeOp()
      if (!proceed) return null
    }

    busy.value = true
    try {
      const result = await fn()
      lastResult.value = result
      if (result.success) {
        ElMessage.success({
          message: result.message || `${opName} 成功`,
          duration: 2000,
        })
        onSaved?.(result)
        return result
      } else {
        ElMessage.error(result.message || `${opName} 失败`)
        return null
      }
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e))
      const msg = err.message || `${opName} 失败`
      ElMessage.error(msg)
      onError?.(err, opName)
      return null
    } finally {
      busy.value = false
    }
  }

  // ==================== 操作 API ====================

  /**
   * 合并多个 PDF(目标文档 = docIds[0],其余追加)
   */
  async function merge(documentIds: number[]) {
    if (documentIds.length < 2) {
      ElMessage.warning('请选择至少 2 个 PDF 进行合并')
      return null
    }
    return run('合并 PDF', () => pdfApi.merge({ documentIds }))
  }

  /**
   * 旋转单页 90°
   */
  async function rotatePage(pageNum: number, degrees = 90) {
    return run(`旋转第 ${pageNum} 页`, () =>
      pdfApi.rotatePages(docId, { pages: [pageNum], degrees }),
    )
  }

  /**
   * 旋转多页
   */
  async function rotatePages(pages: number[], degrees = 90) {
    if (pages.length === 0) return null
    return run(`旋转 ${pages.length} 页`, () =>
      pdfApi.rotatePages(docId, { pages, degrees }),
    )
  }

  /**
   * 删除单页(带确认)
   */
  async function deletePage(pageNum: number) {
    try {
      await ElMessageBox.confirm(
        `确定删除第 ${pageNum} 页?此操作不可撤销。`,
        '删除页面',
        {
          type: 'warning',
          confirmButtonText: '删除',
          cancelButtonText: '取消',
        },
      )
    } catch {
      return null
    }
    return run(`删除第 ${pageNum} 页`, () => pdfApi.deletePage(docId, pageNum))
  }

  /**
   * 提取多页为新 PDF(替换当前文档)
   */
  async function extractPages(pages: number[]) {
    if (pages.length === 0) {
      ElMessage.warning('请选择至少 1 页进行提取')
      return null
    }
    return run(`提取 ${pages.length} 页`, () => pdfApi.extractPages(docId, { pages }))
  }

  /**
   * 重排页面
   * @param newOrder 新的页码顺序,如 [3,1,2] 表示把第3页放最前
   */
  async function reorderPages(newOrder: number[]) {
    if (newOrder.length === 0) return null
    return run('重排页面', () => pdfApi.reorderPages(docId, { newOrder }))
  }

  /**
   * 计算拖拽重排后的 newOrder
   * @param from 当前页(1-indexed)
   * @param to 目标位置页
   * @param totalPages 总页数
   * @returns 新的页码顺序数组
   */
  function computeReorder(from: number, to: number, totalPages: number): number[] {
    const order = Array.from({ length: totalPages }, (_, i) => i + 1)
    const [moved] = order.splice(from - 1, 1)
    order.splice(to - 1, 0, moved)
    return order
  }

  // ==================== 辅助 ====================

  /** 根据 PageOpResult 生成新的 fileUrl(添加 cache-bust) */
  function bustUrl(result: PageOpResult): string {
    const base = options.fileUrl.split('?')[0]
    const ts = Date.now()
    return `${base}?v=${ts}&op=${encodeURIComponent(result.filePath)}`
  }

  return {
    busy,
    lastResult,
    merge,
    rotatePage,
    rotatePages,
    deletePage,
    extractPages,
    reorderPages,
    computeReorder,
    bustUrl,
  }
}

export type UsePdfPageOpsReturn = ReturnType<typeof usePdfPageOps>
