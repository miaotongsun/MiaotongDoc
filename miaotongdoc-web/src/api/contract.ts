import api from './index'

export interface Contract {
  id: number
  documentId: number
  documentTitle?: string
  contractNo?: string
  contractType?: string
  partyA?: string
  partyB?: string
  amount?: number
  currency?: string
  signingDate?: string
  effectiveDate?: string
  expiryDate?: string
  status: string
  ownerUserId: number
  ownerName?: string
  departmentId?: number
  departmentName?: string
  currentStep?: number
  approvedVersion?: number
  signingLocked?: boolean
  remarks?: string
  createdAt: string
  updatedAt: string
  approvalNodes?: ApprovalNode[]
  approvals?: ContractApproval[]
}

export interface ApprovalNode {
  id: number
  stepOrder: number
  approverId: number
  approverName?: string
  status: string
  remark?: string
  actedAt?: string
}

export interface ContractApproval {
  id: number
  action: string
  operatorName?: string
  remark?: string
  createdAt: string
}

export interface ParsedContract {
  contractNo?: string
  contractType?: string
  partyA?: string
  partyB?: string
  amount?: number
  signingDate?: string
  effectiveDate?: string
  expiryDate?: string
}

export interface IntegrityResult {
  intact: boolean | null
  approvedVersion?: number
  currentVersion?: number
  message?: string
  warning?: boolean
}

export const contractApi = {
  parseDocument(docId: number) {
    return api.post<any, ParsedContract>(`/contracts/parse/${docId}`)
  },

  create(data: Record<string, any>) {
    return api.post<any, Contract>('/contracts', data)
  },

  list(params?: { status?: string; contractType?: string; departmentId?: number; keyword?: string; page?: number; size?: number }) {
    return api.get<any, { content: Contract[]; totalElements: number; totalPages: number }>('/contracts', { params })
  },

  detail(id: number) {
    return api.get<any, Contract>(`/contracts/${id}`)
  },

  update(id: number, data: Record<string, any>) {
    return api.put<any, Contract>(`/contracts/${id}`, data)
  },

  submit(id: number, data: { approverIds: number[]; deadline?: string }) {
    return api.post<any, any>(`/contracts/${id}/submit`, data)
  },

  approve(id: number, remark?: string) {
    return api.post<any, any>(`/contracts/${id}/approve`, { remark })
  },

  reject(id: number, remark: string) {
    return api.post<any, any>(`/contracts/${id}/reject`, { remark })
  },

  cancel(id: number) {
    return api.post<any, any>(`/contracts/${id}/cancel`)
  },

  integrity(id: number) {
    return api.get<any, IntegrityResult>(`/contracts/${id}/integrity`)
  },

  delete(id: number) {
    return api.delete(`/contracts/${id}`)
  },

  stats() {
    return api.get<any, Record<string, number>>('/contracts/stats')
  }
}
