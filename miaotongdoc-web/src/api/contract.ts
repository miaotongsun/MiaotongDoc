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

/** AI 审查结果（2026-08-09 新增） */
export interface ContractReview {
  riskLevel: 'low' | 'medium' | 'high'
  riskScore: number
  riskItems: Array<{ category?: string; description?: string; severity?: string }>
  keyClauses: Array<{ title?: string; summary?: string }>
  missingClauses: string[]
  suggestions: string[]
  summary: string
  raw?: string
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
  },

  /**
   * AI 审查（SSE 流式消费,2026-08-09 新增）
   * @param id 合同 ID
   * @param onProgress 接收 SSE 事件回调: docStatus | delta | done | error
   */
  reviewContract(id: number, onProgress: (event: { type: string; data: any }) => void, token: string) {
    return fetch(`/api/contracts/${id}/ai/review`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
        Accept: 'text/event-stream',
      },
    }).then(async (res) => {
      if (!res.ok || !res.body) {
        onProgress({ type: 'error', data: { message: `HTTP ${res.status}` } })
        return
      }
      const reader = res.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      // SSE 事件以空行分隔
      // 解析:event:<name>\ndata:<json>\n\n
      while (true) {
        const { value, done } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        let idx
        while ((idx = buffer.indexOf('\n\n')) !== -1) {
          const raw = buffer.slice(0, idx)
          buffer = buffer.slice(idx + 2)
          // 解析单条事件
          let name = 'message'
          let dataStr = ''
          for (const line of raw.split('\n')) {
            if (line.startsWith('event:')) name = line.substring(6).trim()
            else if (line.startsWith('data:')) dataStr += line.substring(5).trim()
          }
          if (dataStr) {
            try {
              onProgress({ type: name, data: JSON.parse(dataStr) })
            } catch {
              onProgress({ type: name, data: dataStr })
            }
          }
        }
      }
    }).catch((e) => {
      onProgress({ type: 'error', data: { message: String(e) } })
    })
  },

  /** 当前用户待审批列表（2026-08-09 新增） */
  myPending() {
    return api.get<any, Contract[]>('/contracts/my-pending')
  }
}

/** 合同付款计划（2026-08-09 新增） */
export interface ContractPayment {
  id?: number
  contractId?: number
  sequence?: number
  title?: string
  amount?: number
  currency?: string
  dueDate?: string
  paidDate?: string
  status?: 'pending' | 'paid' | 'overdue'
  reminderSent?: boolean
  remarks?: string
  createdAt?: string
  updatedAt?: string
}

/** AI 抽取付款计划的候选（前端用于确认后再入库） */
export interface PaymentPlanDraft {
  title?: string
  amount?: number | string
  currency?: string
  dueDate?: string
  remarks?: string
}

export const contractPaymentApi = {
  list(contractId: number) {
    return api.get<any, ContractPayment[]>(`/contracts/${contractId}/payments`)
  },

  create(contractId: number, data: ContractPayment) {
    return api.post<any, ContractPayment>(`/contracts/${contractId}/payments`, data)
  },

  update(contractId: number, paymentId: number, data: ContractPayment) {
    return api.put<any, ContractPayment>(`/contracts/${contractId}/payments/${paymentId}`, data)
  },

  delete(contractId: number, paymentId: number) {
    return api.delete(`/contracts/${contractId}/payments/${paymentId}`)
  },

  markPaid(contractId: number, paymentId: number, paidDate?: string) {
    return api.put<any, ContractPayment>(`/contracts/${contractId}/payments/${paymentId}/paid`, { paidDate })
  },

  /** AI 自动从合同文档中抽取付款计划（返回候选列表，用户确认后逐条 create） */
  extractByAi(contractId: number) {
    return api.post<any, PaymentPlanDraft[]>(`/contracts/${contractId}/payments/extract`, {})
  }
}
