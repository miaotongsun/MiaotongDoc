export type DocType = 'word' | 'cell' | 'slide'

export interface DocTypeConfig {
  label: string
  brandName: string
  ext: string
  icon: string
  color: string
  ooDocumentType: string
}

export const DOC_TYPE_CONFIG: Record<DocType, DocTypeConfig> = {
  word: {
    label: '文字文档',
    brandName: 'MiaotongWord',
    ext: 'docx',
    icon: 'Document',
    color: '#2B579A',
    ooDocumentType: 'word'
  },
  cell: {
    label: '电子表格',
    brandName: 'MiaotongSheet',
    ext: 'xlsx',
    icon: 'Grid',
    color: '#217346',
    ooDocumentType: 'cell'
  },
  slide: {
    label: '演示文稿',
    brandName: 'MiaotongPPT',
    ext: 'pptx',
    icon: 'Picture',
    color: '#D24726',
    ooDocumentType: 'slide'
  }
}

export function getDocTypeConfig(docType: string): DocTypeConfig {
  return DOC_TYPE_CONFIG[docType as DocType] || DOC_TYPE_CONFIG.word
}

export function getFileTypeIcon(fileType: string): string {
  switch (fileType) {
    case 'docx': return 'Document'
    case 'xlsx': return 'Grid'
    case 'pptx': return 'Picture'
    default: return 'Document'
  }
}

export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}
