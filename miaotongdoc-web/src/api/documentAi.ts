import api from './index'

// ===== 请求类型 =====

export interface AiChatRequest {
  question: string
  history?: Array<{ role: string; content: string }>
  enhanced?: boolean  // 增强模式：智能分块 + 关键词检索
}

export interface AiTranslateRequest {
  text?: string
  targetLang: string
}

export interface AiRewriteRequest {
  text: string
  instruction: string
}

export interface AiCompareRequest {
  compareDocId: string
}

export interface AiVisionChatRequest {
  question: string
  page: string
}

export interface AiSuggestFolderRequest {
  folders: string
}

// ===== 响应类型 =====

export interface AiResponse {
  content: string
  model?: string
}

// ===== API 接口 =====

export const documentAiApi = {

  // ===== 内容生成 =====

  /** AI 内容生成（同步） */
  generate(docId: number, prompt: string) {
    return api.post<any, AiResponse>(`/documents/${docId}/ai/generate`, { prompt })
  },

  /** AI 内容生成（流式） */
  generateStream(docId: number, prompt: string) {
    return api.post<any, AiResponse>(`/documents/${docId}/ai/generate-stream`, { prompt })
  },

  // ===== 文档问答 =====

  /** 文档问答（同步，支持多轮对话） */
  chat(docId: number, data: AiChatRequest) {
    return api.post<any, AiResponse>(`/documents/${docId}/ai/chat`, data)
  },

  /** 文档问答（流式） */
  chatStream(docId: number, data: AiChatRequest) {
    return api.post<any, AiResponse>(`/documents/${docId}/ai/chat-stream`, data)
  },

  // ===== 文档摘要 =====

  /** 文档摘要 */
  summarize(docId: number) {
    return api.post<any, AiResponse>(`/documents/${docId}/ai/summarize`)
  },

  /** 结构化摘要 */
  summarizeStructured(docId: number) {
    return api.post<any, AiResponse>(`/documents/${docId}/ai/summarize-structured`)
  },

  // ===== 翻译 =====

  /** AI 翻译 */
  translate(docId: number, data: AiTranslateRequest) {
    return api.post<any, AiResponse>(`/documents/${docId}/ai/translate`, data)
  },

  // ===== 改写 =====

  /** AI 改写 */
  rewrite(docId: number, data: AiRewriteRequest) {
    return api.post<any, AiResponse>(`/documents/${docId}/ai/rewrite`, data)
  },

  // ===== 表格提取 =====

  /** 表格提取 */
  extractTables(docId: number) {
    return api.post<any, AiResponse>(`/documents/${docId}/ai/extract-tables`)
  },

  // ===== 文档对比 =====

  /** 文档对比 */
  compare(docId: number, data: AiCompareRequest) {
    return api.post<any, AiResponse>(`/documents/${docId}/ai/compare`, data)
  },

  // ===== 视觉问答 =====

  /** 视觉问答 */
  visionChat(docId: number, data: AiVisionChatRequest) {
    return api.post<any, AiResponse>(`/documents/${docId}/ai/vision-chat`, data)
  },

  // ===== 智能标签 =====

  /** 智能标签建议 */
  suggestTags(docId: number) {
    return api.post<any, AiResponse>(`/documents/${docId}/ai/suggest-tags`)
  },

  // ===== 智能分类 =====

  /** 智能文件夹建议 */
  suggestFolder(docId: number, data: AiSuggestFolderRequest) {
    return api.post<any, AiResponse>(`/documents/${docId}/ai/suggest-folder`, data)
  },

  // ===== 合同审查 =====

  /** 合同智能审查 */
  reviewContract(docId: number) {
    return api.post<any, AiResponse>(`/documents/${docId}/ai/review-contract`)
  },

  // ===== 流式调用 =====

  /** 流式 AI 调用（支持 chat / summarize / translate / rewrite） */
  stream(docId: number, body: { action: string; question?: string; text?: string; instruction?: string; targetLang?: string }) {
    const token = sessionStorage.getItem('token')
    return fetch(`/api/documents/${docId}/ai/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify(body),
    })
  },
}
