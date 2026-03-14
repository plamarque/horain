<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { AgentTrace, ToolCallDisplay } from '../types'
import { getTaskType, type TaskTypeId } from '../agentTraceTaskTypes'
import { getToolCallDescription } from '../agentTraceDescriptions'

const TRUNCATE_LEN = 200
const TASK_TYPE_ORDER: TaskTypeId[] = ['exploring', 'reading', 'writing', 'deleting', 'other']

function truncate(str: string, maxLen: number): string {
  if (!str || str.length <= maxLen) return str
  return str.slice(0, maxLen) + '…'
}

/** Extract error message from tool result JSON when present. */
function extractErrorFromResult(result: string): string | null {
  if (!result?.trim()) return null
  try {
    const obj = JSON.parse(result.trim()) as Record<string, unknown>
    if (typeof obj?.error === 'string') return obj.error
    return null
  } catch {
    return null
  }
}

/** Group tool calls by task type, preserving order within each group. */
function groupCallsByTaskType(calls: ToolCallDisplay[]): { taskType: TaskTypeId; label: string; calls: ToolCallDisplay[] }[] {
  const byType = new Map<TaskTypeId, ToolCallDisplay[]>()
  for (const tc of calls) {
    const { taskType } = getTaskType(tc.name)
    const list = byType.get(taskType) ?? []
    list.push(tc)
    byType.set(taskType, list)
  }
  const result: { taskType: TaskTypeId; label: string; calls: ToolCallDisplay[] }[] = []
  for (const taskType of TASK_TYPE_ORDER) {
    const list = byType.get(taskType)
    if (list?.length) {
      result.push({ taskType, label: getTaskType(list[0].name).label, calls: list })
    }
  }
  return result
}

const props = withDefaults(
  defineProps<{
    /** Trace of tool calls for this turn. When present and not streaming, show summary. */
    agentTrace?: AgentTrace | null
    /** When true, show a minimal "thinking" indicator in this block. */
    isStreaming?: boolean
  }>(),
  {
    agentTrace: undefined,
    isStreaming: false,
  }
)

const collapsed = ref(true)
/** Reasoning block ("Thought for Xs") collapsed by default. */
const reasoningCollapsed = ref(true)
/** Accordion: id of the single open section (e.g. "0-exploring") or null. */
const openedSectionKey = ref<string | null>(null)
/** Which iteration (turn) is expanded when not streaming; null = all collapsed. During streaming, current turn is always expanded. */
const expandedIterationIndex = ref<number | null>(null)

/** Show the trace list (sections) when we have any tool calls (during streaming or after). */
const showTraceContent = computed(() => {
  return (props.agentTrace?.toolCalls?.length ?? 0) > 0
})

/** Show "Thinking..." only when streaming and no tool calls yet. */
const showThinkingOnly = computed(() => {
  return (props.isStreaming ?? false) && !(props.agentTrace?.toolCalls?.length ?? 0)
})

/** Whether we have reasoning text to show (Cursor-style "Thought for Xs"). */
const hasReasoning = computed(() => {
  const t = props.agentTrace?.reasoningText
  return typeof t === 'string' && t.length > 0
})

/** "Thought for Xs" label: duration in seconds from reasoningDurationMs, or empty. */
const reasoningDurationLabel = computed(() => {
  const ms = props.agentTrace?.reasoningDurationMs
  if (ms == null || ms < 0) return ''
  const sec = Math.round(ms / 1000)
  return sec <= 0 ? '1' : String(sec)
})

/** One-line summary (Cursor-style: visible in white below the grey detail). Uses backend reasoningSummary when present, else first sentence or ~120 chars of reasoningText. */
const reasoningSummaryLine = computed(() => {
  const explicit = props.agentTrace?.reasoningSummary
  if (explicit && typeof explicit === 'string' && explicit.trim()) return explicit.trim()
  const t = props.agentTrace?.reasoningText
  if (!t || typeof t !== 'string') return ''
  const trimmed = t.trim()
  if (!trimmed) return ''
  const maxLen = 120
  const firstLine = trimmed.split(/\n/)[0]?.trim() ?? trimmed
  const firstSentence = firstLine.match(/^[^.!?]+[.!?]?/)?.[0]?.trim() ?? firstLine
  if (firstSentence.length <= maxLen) return firstSentence
  return firstSentence.slice(0, maxLen).trimEnd() + '…'
})

/** Group tool calls by iteration index (Tour 1, Tour 2, …). */
const toolCallsByIteration = computed(() => {
  const calls = props.agentTrace?.toolCalls ?? []
  if (!calls.length) return []
  const hasIteration = calls.some((tc) => typeof tc.iterationIndex === 'number')
  if (!hasIteration) return [{ iterationLabel: null as string | null, calls }]
  const byIter = new Map<number, ToolCallDisplay[]>()
  for (const tc of calls) {
    const idx = typeof tc.iterationIndex === 'number' ? tc.iterationIndex : -1
    const list = byIter.get(idx) ?? []
    list.push(tc)
    byIter.set(idx, list)
  }
  const sorted = [...byIter.entries()].sort((a, b) => a[0] - b[0])
  return sorted.map(([idx, list]) => ({
    iterationLabel: idx >= 0 ? `Tour ${idx + 1}` : null,
    calls: list,
  }))
})

/** Per iteration: sections grouped by task type (Exploration, Lecture, etc.). */
const sectionsByIteration = computed(() => {
  return toolCallsByIteration.value.map((group, iterIndex) => ({
    iterationLabel: group.iterationLabel,
    sections: groupCallsByTaskType(group.calls).map((s) => ({
      ...s,
      sectionKey: `${iterIndex}-${s.taskType}`,
    })),
  }))
})

/** Index of the "current" turn during streaming (last iteration). */
const currentIterationIndex = computed(() => {
  if (!(props.isStreaming ?? false) || !sectionsByIteration.value.length) return -1
  return sectionsByIteration.value.length - 1
})

/** One-line summary for a turn (e.g. "Lecture · 2 outils, Exploration · 1 outil"). */
function iterationSummary(iter: { sections: { label: string; calls: ToolCallDisplay[] }[] }): string {
  return iter.sections
    .map((s) => `${s.label} · ${s.calls.length} ${s.calls.length === 1 ? 'outil' : 'outils'}`)
    .join(', ')
}

/** Whether this iteration (turn) is expanded: during stream only current; when not streaming, only expandedIterationIndex. */
function isIterationExpanded(gi: number): boolean {
  if (props.isStreaming && gi === currentIterationIndex.value) return true
  return expandedIterationIndex.value === gi
}

function toggleIteration(gi: number) {
  if (props.isStreaming && gi === currentIterationIndex.value) return
  expandedIterationIndex.value = expandedIterationIndex.value === gi ? null : gi
}

/** When stream ends, expand the last turn so user sees the final result. */
watch(
  () => props.isStreaming,
  (streaming, wasStreaming) => {
    if (wasStreaming === true && streaming === false && sectionsByIteration.value.length > 0) {
      expandedIterationIndex.value = sectionsByIteration.value.length - 1
    }
  }
)

/** Summary for main toggle: unique type labels present (e.g. "Exploration, Lecture, Écriture"). */
const summaryLabel = computed(() => {
  if (!props.agentTrace?.toolCalls?.length) return ''
  const labels = new Set<string>()
  for (const tc of props.agentTrace.toolCalls) {
    labels.add(getTaskType(tc.name).label)
  }
  return [...labels].join(', ')
})

function errorMessage(tc: ToolCallDisplay): string | null {
  return extractErrorFromResult(tc.result)
}

function toggleSection(key: string) {
  openedSectionKey.value = openedSectionKey.value === key ? null : key
}

/** Section header: natural-language description when single call, else "Label · N outils". */
function sectionSummary(section: { label: string; calls: ToolCallDisplay[] }): string {
  if (section.calls.length === 1) {
    return getToolCallDescription(section.calls[0])
  }
  return `${section.label} · ${section.calls.length} outils`
}
</script>

<template>
  <div
    v-if="showThinkingOnly || showTraceContent || hasReasoning"
    class="agent-trace-block"
    role="region"
    aria-label="Agent execution trace"
  >
    <!-- Streaming and no tool calls yet: minimal indicator -->
    <div v-if="showThinkingOnly" class="agent-trace-thinking">
      <span class="agent-trace-thinking-text">Thinking...</span>
    </div>
    <!-- Reasoning (Cursor-style "Thought for Xs"): optional, only when model exposes it -->
    <template v-if="hasReasoning">
      <button
        type="button"
        class="agent-trace-reasoning-toggle"
        :aria-expanded="!reasoningCollapsed"
        @click="reasoningCollapsed = !reasoningCollapsed"
      >
        <span class="agent-trace-reasoning-label">
          Thought for {{ reasoningDurationLabel || '?' }}s
        </span>
        <span class="agent-trace-reasoning-chevron" aria-hidden="true">
          {{ reasoningCollapsed ? '▼' : '▲' }}
        </span>
      </button>
      <div v-show="!reasoningCollapsed" class="agent-trace-reasoning-content">
        <p class="agent-trace-reasoning-text">{{ agentTrace?.reasoningText }}</p>
      </div>
      <p v-if="reasoningSummaryLine" class="agent-trace-reasoning-summary">{{ reasoningSummaryLine }}</p>
    </template>
    <!-- Tool calls (during stream or after): collapsible block with sections by task type -->
    <template v-if="showTraceContent">
      <button
        type="button"
        class="agent-trace-toggle"
        :aria-expanded="!collapsed || isStreaming"
        @click="collapsed = !collapsed"
      >
        <span class="agent-trace-toggle-label">
          Outils utilisés : {{ summaryLabel }}
        </span>
        <span class="agent-trace-toggle-icon" aria-hidden="true">{{ (collapsed && !isStreaming) ? '▼' : '▲' }}</span>
      </button>
      <div v-show="!collapsed || isStreaming" class="agent-trace-detail">
        <div
          v-for="(iter, gi) in sectionsByIteration"
          :key="gi"
          class="agent-trace-turn"
        >
          <button
            type="button"
            class="agent-trace-turn-toggle"
            :aria-expanded="isIterationExpanded(gi)"
            :aria-controls="`trace-turn-${gi}`"
            :id="`trace-turn-btn-${gi}`"
            @click="toggleIteration(gi)"
          >
            <span class="agent-trace-turn-chevron" aria-hidden="true">
              {{ isIterationExpanded(gi) ? '▼' : '▶' }}
            </span>
            <span class="agent-trace-turn-summary">{{ iterationSummary(iter) }}</span>
          </button>
          <div
            v-show="isIterationExpanded(gi)"
            :id="`trace-turn-${gi}`"
            class="agent-trace-turn-content"
            role="region"
            :aria-labelledby="`trace-turn-btn-${gi}`"
          >
            <div class="agent-trace-sections">
              <template v-for="section in iter.sections" :key="section.sectionKey">
                <div class="agent-trace-section">
                  <button
                    type="button"
                    class="agent-trace-section-toggle"
                    :aria-expanded="openedSectionKey === section.sectionKey"
                    :aria-controls="`trace-section-${section.sectionKey}`"
                    :id="`trace-section-btn-${section.sectionKey}`"
                    @click="toggleSection(section.sectionKey)"
                  >
                    <span class="agent-trace-section-chevron" aria-hidden="true">
                      {{ openedSectionKey === section.sectionKey ? '▼' : '▶' }}
                    </span>
                    <span class="agent-trace-section-summary">
                      {{ sectionSummary(section) }}
                    </span>
                  </button>
                  <div
                    v-show="openedSectionKey === section.sectionKey"
                    :id="`trace-section-${section.sectionKey}`"
                    class="agent-trace-section-content"
                    role="region"
                    :aria-labelledby="`trace-section-btn-${section.sectionKey}`"
                  >
                    <ul class="agent-trace-list">
                      <li
                        v-for="(tc, i) in section.calls"
                        :key="`${section.sectionKey}-${i}`"
                        class="agent-trace-item"
                        :class="{ 'agent-trace-item--error': tc.success === false }"
                      >
                        <div class="agent-trace-item-header">
                          <span class="agent-trace-description">
                            {{ getToolCallDescription(tc) }}
                          </span>
                          <span
                            class="agent-trace-status"
                            :class="tc.success === false ? 'agent-trace-status--error' : 'agent-trace-status--ok'"
                            :title="tc.success === false ? 'Erreur' : 'OK'"
                          >
                            {{ tc.success === false ? 'Erreur' : 'OK' }}
                          </span>
                        </div>
                        <div class="agent-trace-item-tool-name" title="Tool name (debug)">
                          {{ tc.name }}
                        </div>
                        <div v-if="tc.arguments" class="agent-trace-field">
                          <span class="agent-trace-field-label">Arguments:</span>
                          <pre class="agent-trace-field-value">{{ truncate(tc.arguments, TRUNCATE_LEN) }}</pre>
                        </div>
                        <div class="agent-trace-field">
                          <span class="agent-trace-field-label">Résultat:</span>
                          <pre v-if="errorMessage(tc)" class="agent-trace-field-value agent-trace-field-value--error">{{ errorMessage(tc) }}</pre>
                          <pre v-else class="agent-trace-field-value">{{ truncate(tc.result, TRUNCATE_LEN) }}</pre>
                        </div>
                      </li>
                    </ul>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.agent-trace-block {
  margin-top: 0.35rem;
  padding: 0.25rem 0.35rem 0;
  font-size: 0.8125rem;
  color: #7a7a92;
  background: none;
  border: none;
  align-self: flex-start;
  max-width: 100%;
  width: 100%;
}

.agent-trace-thinking {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.agent-trace-thinking-text {
  font-style: italic;
  color: #6e6e86;
}

.agent-trace-reasoning-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  width: 100%;
  padding: 0.25rem 0;
  margin-top: 0.25rem;
  background: none;
  border: none;
  color: inherit;
  font-size: inherit;
  text-align: left;
  cursor: pointer;
}

.agent-trace-reasoning-toggle:hover {
  color: #8a8aa0;
}

.agent-trace-reasoning-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-trace-reasoning-chevron {
  flex-shrink: 0;
  font-size: 0.7rem;
}

.agent-trace-reasoning-content {
  margin-left: 0.5em;
  margin-top: 0.15rem;
  padding: 0.35rem 0;
  padding-left: 0.25rem;
  border-left: 1px solid rgba(120, 120, 140, 0.2);
  max-height: 14rem;
  overflow-y: auto;
}

.agent-trace-reasoning-text {
  margin: 0;
  font-size: 0.8125rem;
  color: #7a7a92;
  white-space: pre-wrap;
  word-break: break-word;
}

/** Cursor-style: one-line summary in white below the grey reasoning block (gist without expanding). */
.agent-trace-reasoning-summary {
  margin: 0.4rem 0 0 0.5em;
  font-size: 0.875rem;
  color: #e8e8f0;
  line-height: 1.35;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.agent-trace-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  width: 100%;
  padding: 0;
  background: none;
  border: none;
  color: inherit;
  font-size: inherit;
  text-align: left;
  cursor: pointer;
}

.agent-trace-toggle:hover {
  color: #8a8aa0;
}

.agent-trace-toggle-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-trace-toggle-icon {
  flex-shrink: 0;
  font-size: 0.7rem;
}

.agent-trace-detail {
  margin-top: 0.35rem;
  padding-top: 0.25rem;
  border-top: 1px solid rgba(120, 120, 140, 0.2);
  max-height: 14rem;
  overflow-y: auto;
  overflow-x: hidden;
}

.agent-trace-turn {
  margin-bottom: 0.35rem;
}

.agent-trace-turn:last-child {
  margin-bottom: 0;
}

.agent-trace-turn-toggle {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  width: 100%;
  padding: 0.25rem 0;
  background: none;
  border: none;
  color: inherit;
  font-size: inherit;
  text-align: left;
  cursor: pointer;
}

.agent-trace-turn-toggle:hover {
  color: #8a8aa0;
}

.agent-trace-turn-chevron {
  flex-shrink: 0;
  font-size: 0.65rem;
  width: 1em;
}

.agent-trace-turn-summary {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-trace-turn-content {
  margin-left: 0.5em;
  margin-top: 0.15rem;
  padding-left: 0.25rem;
  border-left: 1px solid rgba(120, 120, 140, 0.2);
}

.agent-trace-sections {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.agent-trace-section {
  margin: 0.15rem 0;
}

.agent-trace-section-toggle {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  width: 100%;
  padding: 0.25rem 0;
  background: none;
  border: none;
  color: inherit;
  font-size: inherit;
  text-align: left;
  cursor: pointer;
}

.agent-trace-section-toggle:hover {
  color: #8a8aa0;
}

.agent-trace-section-chevron {
  flex-shrink: 0;
  font-size: 0.65rem;
  width: 1em;
}

.agent-trace-section-summary {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-trace-section-content {
  margin-left: 0.5rem;
  padding-left: 0.25rem;
  border-left: 1px solid rgba(120, 120, 140, 0.2);
}

.agent-trace-list {
  margin: 0;
  padding-left: 0;
  list-style: none;
}

.agent-trace-item {
  margin: 0.5rem 0;
  padding: 0.4rem 0;
  border-bottom: 1px solid rgba(120, 120, 140, 0.15);
}

.agent-trace-item:last-child {
  border-bottom: none;
}

.agent-trace-item--error {
  border-left: 3px solid rgba(239, 68, 68, 0.8);
  padding-left: 0.5rem;
  margin-left: 0.25rem;
}

.agent-trace-item-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.agent-trace-description {
  flex: 1;
  min-width: 0;
  font-weight: 500;
}

.agent-trace-name {
  font-family: ui-monospace, monospace;
  font-weight: 600;
}

.agent-trace-item-tool-name {
  margin-top: 0.15rem;
  font-size: 0.7rem;
  font-family: ui-monospace, monospace;
  color: #6a6a7e;
}

.agent-trace-status {
  font-size: 0.7rem;
  padding: 0.15rem 0.4rem;
  border-radius: 4px;
}

.agent-trace-status--ok {
  background: rgba(34, 197, 94, 0.12);
  color: #6b9f7a;
}

.agent-trace-status--error {
  background: rgba(239, 68, 68, 0.12);
  color: #c97a7a;
}

.agent-trace-field {
  margin-top: 0.25rem;
  font-size: 0.75rem;
}

.agent-trace-field-label {
  color: #6a6a7e;
  margin-right: 0.35rem;
}

.agent-trace-field-value {
  margin: 0.2rem 0 0 0;
  padding: 0.25rem;
  background: rgba(0, 0, 0, 0.12);
  border-radius: 4px;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, monospace;
  font-size: 0.7rem;
  overflow-x: auto;
  color: #6e6e82;
}

.agent-trace-field-value--error {
  color: #c97a7a;
  border: 1px solid rgba(239, 68, 68, 0.2);
}
</style>
