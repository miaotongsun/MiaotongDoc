import api from './index'

export interface SigningTask {
  id: number
  documentId: number
  documentTitle: string
  title: string
  description?: string
  creatorUserId: number
  creatorName: string
  status: 'pending' | 'in_progress' | 'completed' | 'rejected' | 'cancelled'
  requiredCount: number
  completedCount: number
  deadline?: string
  completedAt?: string
  dueDate?: string
  records: SigningRecord[]
  createdAt: string
  updatedAt: string
}

export interface SigningRecord {
  id: number
  taskId: number
  signerUserId: number
  signerName: string
  signerOrder: number
  status: 'pending' | 'signed' | 'rejected'
  comment?: string
  signedAt?: string
}

export interface CreateSigningTaskRequest {
  documentId: number
  signerUserIds: number[]
  dueDate?: string
  message?: string
}

export const signingApi = {
  create(data: CreateSigningTaskRequest) {
    return api.post<any, SigningTask>('/signing/create', data)
  },

  getMyTasks(params?: { type?: 'initiated' | 'todo'; status?: string; page?: number; size?: number }) {
    return api.get<any, any>('/signing/tasks', { params })
  },

  getTask(taskId: number) {
    return api.get<any, SigningTask>(`/signing/tasks/${taskId}`)
  },

  confirm(taskId: number) {
    return api.post<any, any>('/signing/confirm', { taskId })
  },

  reject(taskId: number, remark: string) {
    return api.post<any, any>('/signing/reject', { taskId, remark })
  },

  cancel(taskId: number) {
    return api.put<any, any>(`/signing/tasks/${taskId}/cancel`)
  },

  getRecords(taskId: number) {
    return api.get<any, SigningRecord[]>(`/signing/tasks/${taskId}/records`)
  }
}
