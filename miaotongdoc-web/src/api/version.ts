import api from './index'

export interface VersionItem {
  id: number
  documentId: number
  versionNumber: number
  filePath: string
  fileSize: number
  fileHash?: string
  changeSummary?: string
  createdBy: number
  createdByName?: string
  createdAt: string
}

export const versionApi = {
  getVersions(docId: number) {
    return api.get<any, VersionItem[]>(`/versions/${docId}`)
  },

  getVersion(docId: number, versionNumber: number) {
    return api.get<any, VersionItem>(`/versions/${docId}/${versionNumber}`)
  },

  downloadVersion(docId: number, versionNumber: number) {
    return api.get<any, Blob>(`/versions/${docId}/${versionNumber}/download`, {
      responseType: 'blob' as any
    })
  },

  restoreVersion(docId: number, versionNumber: number) {
    return api.post<any, any>(`/versions/${docId}/${versionNumber}/restore`)
  }
}
