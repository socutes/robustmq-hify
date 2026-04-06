import { get, post, put, del } from '@/utils/request'

export interface McpServerVO {
  id: number
  name: string
  endpoint: string
  description: string
  enabled: number
  createdAt: string
  updatedAt: string
  tools?: string[]
}

export interface McpServerCreateDTO {
  name: string
  endpoint: string
  description?: string
}

export interface McpServerUpdateDTO {
  name: string
  endpoint: string
  description?: string
  enabled?: number
}

export interface McpTestResult {
  success: boolean
  latencyMs: number
  tools: string[]
  errorMessage: string | null
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

export const getMcpServerList = (params: { page: number; pageSize: number; enabled?: number }) =>
  get<PageResult<McpServerVO>>('/v1/mcp-servers', params)

export const getMcpServerDetail = (id: number) =>
  get<McpServerVO>(`/v1/mcp-servers/${id}`)

export const createMcpServer = (data: McpServerCreateDTO) =>
  post<McpServerVO>('/v1/mcp-servers', data)

export const updateMcpServer = (id: number, data: McpServerUpdateDTO) =>
  put<McpServerVO>(`/v1/mcp-servers/${id}`, data)

export const deleteMcpServer = (id: number) =>
  del<void>(`/v1/mcp-servers/${id}`)

export const testMcpServer = (id: number) =>
  post<McpTestResult>(`/v1/mcp-servers/${id}/test`, {})

export interface McpToolDetail {
  name: string
  description: string
  inputSchema: {
    type: string
    properties?: Record<string, { type: string; description?: string }>
    required?: string[]
  } | null
  requiredParams: string[] | null
}

export interface McpDebugResult {
  success: boolean
  result: string | null
  elapsedMs: number
  errorMessage: string | null
}

export const getMcpServerTools = (id: number) =>
  get<McpToolDetail[]>(`/v1/mcp-servers/${id}/tools`)

export const debugMcpTool = (id: number, data: { toolName: string; arguments: Record<string, unknown> }) =>
  post<McpDebugResult>(`/v1/mcp-servers/${id}/debug`, data)
