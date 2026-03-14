/**
 * Natural-language descriptions for agent trace display (UX: "what the agent is doing").
 * Used as the primary label instead of raw tool names; tool name and args remain in expanded/debug view.
 */

import type { ToolCallDisplay } from './types'

const MEMORY_TOOLS = ['store_memory', 'get_memories', 'forget_memory'] as const

/** Default short description per tool name (French). */
const TOOL_DESCRIPTIONS: Record<string, string> = {
  list_projects: 'Chargement de la liste des projets',
  search_project: 'Recherche d\'un projet',
  list_activity_types: 'Chargement des natures d\'activité',
  get_recent_logs: 'Chargement des derniers temps enregistrés',
  get_time_logs_for_period: 'Chargement des temps sur la période',
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

/**
 * Returns a short natural-language description for a tool call (for trace display).
 * Prefer this over the raw tool name so the user sees "what the agent is doing".
 */
export function getToolCallDescription(tc: ToolCallDisplay): string {
  if (MEMORY_TOOLS.includes(tc.name as (typeof MEMORY_TOOLS)[number])) {
    return getMemoryToolDescription(tc)
  }
  return TOOL_DESCRIPTIONS[tc.name] ?? `Appel : ${tc.name}`
}

function getMemoryToolDescription(tc: ToolCallDisplay): string {
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
    return `Accès aux souvenirs : enregistrement de ${key}`
  }
  if (tc.name === 'get_memories') {
    if (kind) return `Accès aux souvenirs : lecture (${kind})`
    return 'Accès aux souvenirs : lecture'
  }
  if (tc.name === 'forget_memory') {
    if (memoryKey) return `Accès aux souvenirs : oubli de ${memoryKey}`
    if (kind) return `Accès aux souvenirs : oubli (kind: ${kind})`
    return 'Accès aux souvenirs : oubli'
  }
  return TOOL_DESCRIPTIONS[tc.name] ?? tc.name
}
