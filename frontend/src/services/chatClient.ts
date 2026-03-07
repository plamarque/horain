/**
 * Chat client for Horain backend.
 * Sends user messages to POST /chat/message and receives assistant responses.
 *
 * The backend orchestrates an LLM with tool calling; tools perform all reads and writes.
 * After a response, the frontend should trigger sync to pull any server-side changes.
 *
 * TODO: Full local-first write orchestration — when the backend requests a write action
 * (e.g. create_time_log), the frontend could also execute the local write path (IndexedDB + sync queue)
 * to preserve true local-first semantics. Currently backend writes go server-first; sync pull
 * refreshes local state. Unify write orchestration so that both paths remain consistent.
 */

import { apiPost } from './apiClient'

export interface ChatMessageResponse {
  assistantMessage: string
  toolCalls?: Array<{ name: string; arguments: string; result: string }>
  data?: unknown
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

/**
 * Send a message to the chat endpoint and get the assistant response.
 * Pass history for conversation context (e.g. corrections, follow-ups).
 * Pass contextEntries when the user has selected time log entries to work with.
 */
export async function sendChatMessage(
  message: string,
  history?: HistoryEntry[],
  contextEntries?: ContextEntry[]
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
    body.contextEntries = contextEntries.map((e) => ({
      id: e.id,
      projectId: e.projectId,
      projectName: e.projectName,
      durationMinutes: e.durationMinutes,
      note: e.note,
      loggedAt: e.loggedAt,
    }))
  }
  return apiPost<ChatMessageResponse>('/chat/message', body)
}
