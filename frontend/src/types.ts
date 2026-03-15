/**
 * Chart specification for agent-proposed visualizations.
 */
export interface ChartSpec {
  type: 'stackedBar' | 'pie' | 'bar'
  title: string
  categories: string[]
  series: { name: string; data: number[] }[]
}

/**
 * Activity type (nature + daily rate) for time log qualification.
 */
export interface ActivityType {
  code: string
  label: string
  dailyRateCents: number
}

/**
 * Time log entry displayed in the chat.
 */
export interface TimeLogEntry {
  id?: string
  projectId?: string
  projectName?: string
  durationMinutes: number
  note?: string
  billable?: boolean
  loggedAt: string
  createdAt?: string
  activityTypeCode?: string
  activityTypeLabel?: string
  dailyRateCents?: number
}

/**
 * Single tool call for display in the agent trace (name, args, result, success).
 * Enriched from backend payload for UI (e.g. success derived from result).
 */
export interface ToolCallDisplay {
  name: string
  arguments: string
  result: string
  success?: boolean
  /** Optional iteration index (Phase 4). */
  iterationIndex?: number
}

/**
 * Agent trace for one turn: tools executed, optionally grouped by iteration.
 * Session-only (not persisted); used to show "what the agent did" under the bubble.
 */
/** One completed reasoning phase (one "Thought" box per LLM turn). */
export interface ReasoningPhase {
  text: string
  durationMs?: number
  /** Cursor-style one-line summary (from lightweight LLM); when set, shown in white below "Thought for Xs". */
  summary?: string
}

export interface AgentTrace {
  toolCalls: ToolCallDisplay[]
  /** Completed reasoning phases (one per LLM turn); tool calls are shown between phases. */
  reasoningPhases?: ReasoningPhase[]
  /** Current reasoning text streaming (next phase not yet closed). */
  reasoningText?: string
  /** Optional duration of last reasoning phase in ms (for "Thought for Xs" header); also set in done payload. */
  reasoningDurationMs?: number
  /** Optional one-line summary (Cursor-style: shown in white below grey detail). When absent, derived from reasoningText. */
  reasoningSummary?: string
  /** Model name used for this turn (e.g. "o4-mini"); displayed in trace. */
  modelName?: string
}

/**
 * One segment of an assistant message for interleaved display (text then tools per turn).
 */
export type AssistantMessageSegment =
  | { type: 'text'; text: string }
  | { type: 'tools'; iterationIndex: number; toolCalls: ToolCallDisplay[] }

/**
 * Conversation message stored in memory.
 */
export interface Message {
  id: string
  role: 'user' | 'assistant'
  text: string
  timestamp: Date
  chart?: ChartSpec
  timeLogs?: TimeLogEntry[]
  /** True while assistant text is streaming into this message. */
  isStreaming?: boolean
  /** Backend turn id for feedback (thumb up/down). Only on assistant messages. */
  turnId?: string | null
  /** Tools and execution trace for this turn (assistant only). Session-only. */
  agentTrace?: AgentTrace
  /** When present, render message as interleaved text + tool blocks (assistant only). */
  segments?: AssistantMessageSegment[]
}
