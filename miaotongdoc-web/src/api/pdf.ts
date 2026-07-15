import api from './index'

// ==================== 请求/响应类型 ====================

export interface PdfConvertRequest {
  targetFormat: 'docx' | 'md' | 'png' | 'txt'
}

export interface PdfMergeRequest {
  documentIds: number[]
}

export interface PdfPageOperationRequest {
  pages: number[]
  degrees?: number
  newOrder?: number[]
}

export interface PdfCompressRequest {
  level?: 'high' | 'medium' | 'low'
}

export interface PdfSecurityRequest {
  password: string
}

export interface PdfAiChatRequest {
  question: string
}

export interface PdfAiTranslateRequest {
  text?: string
  targetLang: string
}

export interface PdfAiCompareRequest {
  compareDocId: number
}

export interface PdfAiVisionChatRequest {
  question: string
  page: number
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

export interface PdfAiResponse {
  content: string
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

  // ---------- 页面操作 ----------

  /** 合并多个 PDF */
  merge(data: PdfMergeRequest) {
    return api.post<any, Blob>('/pdf/merge', data, { responseType: 'blob' as any })
  },

  /** 拆分 PDF */
  split(docId: number) {
    return api.post<any, { totalPages: number; message: string }>(`/pdf/${docId}/split`)
  },

  /** 旋转页面 */
  rotatePages(docId: number, data: PdfPageOperationRequest) {
    return api.post<any, Blob>(`/pdf/${docId}/pages/rotate`, data, { responseType: 'blob' as any })
  },

  /** 删除页面 */
  deletePage(docId: number, pageNum: number) {
    return api.delete<any, Blob>(`/pdf/${docId}/pages/${pageNum}`, { responseType: 'blob' as any })
  },

  /** 提取页面 */
  extractPages(docId: number, data: PdfPageOperationRequest) {
    return api.post<any, Blob>(`/pdf/${docId}/pages/extract`, data, { responseType: 'blob' as any })
  },

  /** 重排页面 */
  reorderPages(docId: number, data: PdfPageOperationRequest) {
    return api.post<any, Blob>(`/pdf/${docId}/pages/reorder`, data, { responseType: 'blob' as any })
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

  /** AI 问答 */
  aiChat(docId: number, data: PdfAiChatRequest) {
    return api.post<any, PdfAiResponse>(`/pdf/${docId}/ai/chat`, data)
  },

  /** AI 摘要 */
  aiSummarize(docId: number) {
    return api.post<any, PdfAiResponse>(`/pdf/${docId}/ai/summarize`)
  },

  /** AI 翻译 */
  aiTranslate(docId: number, data: PdfAiTranslateRequest) {
    return api.post<any, PdfAiResponse>(`/pdf/${docId}/ai/translate`, data)
  },

  /** AI 表格提取 */
  aiExtractTables(docId: number) {
    return api.post<any, PdfAiResponse>(`/pdf/${docId}/ai/extract-tables`)
  },

  /** AI 文档对比 */
  aiCompare(docId: number, data: PdfAiCompareRequest) {
    return api.post<any, PdfAiResponse>(`/pdf/${docId}/ai/compare`, data)
  },

  /** AI 视觉问答 */
  aiVisionChat(docId: number, data: PdfAiVisionChatRequest) {
    return api.post<any, PdfAiResponse>(`/pdf/${docId}/ai/vision-chat`, data)
  },

  // ---------- 识别 ----------

  /** 智能识别 PDF（新版，会自动保存结果） */
  recognize(docId: number) {
    return api.post<any, PdfRecognizeResponse>(`/pdf/${docId}/recognize`)
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
  }
}
