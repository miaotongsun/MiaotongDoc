import { defineStore } from 'pinia'
import { ref } from 'vue'
import { documentApi, type Document } from '@/api/document'

export const useDocumentStore = defineStore('document', () => {
  const documents = ref<Document[]>([])
  const currentDocument = ref<Document | null>(null)
  const loading = ref(false)

  async function fetchDocuments(params?: { type?: string; keyword?: string; departmentId?: number; sort?: string; page?: number; size?: number }) {
    loading.value = true
    try {
      const res = await documentApi.list(params)
      documents.value = res.content || []
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
    fetchDocuments,
    fetchDocument,
    createDocument,
    deleteDocument
  }
})
