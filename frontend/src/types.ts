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
}
