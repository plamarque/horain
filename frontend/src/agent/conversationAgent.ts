import { listProjects, createProject, logTime } from '../tools'

/**
 * Simple rule-based conversation agent.
 * Detects intent and extracts duration/project from transcription.
 */

const DURATION_REGEX = /(\d+)\s*(min(?:ute)?s?|h(?:our)?s?)/i
const MINUTES_REGEX = /(\d+)\s*min(?:ute)?s?/i
const HOURS_REGEX = /(\d+)\s*h(?:our)?s?/i

/**
 * Extract duration in minutes from text.
 * Handles "30 minutes", "1 hour", "2h", etc.
 */
function extractDuration(text: string): number | null {
  const mins = text.match(MINUTES_REGEX)
  if (mins) return parseInt(mins[1], 10)

  const hours = text.match(HOURS_REGEX)
  if (hours) return parseInt(hours[1], 10) * 60

  // Generic: "30 min" or "1 hour"
  const match = text.match(DURATION_REGEX)
  if (!match) return null

  const num = parseInt(match[1], 10)
  const unit = match[2].toLowerCase()
  if (unit.startsWith('h')) return num * 60
  return num
}

/**
 * Extract project name: look for "on X" or "for X" or "X project".
 */
function extractProjectName(text: string): string | null {
  const onMatch = text.match(/\b(?:on|for)\s+([A-Za-z0-9\s]+?)(?:\s+(?:working|spent|logged)|\.|$)/i)
  if (onMatch) return onMatch[1].trim()

  // "X minutes on ProjectName"
  const onMinutes = text.match(/(\d+)\s*min(?:ute)?s?\s+on\s+([A-Za-z0-9\s]+)/i)
  if (onMinutes) return onMinutes[2].trim()

  // "spent X on ProjectName"
  const spent = text.match(/spent\s+\d+\s*min(?:ute)?s?\s+on\s+([A-Za-z0-9\s]+)/i)
  if (spent) return spent[1].trim()

  return null
}

/**
 * Extract note/activity description.
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
