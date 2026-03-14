/**
 * Task type mapping for agent trace display (Phase 1).
 * Each tool name maps to a category and a French label for collapsible sections.
 */

export type TaskTypeId = 'exploring' | 'reading' | 'writing' | 'deleting' | 'other'

export interface TaskTypeInfo {
  taskType: TaskTypeId
  label: string
}

const TOOL_TO_TASK: Record<string, TaskTypeId> = {
  list_projects: 'exploring',
  search_project: 'exploring',
  list_activity_types: 'exploring',
  get_recent_logs: 'reading',
  get_time_logs_for_period: 'reading',
  search_time_logs: 'reading',
  get_current_datetime: 'reading',
  sum_time_by_project: 'reading',
  sum_time_for_period: 'reading',
  sum_billable_time_for_period: 'reading',
  sum_non_billable_time_for_period: 'reading',
  get_time_aggregated_for_chart: 'reading',
  get_memories: 'reading',
  create_time_log: 'writing',
  update_time_log: 'writing',
  create_project: 'writing',
  update_project: 'writing',
  create_activity_type: 'writing',
  update_activity_type: 'writing',
  propose_entries: 'writing',
  propose_chart: 'writing',
  store_memory: 'writing',
  delete_time_log: 'deleting',
  delete_project: 'deleting',
  delete_activity_type: 'deleting',
  forget_memory: 'deleting',
}

const TASK_LABELS: Record<TaskTypeId, string> = {
  exploring: 'Exploration',
  reading: 'Lecture',
  writing: 'Écriture',
  deleting: 'Suppression',
  other: 'Autre',
}

/**
 * Returns task type and display label for a tool name.
 */
export function getTaskType(toolName: string): TaskTypeInfo {
  const taskType = TOOL_TO_TASK[toolName] ?? 'other'
  return {
    taskType,
    label: TASK_LABELS[taskType],
  }
}
