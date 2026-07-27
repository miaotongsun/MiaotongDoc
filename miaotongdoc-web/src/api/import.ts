import api from './index'

export interface ImportResult {
  totalRows: number
  successCount: number
  failCount: number
  errors: { rowNumber: number; message: string }[]
}

export interface OpenApiKeyInfo {
  id: number
  accessKey: string          // 仅创建时返回
  secretPrefix: string       // 列表展示用
  name: string
  ownerSystem?: string
  contact?: string
  enabled: boolean
  expiresAt?: string
  rateLimitPerMinute: number
  allowedIps?: string
  lastUsedAt?: string
  createdAt: string
  revokedAt?: string
}

export interface OpenApiKeyCreateResponse {
  id: number
  accessKey: string
  secretPrefix: string
  name: string
  ownerSystem?: string
  expiresAt?: string
  rateLimit: number
  createdAt: string
  notice: string
}

export const importApi = {
  importUsers(file: File) {
    const fd = new FormData()
    fd.append('file', file)
    return api.post<any, ImportResult>('/admin/users/import', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  importDepartments(file: File) {
    const fd = new FormData()
    fd.append('file', file)
    return api.post<any, ImportResult>('/departments/import', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  downloadUserTemplate() {
    return api.get<any, Blob>('/admin/users/import/template', { responseType: 'blob' as any })
  },
  downloadDeptTemplate() {
    return api.get<any, Blob>('/departments/import/template', { responseType: 'blob' as any })
  }
}

export const openApiKeyAdminApi = {
  list() {
    return api.get<any, OpenApiKeyInfo[]>('/admin/openapi/keys')
  },
  create(data: {
    name: string
    ownerSystem?: string
    contact?: string
    expiresAt?: string
    allowedIps?: string
    rateLimit?: number
  }) {
    return api.post<any, OpenApiKeyCreateResponse>('/admin/openapi/keys', data)
  },
  reveal(id: number) {
    return api.get<any, { accessKey: string }>(`/admin/openapi/keys/${id}/reveal`)
  },
  enable(id: number) {
    return api.put<any, { message: string }>(`/admin/openapi/keys/${id}/enable`)
  },
  disable(id: number) {
    return api.put<any, { message: string }>(`/admin/openapi/keys/${id}/disable`)
  },
  revoke(id: number) {
    return api.delete<any, { message: string }>(`/admin/openapi/keys/${id}`)
  },
  delete(id: number) {
    return api.delete<any, { message: string }>(`/admin/openapi/keys/${id}/hard`)
  }
}