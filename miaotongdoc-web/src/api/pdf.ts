import api from './index'

// ==================== 请求/响应类型 ====================

export interface PdfConvertRequest {
  targetFormat: 'docx' | 'md' | 'png' | 'txt'
}

export interface PdfMergeRequest {
  documentIds: number[]
}

export interface PdfPageOperationRequest {
  pages?: number[]
  degrees?: number
  newOrder?: number[]
}

export interface PdfCompressRequest {
  level?: 'high' | 'medium' | 'low'
}

export interface PdfSecurityRequest {
  password: string
}

export interface PdfRecognizeResponse {
  documentId: number
  title: string
  content: string
  markdown: string
  tables: any[]
  engine: string
  status: string
  totalPages?: number
  error?: string
}

export interface PdfExportEditedRequest {
  content: string
  format: 'md' | 'txt'
}

export interface PdfTextResponse {
  totalPages: number
  fullText: string
  pages: Array<{ pageNum: number; text: string }>
}

export interface PdfInfoResponse {
  totalPages: number
  title: string
  fileSize: number
}

// ==================== API 方法 ====================

export const pdfApi = {
  // ---------- 文本提取 ----------

  /** 提取全文文本 */
  getText(docId: number) {
    return api.get<any, PdfTextResponse>(`/pdf/${docId}/text`)
  },

  /** 提取指定页文本 */
  getPageText(docId: number, pageNum: number) {
    return api.get<any, { text: string }>(`/pdf/${docId}/pages/${pageNum}/text`)
  },

  /** 获取 PDF 信息 */
  getInfo(docId: number) {
    return api.get<any, PdfInfoResponse>(`/pdf/${docId}/info`)
  },

  // ---------- 格式转换 ----------

  /** PDF 格式转换 */
  convert(docId: number, data: PdfConvertRequest) {
    return api.post<any, Blob>(`/pdf/${docId}/convert`, data, { responseType: 'blob' as any })
  },

  // ---------- 页面操作 (Phase 3:返回 JSON 而非 Blob) ----------

  /** 合并多个 PDF(Phase 3:原子化覆盖当前文档) */
  merge(data: PdfMergeRequest) {
    return api.post<any, PageOpResult>('/pdf/merge', data)
  },

  /** 拆分 PDF */
  split(docId: number) {
    return api.post<any, { totalPages: number; message: string }>(`/pdf/${docId}/split`)
  },

  /** 旋转页面(Phase 3:原子化) */
  rotatePages(docId: number, data: PdfPageOperationRequest) {
    return api.post<any, PageOpResult>(`/pdf/${docId}/pages/rotate`, data)
  },

  /** 删除页面(Phase 3:原子化) */
  deletePage(docId: number, pageNum: number) {
    return api.delete<any, PageOpResult>(`/pdf/${docId}/pages/${pageNum}`)
  },

  /** Phase 13.12-D: 批量删除页面(service 层降序删除避免索引偏移) */
  deletePagesBatch(docId: number, pages: number[]) {
    return api.post<any, PageOpResult>(`/pdf/${docId}/pages/delete-batch`, { pages })
  },

  /** 提取页面(Phase 3:原子化,提取为新文件替换当前文档) */
  extractPages(docId: number, data: PdfPageOperationRequest) {
    return api.post<any, PageOpResult>(`/pdf/${docId}/pages/extract`, data)
  },

  /**
   * Phase 13.29: 提取页面到新文档(非破坏性,生成新 Document)
   * POST /api/pdf/{id}/pages/extract-to-new
   * 返回: { success, docId, message }
   */
  extractPagesToNew(docId: number, pages: number[], title?: string) {
    return api.post<any, { success: boolean; docId: number; message?: string }>(
      `/pdf/${docId}/pages/extract-to-new`,
      { pages, title },
    )
  },

  /**
   * Phase 13.29: 高级合并(页区间 + 目标 new/overwrite)
   * POST /api/pdf/merge-advanced
   * 返回: { success, docId?, filePath, targetDocId? }
   */
  mergeAdvanced(payload: {
    documents: Array<{ docId: number; pageRanges?: string }>
    target: { mode: 'new' | 'overwrite'; docId?: number; title?: string }
  }) {
    return api.post<any, { success: boolean; docId?: number; filePath: string; targetDocId?: number; message?: string }>(
      `/pdf/merge-advanced`,
      payload,
    )
  },

  /** 重排页面(Phase 3:原子化) */
  reorderPages(docId: number, data: PdfPageOperationRequest) {
    return api.post<any, PageOpResult>(`/pdf/${docId}/pages/reorder`, data)
  },

  // ---------- 优化 ----------

  /** 压缩 PDF */
  compress(docId: number, data?: PdfCompressRequest) {
    return api.post<any, Blob>(`/pdf/${docId}/compress`, data || {}, { responseType: 'blob' as any })
  },

  // ---------- 安全 ----------

  /** 加密 PDF */
  encrypt(docId: number, data: PdfSecurityRequest) {
    return api.post<any, Blob>(`/pdf/${docId}/encrypt`, data, { responseType: 'blob' as any })
  },

  /** 解密 PDF */
  decrypt(docId: number, data: PdfSecurityRequest) {
    return api.post<any, Blob>(`/pdf/${docId}/decrypt`, data, { responseType: 'blob' as any })
  },

  // ---------- AI ----------
  // Phase 4 清理:无后端实现的 5 个方法已删除,改用通用 documentAiApi
  //   - 摘要  → documentAiApi.summarize(docId)
  //   - 翻译  → documentAiApi.translate(docId, { text, targetLang })
  //   - 视觉问答 → usePdfAiVision(PdfAiFloatPanel 已内置)
  //   - 抽取条款 → usePdfExtractTerms
  //   - 优化 OCR  → usePdfOptimizeOcr

  // ---------- 识别 ----------

  /** 智能识别 PDF（新版，会自动保存结果） */
  recognize(docId: number) {
    return api.post<any, PdfRecognizeResponse>(`/pdf/${docId}/recognize`)
  },
  /** Phase 11.4: 强制 PaddleOCR 识别(返回 bbox 坐标)
   *  Phase 13.6: model 参数选择 mobile(轻量,默认)/ server(高精度)
   */
  recognizePaddle(docId: number, model: 'mobile' | 'server' = 'mobile') {
    return api.post<any, { status: string; engine: string; model?: string; degraded?: boolean; pages?: any[]; totalPages?: number }>(
      `/pdf/${docId}/recognize-paddle?model=${model}`,
    )
  },

  /** 获取 Markdown 内容 */
  getMarkdown(docId: number) {
    return api.get<any, {
      recognized: boolean
      markdown: Record<string, string>
      ocrData: Record<string, { dpi?: number; regions?: Array<{ text: string; bbox: number[]; confidence?: number }> }>
      recognizedAt: string
    }>(`/pdf/${docId}/markdown`)
  },

  /** 保存 Markdown 内容 */
  saveMarkdown(docId: number, markdown: Record<string, string>) {
    return api.put<any, { success: boolean; message: string }>(`/pdf/${docId}/markdown`, markdown)
  },

  /** 查询识别状态 */
  getRecognizeStatus(docId: number) {
    return api.get<any, { status: string; recognized: boolean; recognizedAt: string | null }>(`/pdf/${docId}/recognize-status`)
  },

  /** 导出编辑后内容 */
  exportEdited(docId: number, data: PdfExportEditedRequest) {
    return api.post<any, Blob>(`/pdf/${docId}/export-edited`, data, { responseType: 'blob' as any })
  },

  // ---------- Phase 11: 页面操作(插入/裁剪/水印/页眉页脚) ----------

  /** 插入空白页 */
  insertBlankPage(docId: number, afterPage: number) {
    return api.post<any, { success: boolean; message: string; bustUrl: number }>(
      `/pdf/${docId}/pages/insert-blank`, { afterPage },
    )
  },
  /** 裁剪指定页 */
  cropPages(docId: number, pages: number[], cropBox: { x: number; y: number; width: number; height: number }) {
    return api.post<any, { success: boolean; message: string; bustUrl: number }>(
      `/pdf/${docId}/pages/crop`, { pages, cropBox },
    )
  },
  /** 添加水印 */
  addWatermark(docId: number, data: { text: string; opacity: number; rotation: number; position?: 'diagonal' | 'header' | 'footer' | 'center' | 'tile'; fontSize?: number; clearExisting?: boolean; pages?: number[] }) {
    return api.post<any, { success: boolean; message: string; bustUrl: number }>(
      `/pdf/${docId}/watermark`, data,
    )
  },
  /** 添加页眉/页脚 */
  addHeaderFooter(docId: number, data: { position: 'header' | 'footer'; content: string; fontSize: number; clearExisting?: boolean; pages?: number[] }) {
    return api.post<any, { success: boolean; message: string; bustUrl: number }>(
      `/pdf/${docId}/header-footer`, data,
    )
  },

  // ---------- Phase 2: 原生 PDF 文本编辑 ----------

  /**
   * 获取 PDF 文字位置信息(用于原生编辑)
   * 后端: GET /api/pdf/{id}/text-positions
   * 返回: { positions: [...], totalPages }
   * 字段: { text, x, y, fontSize, font, width, height, pageNum, pageWidth, pageHeight }
   */
  getTextPositions(docId: number) {
    return api.get<any, { positions: PdfTextPosition[]; totalPages: number }>(
      `/pdf/${docId}/text-positions`,
    )
  },

  /**
   * 应用文字编辑(后端 PDFBox 原子化落盘,直接覆盖 storage 文件)
   * 后端: POST /api/pdf/{id}/text-edits
   * payload: { edits: [{ type:'modify'|'add'|'delete', pageNumber, x, y, text, fontSize, color, rect?, originalX?, originalY?, originalText? }] }
   * 返回: { success, message, editsId }
   */
  applyTextEdits(
    docId: number,
    edits: Array<Record<string, unknown>>,
  ) {
    return api.post<any, { success: boolean; message: string; editsId?: string }>(
      `/pdf/${docId}/text-edits`,
      { edits },
    )
  },

  /**
   * Phase 13.25: 应用文本格式修改(字号/颜色/粗/斜/下划线/高亮)持久化
   * 后端: POST /api/pdf/{id}/text-format
   * payload: { pageNumber, ops: [{ range:{x,y,width,height}, format:{fontSize?,color?,bold?,italic?,underline?,highlight?} }] }
   * 返回: { success, filePath }
   */
  applyTextFormat(
    docId: number,
    ops: Array<{
      pageNumber: number
      range: { x: number; y: number; width: number; height: number }
      format: Partial<{ fontSize: number; color: string; bold: boolean; italic: boolean; underline: boolean; highlight: string }>
    }>,
  ) {
    const pageNumber = ops[0]?.pageNumber ?? 1
    return api.post<any, { success: boolean; filePath?: string; message?: string }>(
      `/pdf/${docId}/text-format`,
      { pageNumber, ops },
    )
  },

  /**
   * 加载已有文字编辑(前端重新打开后可恢复编辑状态)
   * 后端: GET /api/pdf/{id}/text-edits
   * 返回: { edits: [...], count }
   */
  loadTextEdits(docId: number) {
    return api.get<any, { edits: Array<Record<string, unknown>>; count: number }>(
      `/pdf/${docId}/text-edits`,
    )
  },

  // ---------- Phase 8: 导航增强 ----------

  /**
   * 获取 PDF 书签/大纲(平铺树形,带 level 字段)
   * GET /api/pdf/{id}/outline
   */
  getOutline(docId: number) {
    return api.get<any, { outline: Array<Record<string, unknown>>; count: number }>(
      `/pdf/${docId}/outline`,
    )
  },

  /**
   * 全文搜索(POST 版本支持中文)
   * POST /api/pdf/{id}/search body: { q, caseSensitive }
   */
  searchPost(docId: number, body: { q: string; caseSensitive?: boolean }) {
    return api.post<any, { results: Array<Record<string, unknown>>; count: number; query: string }>(
      `/pdf/${docId}/search`,
      body,
    )
  },

  /**
   * PDF 元数据
   * GET /api/pdf/{id}/metadata
   */
  getMetadata(docId: number) {
    return api.get<any, Record<string, unknown>>(`/pdf/${docId}/metadata`)
  },

  /**
   * Phase 12.1: 表单字段检测
   * GET /api/pdf/{id}/form-fields
   */
  getFormFields(docId: number) {
    return api.get<any, PdfFormField[]>(`/pdf/${docId}/form-fields`)
  },

  /**
   * Phase 13.11: 另存为新文档(复制当前 PDF 为新文档)
   * POST /api/pdf/{id}/save-as-new
   */
  saveAsNew(docId: number, title?: string) {
    return api.post<any, { success: boolean; newDocId: number; title: string; message: string }>(
      `/pdf/${docId}/save-as-new`,
      { title: title || '' },
    )
  },

  /**
   * Phase 13.11: 创建新版本(编辑保存时记录版本)
   * POST /api/documents/{id}/versions
   */
  createVersion(docId: number, summary: string) {
    return api.post<any, { message: string; versionNumber: number }>(
      `/documents/${docId}/versions`,
      { summary },
    )
  },

  /**
   * Phase 12.2: 填充表单字段
   * POST /api/pdf/{id}/form-fields/fill
   * 返回填充后的 PDF Blob
   */
  fillFormFields(docId: number, values: Record<string, string>) {
    return api.post<any, Blob>(
      `/pdf/${docId}/form-fields/fill`,
      { values },
      { responseType: 'blob' as any },
    )
  },

  /**
   * Phase 12.3: 嵌入签名图片
   * POST /api/pdf/{id}/signature
   * 返回签名后的 PDF Blob
   */
  embedSignature(docId: number, data: PdfSignatureRequest) {
    return api.post<any, Blob>(
      `/pdf/${docId}/signature`,
      data,
      { responseType: 'blob' as any },
    )
  },

  /**
   * Phase 13.26: 签名 in-place 嵌入(落盘 + 返回 filePath,不下载)
   * POST /api/pdf/{id}/signature/in-place
   * 返回: { success, filePath, message }
   */
  embedSignatureInPlace(docId: number, data: PdfSignatureRequest) {
    return api.post<any, { success: boolean; filePath: string; message?: string }>(
      `/pdf/${docId}/signature/in-place`,
      data,
    )
  },

  /**
   * Phase 13.26: 表单填充 in-place(落盘 + 返回 filePath,不下载)
   * POST /api/pdf/{id}/form-fields/fill-in-place
   * 返回: { success, filePath, message }
   */
  fillFormFieldsInPlace(docId: number, values: Record<string, string>) {
    return api.post<any, { success: boolean; filePath: string; message?: string }>(
      `/pdf/${docId}/form-fields/fill-in-place`,
      { values },
    )
  },

  /**
   * Phase 12.4: 应用密文(绘制黑色矩形覆盖)
   * POST /api/pdf/{id}/redact
   */
  applyRedaction(docId: number, regions: Array<{ page: number; x: number; y: number; width: number; height: number }>) {
    return api.post<any, Blob>(
      `/pdf/${docId}/redact`,
      { regions },
      { responseType: 'blob' as any },
    )
  },

  /**
   * Phase 13.12-B: 创建空白 PDF
   * POST /api/pdf/create/blank { pages, width, height, title }
   */
  createBlank(pages: number, width: number, height: number, title?: string) {
    return api.post<any, { docId: number; title: string; pages: number }>(
      `/pdf/create/blank`,
      { pages, width, height, title: title || '新建空白文档' },
    )
  },

  /**
   * Phase 13.12-B: 图片转 PDF(multipart)
   * POST /api/pdf/create/from-images
   */
  createFromImages(files: File[], title?: string) {
    const formData = new FormData()
    files.forEach(f => formData.append('files', f))
    if (title) formData.append('title', title)
    return api.post<any, { docId: number; title: string; pages: number }>(
      `/pdf/create/from-images`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } as any },
    )
  },

  /**
   * Phase 13.12-B: 区间拆分 zip 下载
   * POST /api/pdf/{id}/split-by-ranges { ranges: "1-3,5,7-9" }
   */
  splitByRanges(docId: number, ranges: string) {
    return api.post<any, Blob>(
      `/pdf/${docId}/split-by-ranges`,
      { ranges },
      { responseType: 'blob' as any },
    )
  },

  /**
   * Phase 13.37: 替换页面 - 用上传 PDF 的指定页替换目标文档选中页
   * POST /api/pdf/{id}/pages/replace (multipart)
   */
  replacePages(docId: number, targetPages: number[], file: File, sourceStartPage: number) {
    const form = new FormData()
    form.append('targetPages', targetPages.join(','))
    form.append('sourceStartPage', String(sourceStartPage))
    form.append('file', file)
    return api.post<any, { success: boolean; message: string; filePath: string }>(
      `/pdf/${docId}/pages/replace`,
      form,
      { headers: { 'Content-Type': 'multipart/form-data' } as any },
    )
  },

  /**
   * Phase 13.12-B: 批量提取页面为单个 PDF
   * POST /api/pdf/{id}/extract-pages-batch { pages: [1,3,5] }
   */
  extractPagesBatch(docId: number, pages: number[]) {
    return api.post<any, Blob>(
      `/pdf/${docId}/extract-pages-batch`,
      { pages },
      { responseType: 'blob' as any },
    )
  },

  /** Phase 13.23: 去水印 */
  removeWatermark(docId: number, mode: 'annotation' | 'all' = 'all') {
    return api.post<any, PageOpResult>(`/pdf/${docId}/watermark/remove`, { mode })
  },

  /** Phase 13.23: 智能目录(AI 生成 + 写入 PDF outline) */
  autoOutline(docId: number) {
    return api.post<any, { success: boolean; outline: Array<{ title: string; page: number; level: number }>; filePath?: string; error?: string }>(
      `/pdf/${docId}/ai/auto-outline`, {},
    )
  },

  /** Phase 14.U6: 文档对比 */
  compare(docIdA: number, docIdB: number) {
    return api.post<any, {
      success: boolean
      docA: { id: number; title: string }
      docB: { id: number; title: string }
      summary: { totalPages: number; same: number; modified: number; added: number; removed: number }
      pages: Array<{ page: number; status: 'same' | 'modified' | 'added' | 'removed'; diffHunks?: Array<{ type: 'eq' | 'add' | 'del'; text: string }> }>
    }>(`/pdf/compare`, { docIdA, docIdB })
  },

  /** Phase 13.23: 智能提取(文字+表格+图片+结构化 JSON) */
  extractStructured(docId: number) {
    return api.post<any, { success: boolean; text: string; tables: string; imagesCount: number; structuredJson: string; error?: string }>(
      `/pdf/${docId}/ai/extract-structured`, {},
    )
  },

  /** Phase 13.23: 提取嵌入图片 zip */
  extractImages(docId: number) {
    return api.get<any, Blob>(`/pdf/${docId}/extract-images`, { responseType: 'blob' as any })
  },
}

// ==================== Phase 2 类型扩展 ====================

export interface PdfTextPosition {
  /** 原始字符文本 */
  text: string
  /** PDF 坐标系 X(基线起点) */
  x: number
  /** PDF 坐标系 Y(基线起点) */
  y: number
  /** 字符宽度 */
  width: number
  /** 字符高度 */
  height: number
  /** 字号 */
  fontSize: number
  /** 字体名(可能为空,后端会用 HELVETICA 兜底) */
  font: string
  /** OCR 置信度 0-1(Phase 11.4 PaddleOCR 字段,后端 fallback 路径可能缺失) */
  confidence?: number
  /** Phase 13.22: 原文字真实颜色 #RRGGBB(后端从 graphics state rg/RG 指令解析) */
  color?: string
  /** 所属页码 */
  pageNum: number
  /** 页面原始宽度 */
  pageWidth: number
  /** 页面原始高度 */
  pageHeight: number
}

export interface PdfTextEdit {
  type: 'add' | 'modify' | 'delete'
  pageNumber: number
  /** 新文字 */
  text: string
  /** 新位置 X */
  x: number
  /** 新位置 Y */
  y: number
  /** 字号 */
  fontSize: number
  /** 颜色 hex,如 #000000 */
  color: string
  /** 删除/覆盖用的矩形区域 */
  rect?: { x: number; y: number; width: number; height: number }
  /** modify 类型:原文字 */
  originalText?: string
  /** modify 类型:原位置 X */
  originalX?: number
  /** modify 类型:原位置 Y */
  originalY?: number
  /** Phase 13.22: modify 精确覆盖原 token 宽度(后端用此值替代估算) */
  width?: number
  /** Phase 13.22: 原 token 字体名(后端用此找原字体;缺则 fallback 中文/Helvetica) */
  font?: string
}

/**
 * Phase 3 页面操作统一返回结构(后端原子化覆盖当前文档)
 */
export interface PageOpResult {
  success: boolean
  message: string
  /** 新 storage 路径(供前端 cache-bust 重新加载) */
  filePath: string
  /** 目标文档 ID(merge 场景返回) */
  targetDocId?: number
  /** merge 时包含 */
  rotatedPages?: number[]
  /** delete_page 时包含 */
  deletedPage?: number
  /** extract 时包含 */
  extractedPages?: number[]
  /** reorder 时包含 */
  newOrder?: number[]
}

/**
 * Phase 12.1: PDF AcroForm 表单字段
 */
export interface PdfFormField {
  /** 完整限定名(层级用 . 分隔) */
  name: string
  /** 简称(末段) */
  partialName: string
  /** 类型: text/checkbox/radio/combobox/listbox/signature/unknown */
  type: 'text' | 'checkbox' | 'radio' | 'combobox' | 'listbox' | 'signature' | 'unknown'
  /** 当前值 */
  value: string
  /** 是否只读 */
  readOnly: boolean
  /** 是否必填 */
  required: boolean
  /** 所在页码(1-based,0 表示未定位) */
  page: number
  /** 矩形坐标 [llx, lly, urx, ury] PDF 坐标系(原点左下) */
  rect: [number, number, number, number]
  /** 选项列表(combobox/listbox/radio 用) */
  options?: string[]
}

/**
 * Phase 12.3: 签名嵌入请求
 */
export interface PdfSignatureRequest {
  /** 签名图片 base64(不含 data:image/png;base64, 前缀) */
  image: string
  /** 页码(1-based) */
  page: number
  /** PDF 坐标 X(左下原点,pt) */
  x: number
  /** PDF 坐标 Y(左下原点,pt) */
  y: number
  /** 显示宽度(pt) */
  width: number
  /** 显示高度(pt) */
  height: number
}
