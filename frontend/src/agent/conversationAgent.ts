import { listProjects, createProject, logTime } from '../tools'

/**
 * Simple rule-based conversation agent.
 * Pipeline: transcription → intent detection → entity extraction → tool calls.
 *
 * Intent: LOG_TIME
 * Rule: presence of duration + project mention.
 * Examples: "30 minutes on HatCast", "I spent 45 min on Chrono EPS"
 */

// Supported duration patterns: X minutes, X min, Xh, X hour(s)
const MINUTES_REGEX = /(\d+)\s*min(?:ute)?s?/i
const HOURS_REGEX = /(\d+)\s*h(?:our)?s?/i
const DURATION_REGEX = /(\d+)\s*(min(?:ute)?s?|h(?:our)?s?)/i

// Project: "on PROJECT_NAME" or "for PROJECT_NAME"
// Stops at: "working", "doing", ".", end of string
const PROJECT_ON_REGEX = /\b(?:on|for)\s+([A-Za-z0-9][A-Za-z0-9\s]*?)(?=\s+working|\s+doing|\s+spent|\.|$)/i
const PROJECT_AFTER_MINUTES = /(\d+)\s*min(?:ute)?s?\s+(?:on|for)\s+([A-Za-z0-9][A-Za-z0-9\s]*?)(?=\s+working|\s+doing|\.|$)/i

/**
 * Extract duration in minutes from text.
 * Patterns: "30 minutes", "30 min", "1h", "1 hour"
 */
function extractDuration(text: string): number | null {
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
 * Extract project name from "on X" or "for X".
 * Example: "I spent 30 minutes on HatCast working on..." → "HatCast"
 */
function extractProjectName(text: string): string | null {
  const afterMinutes = text.match(PROJECT_AFTER_MINUTES)
  if (afterMinutes) return afterMinutes[2].trim()

  const onMatch = text.match(PROJECT_ON_REGEX)
  if (onMatch) return onMatch[1].trim()

  return null
}

/**
 * Extract note as remaining sentence (e.g. "working on X").
 * Example: "working on the selection algorithm"
 */
function extractNote(text: string): string | null {
  const working = text.match(/working\s+on\s+(.+?)(?:\.|$)/i)
  if (working) return working[1].trim()

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
}

/**
 * Process transcription and return agent response.
 */
export async function processTranscription(transcription: string): Promise<AgentResponse> {
  const trimmed = transcription.trim()
  if (!trimmed) {
    return { text: "I didn't catch that. Could you repeat?", success: false }
  }

  const duration = extractDuration(trimmed)
  const projectName = extractProjectName(trimmed)
  const note = extractNote(trimmed)

  // Missing duration
  if (!duration && projectName) {
    return { text: 'Can you estimate the duration?', success: false }
  }

  // Missing project
  if (!projectName && duration) {
    return { text: 'Which project was this for?', success: false }
  }

  if (!duration || !projectName) {
    return {
      text: "I need a duration and a project. Try: '30 minutes on HatCast'.",
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
