import { get, post, put, del } from '@/utils/request'

export interface KnowledgeBase {
  id: number
  name: string
  description: string
  enabled: number
  createdAt: string
  updatedAt: string
}

export interface KnowledgeDocument {
  id: number
  knowledgeBaseId: number
  name: string
  fileType: string
  fileSize: number
  status: 'PENDING' | 'PROCESSING' | 'DONE' | 'FAILED'
  errorMessage: string
  chunkCount: number
  createdAt: string
}

export interface ChunkVO {
  id: number
  documentId: number
  chunkIndex: number
  content: string
  tokenCount: number
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

export const createKb = (data: { name: string; description?: string }) =>
  post<KnowledgeBase>('/v1/knowledge-bases', data)

export const listKb = (params?: { page?: number; pageSize?: number; name?: string }) =>
  get<PageResult<KnowledgeBase>>('/v1/knowledge-bases', params)

export const getKb = (id: number) =>
  get<KnowledgeBase>(`/v1/knowledge-bases/${id}`)

export const updateKb = (id: number, data: { name?: string; description?: string; enabled?: number }) =>
  put<KnowledgeBase>(`/v1/knowledge-bases/${id}`, data)

export const deleteKb = (id: number) =>
  del<void>(`/v1/knowledge-bases/${id}`)

export const listDocuments = (kbId: number, params?: { page?: number; pageSize?: number }) =>
  get<PageResult<KnowledgeDocument>>(`/v1/knowledge-bases/${kbId}/documents`, params)

export const getDocument = (id: number) =>
  get<KnowledgeDocument>(`/v1/documents/${id}`)

export const deleteDocument = (id: number) =>
  del<void>(`/v1/documents/${id}`)

export const getChunks = (documentId: number) =>
  get<ChunkVO[]>(`/v1/documents/${documentId}/chunks`)

export const uploadDocument = (kbId: number, file: File) => {
  const form = new FormData()
  form.append('file', file)
  return fetch(`/api/v1/knowledge-bases/${kbId}/documents`, {
    method: 'POST',
    body: form,
  }).then(r => r.json()).then(res => {
    if (res.code !== 200) throw new Error(res.message)
    return res.data as KnowledgeDocument
  })
}
