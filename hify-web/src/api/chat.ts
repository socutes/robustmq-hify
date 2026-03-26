import { get, post, del } from '@/utils/request'

export interface ChatSession {
  id: number
  agentId: number
  title: string
  status: string
  createdAt: string
}

export interface ChatMessage {
  id: number
  sessionId: number
  role: 'user' | 'assistant' | 'system'
  content: string
  tokens: number
  finishReason: string
  latencyMs: number
  createdAt: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

export const createSession = (agentId: number) =>
  post<ChatSession>('/v1/chat/sessions', { agentId })

export const getSessions = (params?: { agentId?: number; page?: number; pageSize?: number }) =>
  get<PageResult<ChatSession>>('/v1/chat/sessions', params)

export const deleteSession = (sessionId: number) =>
  del<void>(`/v1/chat/sessions/${sessionId}`)

export const getMessages = (sessionId: number, params?: { page?: number; pageSize?: number }) =>
  get<PageResult<ChatMessage>>(`/v1/chat/sessions/${sessionId}/messages`, params)

/**
 * 流式发送消息 — 使用 fetch 手动处理 SSE（不用 EventSource，接口是 POST）
 * onDelta: 每收到 delta chunk 回调
 * onDone: 收到 done 事件回调
 * onError: 收到 error 事件或网络异常回调
 * 返回 AbortController，调用方可以 abort() 中止流
 */
export function streamMessage(
  sessionId: number,
  content: string,
  onDelta: (text: string) => void,
  onDone: (finishReason: string, latencyMs: number) => void,
  onError: (msg: string) => void,
): AbortController {
  const ctrl = new AbortController()

  ;(async () => {
    try {
      const resp = await fetch(`/api/v1/chat/sessions/${sessionId}/messages`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
        },
        body: JSON.stringify({ content, stream: true }),
        signal: ctrl.signal,
      })

      if (!resp.ok) {
        onError(`请求失败：HTTP ${resp.status}`)
        return
      }

      const reader = resp.body!.getReader()
      const decoder = new TextDecoder()
      let buf = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buf += decoder.decode(value, { stream: true })
        const lines = buf.split('\n')
        buf = lines.pop() ?? ''

        for (const line of lines) {
          if (!line.startsWith('data:')) continue
          const raw = line.slice(5).trim()
          if (!raw) continue
          try {
            const event = JSON.parse(raw)
            if (event.type === 'delta') onDelta(event.content ?? '')
            else if (event.type === 'done') onDone(event.finishReason ?? 'stop', event.latencyMs ?? 0)
            else if (event.type === 'error') onError(event.message ?? 'LLM 调用失败')
          } catch {
            // ignore malformed line
          }
        }
      }
    } catch (e: unknown) {
      if ((e as Error).name !== 'AbortError') {
        onError((e as Error).message ?? '网络异常')
      }
    }
  })()

  return ctrl
}
