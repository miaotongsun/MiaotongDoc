import api from './index'

export interface AuditLogItem {
  id: number
  documentId?: number
  userId: number
  employeeId: string
  userName: string
  action: string
  resourceType: string
  resourceId?: number
  detail?: string
  ipAddress?: string
  createdAt: string
}

export interface AuditLogPage {
  content: AuditLogItem[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export const auditApi = {
  getDocumentLogs(docId: number, params?: { page?: number; size?: number }) {
    return api.get<any, AuditLogPage>(`/audit/document/${docId}`, { params })
  },

  getMyLogs(params?: { page?: number; size?: number; startDate?: string; endDate?: string }) {
    return api.get<any, AuditLogPage>('/audit/me', { params })
  },

  getAllLogs(params?: { page?: number; size?: number; startDate?: string; endDate?: string; userId?: number; action?: string }) {
    return api.get<any, AuditLogPage>('/audit/all', { params })
  }
}
