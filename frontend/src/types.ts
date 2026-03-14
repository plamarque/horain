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
export interface AgentTrace {
  toolCalls: ToolCallDisplay[]
  /** Optional reasoning text (Phase 2: filled by stream or done when model exposes it). */
  reasoningText?: string
}

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
}
