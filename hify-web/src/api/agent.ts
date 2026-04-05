import { get, post, put, del } from '@/utils/request'
import type { PageResult } from '@/api/provider'

export interface AgentListItem {
  id: number
  name: string
  description: string
  modelConfigId: number
  temperature: number
  enabled: number
  toolCount: number
  workflowId?: number | null
  knowledgeBaseId?: number | null
  createdAt: string
}

export interface AgentDetail {
  id: number
  name: string
  description: string
  systemPrompt: string
  modelConfigId: number
  temperature: number
  maxTokens: number
  maxContextTurns: number
  enabled: number
  toolIds: number[]
  createdAt: string
  updatedAt: string
}

export interface AgentCreateDTO {
  name: string
  description?: string
  systemPrompt?: string
  modelConfigId: number
  temperature: number
  maxTokens: number
  maxContextTurns: number
  toolIds?: number[]
}

export interface AgentUpdateDTO {
  name: string
  description?: string
  systemPrompt?: string
  modelConfigId: number
  temperature?: number
  maxTokens?: number
  maxContextTurns?: number
}

export interface ModelOption {
  modelConfigId: number
  modelName: string
  providerName: string
  providerType: string
}

export const getAgentList = (params: { page: number; pageSize: number; enabled?: boolean }) =>
  get<PageResult<AgentListItem>>('/v1/agents', params)

export const getAgentDetail = (id: number) =>
  get<AgentDetail>(`/v1/agents/${id}`)

export const createAgent = (data: AgentCreateDTO) =>
  post<AgentDetail>('/v1/agents', data)

export const updateAgent = (id: number, data: AgentUpdateDTO) =>
  put<AgentDetail>(`/v1/agents/${id}`, data)

export const bindAgentTools = (id: number, toolIds: number[]) =>
  put<void>(`/v1/agents/${id}/tools`, { toolIds })

export const deleteAgent = (id: number) =>
  del<void>(`/v1/agents/${id}`)

/** 从 provider 列表中提取所有已启用的模型，供 Agent 表单选择 */
export const getModelOptions = async (): Promise<ModelOption[]> => {
  const { getProviderList } = await import('@/api/provider')
  const res = await getProviderList({ page: 1, pageSize: 100 })
  const options: ModelOption[] = []
  for (const provider of res.list) {
    if (!provider.enabled) continue
    for (const model of provider.models ?? []) {
      if (!model.enabled) continue
      options.push({
        modelConfigId: model.id,
        modelName: model.name,
        providerName: provider.name,
        providerType: provider.type,
      })
    }
  }
  return options
}
