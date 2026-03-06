import { listProjects, createProject, logTime } from '../tools'

/**
 * Simple rule-based conversation agent.
 *
 * Pipeline: transcription → intent detection → entity extraction → tool calls.
 *
 * Intent: LOG_TIME
 *   Detection rule: presence of duration + project mention.
 *   Examples: "30 minutes on HatCast", "I spent 45 min on Chrono EPS", "1h on Chrono EPS"
 *
 * Entity extraction (regex-based):
 *   - duration_minutes: "X minutes", "X min", "Xh", "X hour(s)"
 *   - project: "on PROJECT_NAME" or "for PROJECT_NAME"
 *   - note: "working on X" or "doing X"
 *
 * Tool flow: listProjects → match or createProject → logTime → enqueue sync
 */

// Duration: X minutes, X min, Xh, X hour(s) — EN & FR (minutes, min identiques)
// Also: demi heure, demi-heure, half hour = 30 min; une heure, one hour = 60 min
const MINUTES_REGEX = /(\d+)\s*min(?:ute)?s?/i
const HOURS_REGEX = /(\d+)\s*h(?:our)?s?/i
const DURATION_REGEX = /(\d+)\s*(min(?:ute)?s?|h(?:our)?s?)/i
const HALF_HOUR_REGEX = /(?:une?\s+)?(?:demi[- ]?heure|half\s+hour)/i
const ONE_HOUR_REGEX = /(?:une?\s+)?heure\b|one\s+hour/i

// Project: "on/for/sur/pour PROJECT_NAME" — EN: on, for | FR: sur, pour
const PROJECT_PREP = /(?:on|for|sur|pour)\s+([A-Za-z0-9][A-Za-z0-9\s\u00C0-\u024F]*?)(?=\s+(?:working|doing|travailler|à|\.)|\.|$)/iu
const PROJECT_AFTER_MINUTES = /(\d+)\s*min(?:ute)?s?\s+(?:on|for|sur|pour)\s+([A-Za-z0-9][A-Za-z0-9\s\u00C0-\u024F]*?)(?=\s+(?:working|doing|travailler|à|\.)|\.|$)/iu
const PROJECT_AFTER_HOURS = /(\d+)\s*h(?:our)?s?\s+(?:on|for|sur|pour)\s+([A-Za-z0-9][A-Za-z0-9\s\u00C0-\u024F]*?)(?=\s+(?:working|doing|travailler|à|\.)|\.|$)/iu

/**
 * Extract duration in minutes from text.
 * Patterns: "30 minutes", "30 min", "1h", "1 hour"
 */
function extractDuration(text: string): number | null {
  const halfHour = text.match(HALF_HOUR_REGEX)
  if (halfHour) return 30

  const oneHour = text.match(ONE_HOUR_REGEX)
  if (oneHour) return 60

  const mins = text.match(MINUTES_REGEX)
  if (mins) return parseInt(mins[1], 10)

  const hours = text.match(HOURS_REGEX)
  if (hours) return parseInt(hours[1], 10) * 60

  const match = text.match(DURATION_REGEX)
  if (!match) return null

  const num = parseInt(match[1], 10)
  const unit = match[2].toLowerCase()
  if (unit.startsWith('h')) return num * 60
  return num
}

/**
 * Extract project name from "on/for/sur/pour X".
 * EN: "30 minutes on HatCast" | FR: "30 minutes sur HatCast"
 */
function extractProjectName(text: string): string | null {
  const afterMinutes = text.match(PROJECT_AFTER_MINUTES)
  if (afterMinutes) return afterMinutes[2].trim()

  const afterHours = text.match(PROJECT_AFTER_HOURS)
  if (afterHours) return afterHours[2].trim()

  const prepMatch = text.match(PROJECT_PREP)
  if (prepMatch) return prepMatch[1].trim()

  return null
}

/**
 * Extract note: "working on X", "doing X" (EN) | "travailler sur X", "à travailler sur X" (FR)
 */
function extractNote(text: string): string | null {
  const working = text.match(/working\s+on\s+(.+?)(?:\.|$)/i)
  if (working) return working[1].trim()
  const doing = text.match(/doing\s+(.+?)(?:\.|$)/i)
  if (doing) return doing[1].trim()
  const travaillerSur = text.match(/(?:à\s+)?travailler\s+sur\s+(.+?)(?:\.|$)/i)
  if (travaillerSur) return travaillerSur[1].trim()
  return null
}

/**
 * Fuzzy match project name against project list.
 */
function matchProject(projects: Array<{ id: string; name: string }>, name: string): Array<{ id: string; name: string }> {
  const lower = name.toLowerCase().trim()
  return projects.filter((p) => p.name.toLowerCase().includes(lower) || lower.includes(p.name.toLowerCase()))
}

export interface AgentResponse {
  text: string
  success: boolean
  /** When we asked a clarifying question, store what we already have for the next turn */
  pendingContext?: { duration?: number; projectName?: string; note?: string }
}

/** Optional context from a previous turn (e.g. we have duration, waiting for project) */
export interface ConversationContext {
  duration?: number
  projectName?: string
  note?: string
}

/**
 * Heuristic: when user replies with a short phrase (no duration pattern), treat as project name
 * if we have pending duration. E.g. "HatCast" in response to "Which project?"
 */
function looksLikeProjectName(text: string): boolean {
  const t = text.trim()
  if (!t || t.length > 60) return false
  // Avoid treating "30" or "45 minutes" as project name
  if (/\d/.test(t)) return false
  return /^[A-Za-z\u00C0-\u024F0-9\s\-]+$/.test(t)
}

/**
 * Process transcription and return agent response.
 * Pass context when the user is replying to a clarifying question (e.g. "HatCast" after "Which project?").
 */
export async function processTranscription(
  transcription: string,
  context?: ConversationContext
): Promise<AgentResponse> {
  const trimmed = transcription.trim()
  if (!trimmed) {
    return { text: "I didn't catch that. Could you repeat?", success: false }
  }

  let duration = extractDuration(trimmed) ?? context?.duration
  let projectName = extractProjectName(trimmed) ?? context?.projectName
  let note = extractNote(trimmed) ?? context?.note

  // Merge with context: user said "HatCast" alone, we have duration from previous turn
  if (!projectName && duration && looksLikeProjectName(trimmed)) {
    projectName = trimmed
  }
  // User said "30 minutes" alone, we have project from previous turn
  const durationFromText = extractDuration(trimmed)
  if (!duration && projectName && durationFromText != null) {
    duration = durationFromText
  }
  if (duration == null) duration = context?.duration
  if (projectName == null) projectName = context?.projectName

  // Check analytics intent first (before "missing duration")
  const analyticsPattern =
    /\b(combien|how\s+many|how\s+much|what\s+did\s+i\s+do|temps\s+en\s+tout|temps\s+logg?é|total|summary|résumé)\b/i
  if (analyticsPattern.test(trimmed)) {
    return {
      text: "I can only log time entries. To ask questions about your tracked time (e.g. 'how many hours this week?'), the assistant needs an LLM. Configure OPENAI_API_KEY on the backend.",
      success: false,
    }
  }

  // Missing duration
  if (!duration && projectName) {
    return {
      text: 'Can you estimate the duration?',
      success: false,
      pendingContext: { projectName, note: note ?? undefined },
    }
  }

  // Missing project
  if (!projectName && duration) {
    return {
      text: 'Which project was this for?',
      success: false,
      pendingContext: { duration, note: note ?? undefined },
    }
  }

  if (!duration || !projectName) {
    return {
      text: "I need a duration and a project. Try: '30 minutes on HatCast' or 'une demi heure sur Festibask'.",
      success: false,
    }
  }

  const projects = await listProjects()
  const matches = matchProject(projects, projectName)

  if (matches.length === 0) {
    // Unknown project: create and log
    const created = await createProject(projectName)
    await logTime(created.id, duration, note || undefined)
    const noteStr = note ? ` Note: ${note}.` : ''
    return {
      text: `I created project "${created.name}" and logged ${duration} minutes.${noteStr}`,
      success: true,
    }
  }

  if (matches.length > 1) {
    const names = matches.map((m) => m.name).join(', ')
    return {
      text: `I found multiple projects: ${names}. Which one?`,
      success: false,
    }
  }

  // Single match
  const project = matches[0]
  await logTime(project.id, duration, note || undefined)
  const noteStr = note ? ` Note: ${note}.` : ''
  return {
    text: `Got it. ${duration} minutes logged for ${project.name}.${noteStr}`,
    success: true,
  }
}
