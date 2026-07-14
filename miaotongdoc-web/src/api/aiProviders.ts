/**
 * aiProviders.ts —— AI Provider 管理 API 客户端（v2.7）
 *
 * 后端：/api/admin/ai/providers/*
 * 用途：管理后台的 AI 配置页（多 Provider CRUD）
 *
 * 支持的 type：
 *   - LLM          （大语言模型，MD/PDF AI 用）
 *   - VISION       （视觉问答，PDF VLM 用）
 *   - OCR_PADDLE   （PaddleOCR 容器，PDF OCR 主力）
 *   - DOCLING      （Docling 文档解析，文本型 PDF）
 *   - OCR_TESSERACT（Tesseract OCR，扫描件兜底）
 */
import http from './index'

export type ProviderType = 'LLM' | 'VISION' | 'OCR_PADDLE' | 'DOCLING' | 'OCR_TESSERACT'

export interface AiProvider {
  id?: number
  type: ProviderType
  name: string
  baseUrl: string
  apiKey: string  // 后端脱敏返回，前端只读；编辑时才发送真实 key
  defaultModel?: string
  timeout?: number
  enabled: boolean
  isDefault: boolean
  remark?: string
  extra?: string
  createdAt?: string
  updatedAt?: string
  /** v2.7.2：仅前端表单状态，标记用户是否修改过 apiKey（false → 后端保留原值） */
  apiKeyChanged?: boolean
}

export const PROVIDER_TYPE_LABELS: Record<ProviderType, string> = {
  LLM: '大语言模型',
  VISION: '视觉问答',
  OCR_PADDLE: 'OCR 识别（PaddleOCR）',
  DOCLING: '文档解析（Docling）',
  OCR_TESSERACT: 'OCR 兜底（Tesseract）',
}

export const PROVIDER_TYPE_ICONS: Record<ProviderType, string> = {
  LLM: 'MagicStick',
  VISION: 'View',
  OCR_PADDLE: 'Reading',
  DOCLING: 'Files',
  OCR_TESSERACT: 'Document',
}

export const aiProvidersApi = {
  /** 列出所有 Provider（可按 type 过滤） */
  list: (type?: ProviderType) =>
    http.get<any, AiProvider[]>(`/admin/ai/providers${type ? `?type=${type}` : ''}`),

  /** 获取单个 */
  get: (id: number) => http.get<any, AiProvider>(`/admin/ai/providers/${id}`),

  /** 创建 */
  create: (data: AiProvider) => http.post<any, AiProvider>('/admin/ai/providers', data),

  /** 更新 */
  update: (id: number, data: AiProvider) =>
    http.put<any, AiProvider>(`/admin/ai/providers/${id}`, data),

  /** 删除 */
  remove: (id: number) => http.delete<any, { message: string }>(`/admin/ai/providers/${id}`),

  /** 设为默认（同 type 内的其他默认自动取消） */
  setDefault: (id: number) =>
    http.post<any, { status: string; message: string; provider: AiProvider }>(
      `/admin/ai/providers/${id}/set-default`),

  /** 启用/禁用 */
  setEnabled: (id: number, enabled: boolean) =>
    http.put<any, AiProvider>(`/admin/ai/providers/${id}`, { enabled }),

  /** 手动触发缓存刷新（运维用） */
  refresh: () => http.post<any, { message: string }>('/admin/ai/providers/refresh'),

  /**
   * 拉取 Provider 可用模型列表
   * body: { id? , baseUrl? , apiKey? }
   * 优先用 DB 里的 id；id 没有时用前端临时传的 baseUrl/apiKey
   */
  fetchModels: (data: { id?: number; baseUrl?: string; apiKey?: string }) =>
    http.post<any, { status: string; url: string; models: string[] }>(
      '/admin/ai/providers/fetch-models', data),

  /**
   * 统一测试连接
   * body: { id? , baseUrl? , apiKey? , model? }
   * 返回：{ status, url, httpCode, latencyMs, model, modelCount, message, error? }
   */
  testConnection: (data: { id?: number; baseUrl?: string; apiKey?: string; model?: string }) =>
    http.post<any, {
      status: 'ok' | 'fail';
      url: string;
      httpCode: number;
      latencyMs: number;
      model?: string;
      modelCount?: number;
      message: string;
      error?: string;
      detail?: string;
    }>('/admin/ai/providers/test-connection', data),
}