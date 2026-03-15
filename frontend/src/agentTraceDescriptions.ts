/**
 * Natural-language descriptions for agent trace display (UX: "what the agent is doing").
 * Used as the primary label instead of raw tool names; tool name and args remain in expanded/debug view.
 */

import type { ToolCallDisplay } from './types'

const MEMORY_TOOLS = ['store_memory', 'get_memories', 'forget_memory'] as const

const PERIOD_TOOLS = [
  'get_time_logs_for_period',
  'sum_time_for_period',
  'sum_billable_time_for_period',
  'sum_non_billable_time_for_period',
  'get_time_aggregated_for_chart',
] as const

/** French month names (index 0 = January). */
const MONTH_NAMES = [
  'Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin',
  'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre',
]

/** Parse ISO date string (YYYY-MM-DD or full ISO) to { year, monthIndex }; returns null if invalid. */
function parseIsoDate(str: string): { year: number; monthIndex: number } | null {
  if (!str || typeof str !== 'string') return null
  const match = str.trim().match(/^(\d{4})-(\d{2})/)
  if (!match) return null
  const year = parseInt(match[1], 10)
  const monthIndex = parseInt(match[2], 10) - 1
  if (monthIndex < 0 || monthIndex > 11) return null
  return { year, monthIndex }
}

/** Format period from start/end args for display (e.g. "mois de Janvier 2026" or "période Janvier 2026 - Mars 2026"). */
function formatPeriodLabel(args: Record<string, unknown>): string | null {
  const startStr = typeof args.start === 'string' ? args.start : null
  const endStr = typeof args.end === 'string' ? args.end : null
  if (!startStr && !endStr) return null
  const start = startStr ? parseIsoDate(startStr) : null
  const end = endStr ? parseIsoDate(endStr) : null
  if (!start && !end) return null
  const fmt = (d: { year: number; monthIndex: number }) => `${MONTH_NAMES[d.monthIndex]} ${d.year}`
  if (start && end && start.year === end.year && start.monthIndex === end.monthIndex) {
    return `mois de ${fmt(start)}`
  }
  if (start && end) {
    return `période ${fmt(start)} – ${fmt(end)}`
  }
  if (start) return fmt(start)
  if (end) return fmt(end)
  return null
}

/** Default short description per tool name (French). */
const TOOL_DESCRIPTIONS: Record<string, string> = {
  list_projects: 'Chargement de la liste des projets',
  search_project: 'Recherche d\'un projet',
  list_activity_types: 'Chargement des natures d\'activité',
  get_recent_logs: 'Chargement des derniers temps enregistrés',
  get_time_logs_for_period: 'Chargement des temps',
  search_time_logs: 'Recherche d\'entrées par mot-clé',
  get_current_datetime: 'Récupération de la date et l\'heure',
  sum_time_by_project: 'Total des temps par projet',
  sum_time_for_period: 'Total des temps sur la période',
  sum_billable_time_for_period: 'Total des temps facturables',
  sum_non_billable_time_for_period: 'Total des temps non facturables',
  get_time_aggregated_for_chart: 'Chargement des temps pour graphique agrégé',
  get_memories: 'Accès aux souvenirs : lecture',
  create_time_log: 'Enregistrement d\'un temps',
  update_time_log: 'Mise à jour d\'un temps',
  create_project: 'Création d\'un projet',
  update_project: 'Mise à jour d\'un projet',
  create_activity_type: 'Création d\'une nature d\'activité',
  update_activity_type: 'Mise à jour d\'une nature d\'activité',
  propose_entries: 'Préparation des cartes d\'activités',
  propose_chart: 'Préparation du graphique',
  store_memory: 'Accès aux souvenirs : enregistrement',
  delete_time_log: 'Suppression d\'un temps',
  delete_project: 'Suppression d\'un projet',
  delete_activity_type: 'Suppression d\'une nature d\'activité',
  forget_memory: 'Accès aux souvenirs : oubli',
}

/** Base labels for period tools (without period suffix). */
const PERIOD_TOOL_BASES: Record<string, string> = {
  get_time_logs_for_period: 'Chargement des temps',
  sum_time_for_period: 'Total des temps sur la période',
  sum_billable_time_for_period: 'Chargement des temps facturables',
  sum_non_billable_time_for_period: 'Chargement des temps non facturables',
  get_time_aggregated_for_chart: 'Chargement des temps pour graphique agrégé',
}

export type ToolCallDescriptionParts = { base: string; qualification: string }

/**
 * Returns base label and optional qualification for a tool call (for styled display: name in one color, qualification muted).
 */
export function getToolCallDescriptionParts(tc: ToolCallDisplay): ToolCallDescriptionParts {
  if (MEMORY_TOOLS.includes(tc.name as (typeof MEMORY_TOOLS)[number])) {
    return getMemoryToolDescriptionParts(tc)
  }
  if (PERIOD_TOOLS.includes(tc.name as (typeof PERIOD_TOOLS)[number])) {
    return getPeriodToolDescriptionParts(tc)
  }
  const full = TOOL_DESCRIPTIONS[tc.name] ?? `Appel : ${tc.name}`
  return { base: full, qualification: '' }
}

/**
 * Returns a short natural-language description for a tool call (for trace display).
 * Prefer this over the raw tool name so the user sees "what the agent is doing".
 */
export function getToolCallDescription(tc: ToolCallDisplay): string {
  const { base, qualification } = getToolCallDescriptionParts(tc)
  return qualification ? `${base}${qualification}` : base
}

function getPeriodToolDescription(tc: ToolCallDisplay): string {
  const { base, qualification } = getPeriodToolDescriptionParts(tc)
  return qualification ? `${base}${qualification}` : base
}

function getPeriodToolDescriptionParts(tc: ToolCallDisplay): ToolCallDescriptionParts {
  let args: Record<string, unknown> = {}
  try {
    if (tc.arguments?.trim()) args = JSON.parse(tc.arguments) as Record<string, unknown>
  } catch {
    /* use empty args */
  }
  const base = PERIOD_TOOL_BASES[tc.name] ?? TOOL_DESCRIPTIONS[tc.name] ?? tc.name
  const periodLabel = formatPeriodLabel(args)
  const qualification = periodLabel != null && periodLabel.length > 0 ? ` ${periodLabel}` : ''
  return { base, qualification }
}

function getMemoryToolDescription(tc: ToolCallDisplay): string {
  const { base, qualification } = getMemoryToolDescriptionParts(tc)
  return qualification ? `${base}${qualification}` : base
}

function getMemoryToolDescriptionParts(tc: ToolCallDisplay): ToolCallDescriptionParts {
  let args: Record<string, unknown> = {}
  try {
    if (tc.arguments?.trim()) args = JSON.parse(tc.arguments) as Record<string, unknown>
  } catch {
    /* use empty args */
  }
  const kind = typeof args.kind === 'string' ? args.kind : ''
  const memoryKey = typeof args.memoryKey === 'string' ? args.memoryKey : ''

  if (tc.name === 'store_memory') {
    const key = memoryKey || kind || 'souvenir'
    return { base: 'Accès aux souvenirs : enregistrement de', qualification: ` ${key}` }
  }
  if (tc.name === 'get_memories') {
    return { base: 'Accès aux souvenirs : lecture', qualification: kind ? ` (${kind})` : '' }
  }
  if (tc.name === 'forget_memory') {
    if (memoryKey) return { base: 'Accès aux souvenirs : oubli de', qualification: ` ${memoryKey}` }
    if (kind) return { base: 'Accès aux souvenirs : oubli', qualification: ` (kind: ${kind})` }
    return { base: 'Accès aux souvenirs : oubli', qualification: '' }
  }
  const full = TOOL_DESCRIPTIONS[tc.name] ?? tc.name
  return { base: full, qualification: '' }
}
