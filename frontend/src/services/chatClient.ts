/**
 * Chat client for Horain backend.
 * Sends user messages to POST /chat/message and receives assistant responses.
 * The backend orchestrates an LLM with tool calling; tools perform all reads and writes.
 */

import { apiPost, getStreamRequestConfig } from './apiClient'

export interface ChatMessageResponse {
  assistantMessage: string
  toolCalls?: Array<{ name: string; arguments: string; result: string }>
  data?: unknown
  /** Turn id for feedback API (thumb up/down). */
  turnId?: string | null
}

/** Maximum number of history messages to send (keeps context window manageable). */
const MAX_HISTORY_MESSAGES = 20

export interface HistoryEntry {
  role: 'user' | 'assistant'
  text: string
}

export interface ContextEntry {
  id?: string
  projectId?: string
  projectName?: string
  durationMinutes: number
  note?: string
  loggedAt: string
}

export interface ContextProject {
  id: string
  name: string
  description?: string
}

/**
 * Send a message to the chat endpoint and get the assistant response.
 * Pass history for conversation context (e.g. corrections, follow-ups).
 * Pass contextEntries when the user has selected time log entries to work with.
 * Pass signal to allow cancellation (e.g. when user clicks Stop).
 */
export async function sendChatMessage(
  message: string,
  history?: HistoryEntry[],
  contextEntries?: ContextEntry[],
  contextProjects?: ContextProject[],
  init?: { signal?: AbortSignal }
): Promise<ChatMessageResponse> {
  const trimmed =
    history?.slice(-MAX_HISTORY_MESSAGES).map((m) => ({
      role: m.role,
      content: m.text,
    })) ?? []
  const body: Record<string, unknown> = {
    message,
    history: trimmed,
  }
  if (contextEntries?.length) {
    const entriesWithId = contextEntries.filter((e): e is ContextEntry & { id: string } => !!e.id)
    if (entriesWithId.length) {
      body.contextEntries = entriesWithId.map((e) => ({
        id: e.id,
        projectId: e.projectId,
        projectName: e.projectName,
        durationMinutes: e.durationMinutes,
        note: e.note,
        loggedAt: e.loggedAt,
      }))
    }
  }
  if (contextProjects?.length) {
    body.contextProjects = contextProjects.map((p) => ({
      id: p.id,
      name: p.name,
      description: p.description,
    }))
  }
  return apiPost<ChatMessageResponse>('/chat/message', body, init)
}

/**
 * Submit user feedback (thumb up/down) for a turn.
 */
export async function sendFeedback(
  turnId: string,
  rating: 'up' | 'down',
  reasonCode?: string,
  comment?: string
): Promise<void> {
  const body: Record<string, unknown> = { turnId, rating }
  if (reasonCode != null) body.reasonCode = reasonCode
  if (comment != null) body.comment = comment
  await apiPost<{ ok?: boolean }>('/chat/feedback', body)
}

export interface StreamCallbacks {
  onChunk: (text: string) => void
  onDone: (payload: ChatMessageResponse) => void
  onError?: (err: Error) => void
}

/**
 * Send a message to the streaming endpoint and process SSE events.
 * Calls onChunk for each text delta, onDone with the final payload, and onError on failure or error event.
 * Pass signal to allow cancellation (e.g. when user clicks Stop).
 */
export async function sendChatMessageStream(
  message: string,
  callbacks: StreamCallbacks,
  history?: HistoryEntry[],
  contextEntries?: ContextEntry[],
  contextProjects?: ContextProject[],
  init?: { signal?: AbortSignal }
): Promise<void> {
  const trimmed =
    history?.slice(-MAX_HISTORY_MESSAGES).map((m) => ({
      role: m.role,
      content: m.text,
    })) ?? []
  const body: Record<string, unknown> = {
    message,
    history: trimmed,
  }
  if (contextEntries?.length) {
    const entriesWithId = contextEntries.filter((e): e is ContextEntry & { id: string } => !!e.id)
    if (entriesWithId.length) {
      body.contextEntries = entriesWithId.map((e) => ({
        id: e.id,
        projectId: e.projectId,
        projectName: e.projectName,
        durationMinutes: e.durationMinutes,
        note: e.note,
        loggedAt: e.loggedAt,
      }))
    }
  }
  if (contextProjects?.length) {
    body.contextProjects = contextProjects.map((p) => ({
      id: p.id,
      name: p.name,
      description: p.description,
    }))
  }

  const { url, headers } = getStreamRequestConfig('/chat/message/stream')
  const res = await fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
    signal: init?.signal,
  })
  if (!res.ok) {
    const text = await res.text()
    let msg = `API error ${res.status}`
    try {
      const json = text ? (JSON.parse(text) as Record<string, unknown>) : null
      if (json && typeof json.message === 'string') msg = json.message
      else if (json && typeof json.error === 'string') msg = json.error
    } catch {
      /* ignore */
    }
    const err = new Error(msg) as Error & { status?: number }
    err.status = res.status
    callbacks.onError?.(err)
    throw err
  }

  const reader = res.body?.getReader()
  if (!reader) {
    const err = new Error('No response body')
    callbacks.onError?.(err)
    throw err
  }

  const decoder = new TextDecoder()
  let buffer = ''
  let currentEvent: string | null = null
  let currentData: string[] = []

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() ?? ''

      for (const line of lines) {
        if (line.startsWith('event:')) {
          if (currentEvent !== null && currentData.length > 0) {
            const dataStr = currentData.join('\n').trim()
            dispatchEvent(currentEvent, dataStr, callbacks)
          }
          currentEvent = line.slice(6).trim()
          currentData = []
        } else if (line.startsWith('data:')) {
          currentData.push(line.slice(5))
        } else if (line === '' && currentEvent !== null) {
          if (currentData.length > 0) {
            const dataStr = currentData.join('\n').trim()
            dispatchEvent(currentEvent, dataStr, callbacks)
          }
          currentEvent = null
          currentData = []
        }
      }
    }
    if (currentEvent !== null && currentData.length > 0) {
      const dataStr = currentData.join('\n').trim()
      dispatchEvent(currentEvent, dataStr, callbacks)
    }
  } catch (e) {
    if ((e as Error).name === 'AbortError') {
      callbacks.onError?.(e as Error)
    }
    throw e
  }
}

function dispatchEvent(
  event: string,
  dataStr: string,
  callbacks: StreamCallbacks
): void {
  if (!dataStr) return
  try {
    if (event === 'chunk') {
      const data = JSON.parse(dataStr) as { text?: string }
      if (typeof data.text === 'string') {
        callbacks.onChunk(data.text)
      }
    } else if (event === 'done') {
      const payload = JSON.parse(dataStr) as ChatMessageResponse
      callbacks.onDone(payload)
    } else if (event === 'error') {
      const data = JSON.parse(dataStr) as { message?: string }
      const err = new Error(data.message ?? 'Stream error')
      callbacks.onError?.(err)
    }
  } catch (e) {
    callbacks.onError?.(e as Error)
  }
}
