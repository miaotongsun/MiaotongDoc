import api from './index'

export interface AiChatRequest {
  question: string
}

export interface AiTranslateRequest {
  text: string
  targetLang: string
}

export interface AiRewriteRequest {
  text: string
  instruction: string
}

export interface AiResponse {
  content: string
  model?: string
}

export const documentAiApi = {
  /** 文档问答 */
  chat(docId: number, data: AiChatRequest) {
    return api.post<any, AiResponse>(`/documents/${docId}/ai/chat`, data)
  },

  /** 文档摘要 */
  summarize(docId: number) {
    return api.post<any, AiResponse>(`/documents/${docId}/ai/summarize`)
  },

  /** AI 翻译 */
  translate(docId: number, data: AiTranslateRequest) {
    return api.post<any, AiResponse>(`/documents/${docId}/ai/translate`, data)
  },

  /** AI 改写 */
  rewrite(docId: number, data: AiRewriteRequest) {
    return api.post<any, AiResponse>(`/documents/${docId}/ai/rewrite`, data)
  },
}
