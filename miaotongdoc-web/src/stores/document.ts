import { defineStore } from 'pinia'
import { ref } from 'vue'
import { documentApi, type Document } from '@/api/document'

export const useDocumentStore = defineStore('document', () => {
  const documents = ref<Document[]>([])
  const currentDocument = ref<Document | null>(null)
  const loading = ref(false)
  const total = ref(0)
  const page = ref(0)
  const pageSize = ref(10)

  async function fetchDocuments(params?: { type?: string; keyword?: string; departmentId?: number; sort?: string; page?: number; size?: number }) {
    loading.value = true
    try {
      const res = await documentApi.list(params)
      documents.value = res.content || []
      total.value = res.totalElements || 0
      page.value = res.number || 0
      pageSize.value = res.size || 50
    } finally {
      loading.value = false
    }
  }

  async function fetchDocument(id: number) {
    loading.value = true
    try {
      currentDocument.value = await documentApi.getById(id)
    } finally {
      loading.value = false
    }
  }

  async function createDocument(docType: string, title?: string) {
    const doc = await documentApi.create({ docType, title })
    documents.value.unshift(doc)
    return doc
  }

  async function deleteDocument(id: number) {
    await documentApi.delete(id)
    documents.value = documents.value.filter(d => d.id !== id)
  }

  return {
    documents,
    currentDocument,
    loading,
    total,
    page,
    pageSize,
    fetchDocuments,
    fetchDocument,
    createDocument,
    deleteDocument
  }
})
