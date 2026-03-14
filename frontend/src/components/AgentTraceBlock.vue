<script setup lang="ts">
import { ref, computed } from 'vue'
import type { AgentTrace, ToolCallDisplay } from '../types'

const TRUNCATE_LEN = 200

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

const toolCallNames = computed(() => {
  if (!props.agentTrace?.toolCalls?.length) return []
  return props.agentTrace.toolCalls.map((tc) => tc.name)
})

const summaryLabel = computed(() => {
  if (toolCallNames.value.length === 0) return ''
  if (toolCallNames.value.length === 1) return toolCallNames.value[0]
  return toolCallNames.value.join(', ')
})

const showTraceContent = computed(() => {
  return props.agentTrace?.toolCalls?.length && !props.isStreaming
})

/** Group tool calls by iteration index for display (Tour 1, Tour 2, …). */
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

function errorMessage(tc: ToolCallDisplay): string | null {
  return extractErrorFromResult(tc.result)
}
</script>

<template>
  <div
    v-if="isStreaming || showTraceContent"
    class="agent-trace-block"
    role="region"
    aria-label="Agent execution trace"
  >
    <!-- During reflection: minimal indicator -->
    <div v-if="isStreaming" class="agent-trace-thinking">
      <span class="agent-trace-thinking-text">Réflexion…</span>
    </div>
    <!-- After done: collapsible list of tools (Phase 1: names only) -->
    <template v-else-if="showTraceContent">
      <button
        type="button"
        class="agent-trace-toggle"
        :aria-expanded="!collapsed"
        @click="collapsed = !collapsed"
      >
        <span class="agent-trace-toggle-label">
          Outils utilisés : {{ summaryLabel }}
        </span>
        <span class="agent-trace-toggle-icon" aria-hidden="true">{{ collapsed ? '▼' : '▲' }}</span>
      </button>
      <div v-show="!collapsed" class="agent-trace-detail">
        <template v-for="(group, gi) in toolCallsByIteration" :key="gi">
          <div v-if="group.iterationLabel" class="agent-trace-iteration-label">
            {{ group.iterationLabel }}
          </div>
          <ul class="agent-trace-list">
            <li
              v-for="(tc, i) in group.calls"
              :key="`${gi}-${i}`"
              class="agent-trace-item"
              :class="{ 'agent-trace-item--error': tc.success === false }"
            >
              <div class="agent-trace-item-header">
                <span class="agent-trace-name">{{ tc.name }}</span>
                <span
                  class="agent-trace-status"
                  :class="tc.success === false ? 'agent-trace-status--error' : 'agent-trace-status--ok'"
                  :title="tc.success === false ? 'Erreur' : 'OK'"
                >
                  {{ tc.success === false ? 'Erreur' : 'OK' }}
                </span>
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
        </template>
      </div>
    </template>
  </div>
</template>


<style scoped>
.agent-trace-block {
  margin-top: 0.35rem;
  padding: 0.4rem 0.6rem;
  font-size: 0.8125rem;
  color: #a0a0b8;
  background: rgba(45, 38, 64, 0.6);
  border: 1px solid rgba(139, 92, 246, 0.2);
  border-radius: 10px;
  align-self: flex-start;
  max-width: 85%;
}

.agent-trace-thinking {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.agent-trace-thinking-text {
  font-style: italic;
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
  color: #c0c0d8;
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
  padding-top: 0.35rem;
  border-top: 1px solid rgba(139, 92, 246, 0.15);
}

.agent-trace-iteration-label {
  font-size: 0.7rem;
  font-weight: 600;
  color: rgba(139, 92, 246, 0.9);
  margin: 0.5rem 0 0.25rem;
}

.agent-trace-iteration-label:first-child {
  margin-top: 0;
}

.agent-trace-list {
  margin: 0;
  padding-left: 0;
  list-style: none;
}

.agent-trace-item {
  margin: 0.5rem 0;
  padding: 0.4rem 0;
  border-bottom: 1px solid rgba(139, 92, 246, 0.12);
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

.agent-trace-name {
  font-family: ui-monospace, monospace;
  font-weight: 600;
}

.agent-trace-status {
  font-size: 0.7rem;
  padding: 0.15rem 0.4rem;
  border-radius: 4px;
}

.agent-trace-status--ok {
  background: rgba(34, 197, 94, 0.2);
  color: #86efac;
}

.agent-trace-status--error {
  background: rgba(239, 68, 68, 0.2);
  color: #fca5a5;
}

.agent-trace-field {
  margin-top: 0.25rem;
  font-size: 0.75rem;
}

.agent-trace-field-label {
  color: #8888a0;
  margin-right: 0.35rem;
}

.agent-trace-field-value {
  margin: 0.2rem 0 0 0;
  padding: 0.25rem;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 4px;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, monospace;
  font-size: 0.7rem;
  overflow-x: auto;
}

.agent-trace-field-value--error {
  color: #fca5a5;
  border: 1px solid rgba(239, 68, 68, 0.3);
}
</style>
