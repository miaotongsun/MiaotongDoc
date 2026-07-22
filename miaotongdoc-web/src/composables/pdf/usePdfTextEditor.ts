/**
 * usePdfTextEditor —— PDF 原生页面文本编辑逻辑
 *
 * Phase 2 核心:用户在 PDF 原页面直接点击文字修改,改动通过
 * 后端 PdfToolService.applyTextEdits(PDFBox 文字流写回)落盘。
 *
 * 设计:
 *   - 编辑流程:
 *     1. 选中 "T 文本编辑" 工具
 *     2. PdfTextEditorLayer 调 loadPositions(pageNum) 加载坐标
 *     3. 用户点击 token → contenteditable → blur 触发 applyEdit()
 *     4. 防抖 600ms 后批量提交到后端
 *     5. 后端原子化覆盖 storage 文件
 *     6. 前端 reload 渲染 + 重新加载 positions
 *
 *   - 状态:perPage 缓存 positions(避免重复请求)
 *   - 错误兜底:API 失败时回滚 UI,toast 提示
 *   - 与 Yjs 标注互不影响:文本编辑是文档级持久,标注是会话级临时
 *
 * 来源:plans/twinkly-knitting-waterfall.md § 场景 S1
 */

import { ref, computed, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { pdfApi, type PdfTextPosition, type PdfTextEdit } from '@/api/pdf'

export type EditState = 'idle' | 'editing' | 'saving' | 'saved' | 'error'

export interface PendingEdit extends PdfTextEdit {
  /** 内部唯一 id */
  id: string
  /** 编辑状态(用于 UI 反馈) */
  state: EditState
  /** 原始 token text(用于回滚) */
  originalText: string
  /** 原始坐标(用于回滚) */
  originalX: number
  originalY: number
}

export interface UsePdfTextEditorOptions {
  /** 文档 ID */
  docId: number
  /** 缓存 key(可选,默认按页码) */
  cacheKey?: string
  /** 防抖延迟(ms) */
  debounceMs?: number
  /** 自动保存回调(应用后由父组件触发文件重载) */
  onSaved?: () => void
  /** 错误回调 */
  onError?: (err: Error) => void
  /** Phase 13.11: 是否自动提交(默认 true;编辑模式设 false 由用户手动保存) */
  autoCommit?: boolean
}

/**
 * 简易 debounce 实现
 */
function debounce<T extends (...args: any[]) => void>(fn: T, ms: number): T & { cancel: () => void; flush: () => void } {
  let timer: ReturnType<typeof setTimeout> | null = null
  let lastArgs: any[] | null = null
  const wrapped = ((...args: any[]) => {
    lastArgs = args
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      timer = null
      if (lastArgs) fn(...lastArgs)
    }, ms)
  }) as T & { cancel: () => void; flush: () => void }
  wrapped.cancel = () => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }
  wrapped.flush = () => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    if (lastArgs) fn(...lastArgs)
  }
  return wrapped
}

/**
 * PDF 文本编辑 composable
 *
 * @example
 * ```ts
 * const editor = usePdfTextEditor({ docId, onSaved: () => location.reload() })
 * await editor.loadPositions(1)  // 加载第 1 页坐标
 * editor.applyEdit({ ... })  // 应用编辑
 * await editor.flush()  // 立即提交
 * ```
 */
export function usePdfTextEditor(options: UsePdfTextEditorOptions) {
  const {
    docId,
    debounceMs = 600,
    onSaved,
    onError,
    autoCommit = true,
  } = options

  // ========== 状态 ==========
  /** 所有页的 positions(按页码缓存) */
  const positionsByPage = ref<Map<number, PdfTextPosition[]>>(new Map())
  /** 总页数(从 positions 接口响应推断) */
  const totalPagesFromPositions = ref(0)
  /** 待提交编辑(按页码分组) */
  const pendingByPage = ref<Map<number, PendingEdit[]>>(new Map())
  /** 各编辑的状态(id → EditState) */
  const editStates = ref<Map<string, EditState>>(new Map())
  /** 加载状态 */
  const loadingPositions = ref<Set<number>>(new Set())
  /** 是否有未保存的编辑 */
  const dirty = computed(() => {
    for (const arr of pendingByPage.value.values()) {
      if (arr.length > 0) return true
    }
    return false
  })

  // ========== 防抖提交 ==========
  const debouncedCommit = debounce(commitNow, debounceMs)

  /**
   * 提交所有待编辑(立即执行)
   * Phase 13.11: 支持指定 targetDocId(另存为新文档时提交到新文档)
   */
  async function commitNow(targetDocId?: number): Promise<void> {
    const targetId = targetDocId ?? docId
    const groups: Array<{ pageNum: number; edits: PendingEdit[] }> = []
    for (const [pageNum, edits] of pendingByPage.value.entries()) {
      if (edits.length > 0) groups.push({ pageNum, edits })
    }
    if (groups.length === 0) return

    // 标记 saving
    for (const { edits } of groups) {
      for (const e of edits) {
        editStates.value.set(e.id, 'saving')
      }
    }

    try {
      for (const { pageNum, edits } of groups) {
        const payload = edits.map((e) => ({
          type: e.type,
          pageNumber: e.pageNumber,
          x: e.x,
          y: e.y,
          text: e.text,
          fontSize: e.fontSize,
          color: e.color,
          ...(e.rect ? { rect: e.rect } : {}),
          ...(e.originalText ? { originalText: e.originalText } : {}),
          ...(e.originalX !== undefined ? { originalX: e.originalX } : {}),
          ...(e.originalY !== undefined ? { originalY: e.originalY } : {}),
          // Phase 13.22: 透传原 token 宽度/字体,后端精确覆盖 + 选原字体
          ...(e.width !== undefined ? { width: e.width } : {}),
          ...(e.font ? { font: e.font } : {}),
        }))

        await pdfApi.applyTextEdits(targetId, payload)

        // 标记 saved
        for (const e of edits) {
          editStates.value.set(e.id, 'saved')
        }
        // 1.5s 后清理
        setTimeout(() => {
          for (const e of edits) {
            editStates.value.delete(e.id)
          }
        }, 1500)
      }

      // 清空 pending
      pendingByPage.value.clear()

      ElMessage.success({
        message: `已保存 ${groups.reduce((s, g) => s + g.edits.length, 0)} 处文本编辑`,
        duration: 2000,
      })

      onSaved?.()
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e))
      // 回滚状态
      for (const { edits } of groups) {
        for (const edit of edits) {
          editStates.value.set(edit.id, 'error')
        }
      }
      ElMessage.error(`保存失败:${err.message}`)
      onError?.(err)
    }
  }

  // ========== API ==========

  /**
   * 加载指定页的字符坐标(带缓存)
   */
  async function loadPositions(pageNum: number, force = false): Promise<PdfTextPosition[]> {
    if (!force && positionsByPage.value.has(pageNum)) {
      return positionsByPage.value.get(pageNum)!
    }
    if (loadingPositions.value.has(pageNum)) {
      // 等待正在进行的请求
      return new Promise((resolve) => {
        const check = () => {
          if (positionsByPage.value.has(pageNum)) {
            resolve(positionsByPage.value.get(pageNum)!)
          } else if (!loadingPositions.value.has(pageNum)) {
            resolve([])
          } else {
            setTimeout(check, 50)
          }
        }
        check()
      })
    }
    loadingPositions.value.add(pageNum)
    try {
      const resp = await pdfApi.getTextPositions(docId)
      const all = resp.positions || []
      // 分组
      const grouped = new Map<number, PdfTextPosition[]>()
      for (const p of all) {
        if (!grouped.has(p.pageNum)) grouped.set(p.pageNum, [])
        grouped.get(p.pageNum)!.push(p)
      }
      // 写入全部页(避免每页一次请求)
      for (const [pn, arr] of grouped.entries()) {
        positionsByPage.value.set(pn, arr)
      }
      totalPagesFromPositions.value = resp.totalPages || 0
      return grouped.get(pageNum) || []
    } catch (e) {
      console.error(`[usePdfTextEditor] loadPositions(${pageNum}) failed:`, e)
      ElMessage.warning(`第 ${pageNum} 页文字位置加载失败,文本编辑可能不准确`)
      return []
    } finally {
      loadingPositions.value.delete(pageNum)
    }
  }

  /**
   * 加载全部页(批量)
   */
  async function loadAllPositions(): Promise<Map<number, PdfTextPosition[]>> {
    return new Promise(async (resolve) => {
      try {
        const resp = await pdfApi.getTextPositions(docId)
        const all = resp.positions || []
        const grouped = new Map<number, PdfTextPosition[]>()
        for (const p of all) {
          if (!grouped.has(p.pageNum)) grouped.set(p.pageNum, [])
          grouped.get(p.pageNum)!.push(p)
        }
        positionsByPage.value = grouped
        totalPagesFromPositions.value = resp.totalPages || 0
        resolve(grouped)
      } catch (e) {
        console.error('[usePdfTextEditor] loadAllPositions failed:', e)
        resolve(new Map())
      }
    })
  }

  /**
   * 加载已有编辑(打开文档时恢复状态)
   */
  async function loadSavedEdits(): Promise<Array<Record<string, unknown>>> {
    try {
      const resp = await pdfApi.loadTextEdits(docId)
      return resp.edits || []
    } catch (e) {
      console.error('[usePdfTextEditor] loadSavedEdits failed:', e)
      return []
    }
  }

  /**
   * 应用一次编辑(modify 现有文字)
   * - 自动从 positions 查找 originalText
   * - push 到 pendingByPage
   * - 启动防抖提交
   */
  function applyEdit(input: {
    position: PdfTextPosition
    newText: string
    color?: string
  }): PendingEdit {
    const id = `edit-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    const edit: PendingEdit = {
      id,
      type: 'modify',
      pageNumber: input.position.pageNum,
      text: input.newText,
      x: input.position.x,
      y: input.position.y,
      fontSize: input.position.fontSize || 12,
      color: input.color || input.position.color || '#000000',
      originalText: input.position.text,
      originalX: input.position.x,
      originalY: input.position.y,
      // Phase 13.22: 透传原 token 宽度 + 字体,后端精确覆盖 + 选原字体
      width: input.position.width,
      font: input.position.font,
      state: 'editing',
    }

    if (!pendingByPage.value.has(edit.pageNumber)) {
      pendingByPage.value.set(edit.pageNumber, [])
    }
    pendingByPage.value.get(edit.pageNumber)!.push(edit)
    editStates.value.set(id, 'editing')

    // Phase 13.11: autoCommit=false 时不自动提交(由用户点保存触发)
    if (autoCommit) {
      debouncedCommit()
    }
    return edit
  }

  /**
   * 直接应用一组编辑(批量)
   */
  function applyEdits(edits: PdfTextEdit[]): PendingEdit[] {
    const out: PendingEdit[] = []
    for (const e of edits) {
      const id = `edit-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
      const pending: PendingEdit = {
        ...e,
        id,
        originalText: e.originalText || e.text,
        originalX: e.originalX ?? e.x,
        originalY: e.originalY ?? e.y,
        state: 'editing',
      }
      if (!pendingByPage.value.has(e.pageNumber)) {
        pendingByPage.value.set(e.pageNumber, [])
      }
      pendingByPage.value.get(e.pageNumber)!.push(pending)
      editStates.value.set(id, 'editing')
      out.push(pending)
    }
    if (autoCommit) {
      debouncedCommit()
    }
    return out
  }

  /**
   * 立即刷新提交(不等防抖)
   */
  async function flush(): Promise<void> {
    debouncedCommit.cancel()
    await commitNow()
  }

  /**
   * Phase 13.11: 提交到指定文档(另存为新文档时用)
   * 提交后清空 pending,但不清除 positions(保持画布编辑状态)
   */
  async function flushTo(targetDocId: number): Promise<void> {
    debouncedCommit.cancel()
    await commitNow(targetDocId)
  }

  /**
   * 获取某页的待编辑数
   */
  function getPendingCount(pageNum?: number): number {
    if (pageNum !== undefined) {
      return pendingByPage.value.get(pageNum)?.length || 0
    }
    let total = 0
    for (const arr of pendingByPage.value.values()) total += arr.length
    return total
  }

  /**
   * 获取某 token 的编辑状态(用于 UI 反馈)
   */
  function getState(editId: string): EditState {
    return editStates.value.get(editId) || 'idle'
  }

  /**
   * 清空所有 pending(回滚 UI,不提交)
   */
  function rollback(): void {
    pendingByPage.value.clear()
    editStates.value.clear()
    debouncedCommit.cancel()
  }

  /**
   * 清空缓存(切换文档或重新加载后调用)
   */
  function clearCache(): void {
    positionsByPage.value.clear()
    pendingByPage.value.clear()
    editStates.value.clear()
    debouncedCommit.cancel()
  }

  onUnmounted(() => {
    debouncedCommit.cancel()
  })

  return {
    // state
    positionsByPage,
    totalPagesFromPositions,
    pendingByPage,
    editStates,
    loadingPositions,
    dirty,
    // actions
    loadPositions,
    loadAllPositions,
    loadSavedEdits,
    applyEdit,
    applyEdits,
    flush,
    flushTo,
    rollback,
    clearCache,
    getPendingCount,
    getState,
  }
}

export type UsePdfTextEditorReturn = ReturnType<typeof usePdfTextEditor>
