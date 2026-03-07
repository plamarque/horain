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
 * Time log entry displayed in the chat.
 */
export interface TimeLogEntry {
  id?: string
  projectId?: string
  projectName?: string
  durationMinutes: number
  note?: string
  loggedAt: string
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
}
