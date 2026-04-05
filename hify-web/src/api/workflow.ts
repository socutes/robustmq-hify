import { get, post, del } from '@/utils/request'

export interface WorkflowListItem {
  id: number
  name: string
  description: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface WorkflowDetail {
  id: number
  name: string
  description: string
  status: string
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
  createdAt: string
  updatedAt: string
}

export interface WorkflowNode {
  nodeKey: string
  type: string
  name: string
  config: Record<string, any>
}

export interface WorkflowEdge {
  sourceNodeKey: string
  targetNodeKey: string
  condition: string | null
}

export interface WorkflowCreateRequest {
  name: string
  description?: string
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
}

export function listWorkflows(params?: { page?: number; pageSize?: number; status?: string }) {
  return get<any>('/v1/workflows', { page: 1, pageSize: 20, ...params })
}

export function getWorkflow(id: number) {
  return get<any>(`/v1/workflows/${id}`)
}

export function createWorkflow(data: WorkflowCreateRequest) {
  return post<any>('/v1/workflows', data)
}

export function deleteWorkflow(id: number) {
  return del<any>(`/v1/workflows/${id}`)
}
