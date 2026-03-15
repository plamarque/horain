<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import type { AgentTrace, ToolCallDisplay, ReasoningPhase } from '../types'
import { getToolCallDescriptionParts } from '../agentTraceDescriptions'

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

/** Per-phase collapse state (phase index -> collapsed). Default true. */
const phaseCollapsed = ref<Record<number, boolean>>({})
/** Which iteration (turn) is expanded when not streaming; null = all collapsed. During streaming, current turn is always expanded. */
const expandedIterationIndex = ref<number | null>(null)
/** Keys of tool calls whose detail (params, result, status) is expanded. Key = `${iterIndex}-${callIndex}`. */
const expandedCallKeys = ref<Record<string, boolean>>({})

/** Ref for the streaming reasoning content container (the one with overflow) so we can auto-scroll to bottom. */
const streamingReasoningContentRef = ref<HTMLElement | null>(null)

function scrollStreamingReasoningToBottom() {
  const el = streamingReasoningContentRef.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

/** Show minimal "Thinking..." only when streaming, no tool calls yet, and no reasoning text yet. */
const showThinkingOnly = computed(() => {
  const hasAny = typeof props.agentTrace?.reasoningText === 'string' && props.agentTrace.reasoningText.length > 0
  return (props.isStreaming ?? false) && !(props.agentTrace?.toolCalls?.length ?? 0) && !hasAny
})

/** Completed reasoning phases (one per LLM turn). When streaming and no phases yet, return [] so first chunk shows as streaming segment; otherwise fall back to single reasoningText for backwards compat. */
const reasoningPhases = computed((): ReasoningPhase[] => {
  const p = props.agentTrace?.reasoningPhases
  if (p?.length) return p
  const t = props.agentTrace?.reasoningText
  // While streaming with no phases yet, don't create one growing "phase" — show text in streaming segment only
  if ((props.isStreaming ?? false) && !p?.length) return []
  if (typeof t === 'string' && t.length > 0) {
    return [{ text: t, durationMs: props.agentTrace?.reasoningDurationMs }]
  }
  return []
})

/** Current phase still streaming (text not yet pushed to reasoningPhases). Shown for first phase and any subsequent one. */
const currentStreamingReasoning = computed(() => {
  const t = props.agentTrace?.reasoningText
  return typeof t === 'string' && t.length > 0 ? t : ''
})

/** When streaming reasoning text grows, scroll the content area to bottom so the user can keep reading. */
watch(
  () => props.agentTrace?.reasoningText ?? '',
  () => {
    nextTick(scrollStreamingReasoningToBottom)
  }
)

/** Duration label for a phase in seconds. */
function phaseDurationLabel(phase: ReasoningPhase): string {
  const ms = phase.durationMs
  if (ms == null || ms < 0) return '?'
  const sec = Math.round(ms / 1000)
  return sec <= 0 ? '1' : String(sec)
}

/** One-line summary for a phase: LLM-generated summary when present, else first sentence or ~120 chars. */
function phaseSummaryLine(phase: ReasoningPhase): string {
  if (phase.summary?.trim()) return phase.summary.trim()
  const t = phase.text?.trim() ?? ''
  if (!t) return ''
  const maxLen = 120
  const firstLine = t.split(/\n/)[0]?.trim() ?? t
  const firstSentence = firstLine.match(/^[^.!?]+[.!?]?/)?.[0]?.trim() ?? firstLine
  if (firstSentence.length <= maxLen) return firstSentence
  return firstSentence.slice(0, maxLen).trimEnd() + '…'
}

/** Interleaved segments: phase 0, turn 0, phase 1, turn 1, …, then current streaming phase if any. */
type TraceSegment =
  | { type: 'reasoning'; phase: ReasoningPhase; index: number }
  | { type: 'tools'; iteration: { iterationLabel: string | null; calls: ToolCallDisplay[] }; index: number }
  | { type: 'reasoningStreaming'; text: string }
const traceSegments = computed((): TraceSegment[] => {
  const out: TraceSegment[] = []
  const phases = reasoningPhases.value
  const iters = toolCallsByIteration.value
  const n = Math.max(phases.length, iters.length)
  for (let i = 0; i < n; i++) {
    if (phases[i]) out.push({ type: 'reasoning', phase: phases[i], index: i })
    if (iters[i]) out.push({ type: 'tools', iteration: iters[i], index: i })
  }
  const streaming = currentStreamingReasoning.value
  if (streaming) out.push({ type: 'reasoningStreaming', text: streaming })
  return out
})

function isPhaseCollapsed(index: number): boolean {
  return phaseCollapsed.value[index] !== false
}
function togglePhaseCollapsed(index: number) {
  phaseCollapsed.value = { ...phaseCollapsed.value, [index]: !isPhaseCollapsed(index) }
}

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

/** Index of the "current" turn during streaming (last iteration). */
const currentIterationIndex = computed(() => {
  if (!(props.isStreaming ?? false) || !toolCallsByIteration.value.length) return -1
  return toolCallsByIteration.value.length - 1
})

/** One-line summary for a turn (flat list: "N appels"). */
function iterationSummary(iter: { calls: ToolCallDisplay[] }): string {
  const n = iter.calls.length
  return n === 1 ? '1 appel' : `${n} appels`
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
    if (wasStreaming === true && streaming === false && toolCallsByIteration.value.length > 0) {
      expandedIterationIndex.value = toolCallsByIteration.value.length - 1
    }
  }
)

function errorMessage(tc: ToolCallDisplay): string | null {
  return extractErrorFromResult(tc.result)
}

function callDetailKey(iterIndex: number, callIndex: number): string {
  return `${iterIndex}-${callIndex}`
}

function isCallExpanded(iterIndex: number, callIndex: number): boolean {
  return !!expandedCallKeys.value[callDetailKey(iterIndex, callIndex)]
}

function toggleCallExpanded(iterIndex: number, callIndex: number) {
  const key = callDetailKey(iterIndex, callIndex)
  expandedCallKeys.value = { ...expandedCallKeys.value, [key]: !expandedCallKeys.value[key] }
}
</script>

<template>
  <div
    v-if="agentTrace?.modelName || showThinkingOnly || traceSegments.length > 0"
    class="agent-trace-block"
    role="region"
    aria-label="Agent execution trace"
  >
    <!-- Model name (when known): displayed at top of trace -->
    <p v-if="agentTrace?.modelName" class="agent-trace-model">
      Modèle : <span class="agent-trace-model-name">{{ agentTrace.modelName }}</span>
    </p>
    <!-- Streaming and no tool calls yet: minimal indicator -->
    <div v-if="showThinkingOnly" class="agent-trace-thinking">
      <span class="agent-trace-thinking-text">Réflexion...</span>
    </div>
    <!-- Interleaved: one reasoning phase box per LLM turn, then tool calls for that turn -->
    <template v-for="(seg, si) in traceSegments" :key="seg.type === 'reasoningStreaming' ? 'reasoning-streaming' : `${seg.type}-${seg.index}`">
      <!-- Completed reasoning phase (collapsible "Thought for Xs") -->
      <template v-if="seg.type === 'reasoning'">
        <button
          type="button"
          class="agent-trace-reasoning-toggle"
          :aria-expanded="!isPhaseCollapsed(seg.index)"
          @click="togglePhaseCollapsed(seg.index)"
        >
          <span class="agent-trace-reasoning-label">
            <span class="agent-trace-activity-label">Réflexion </span>
            <span class="agent-trace-qualifier">{{ phaseDurationLabel(seg.phase) }}s</span>
          </span>
          <span class="agent-trace-reasoning-chevron agent-trace-chevron-hover" aria-hidden="true">
            {{ isPhaseCollapsed(seg.index) ? '▼' : '▲' }}
          </span>
        </button>
        <div v-show="!isPhaseCollapsed(seg.index)" class="agent-trace-reasoning-content">
          <p class="agent-trace-reasoning-text">{{ seg.phase.text }}</p>
        </div>
        <p v-if="phaseSummaryLine(seg.phase)" class="agent-trace-reasoning-summary">{{ phaseSummaryLine(seg.phase) }}</p>
      </template>
      <!-- Current phase streaming ("Thinking..." + content open) -->
      <template v-else-if="seg.type === 'reasoningStreaming'">
        <div class="agent-trace-reasoning-header agent-trace-reasoning-header--streaming">
          <span class="agent-trace-activity-label">Réflexion...</span>
        </div>
        <div
          :ref="(el) => { if (seg.type === 'reasoningStreaming') streamingReasoningContentRef = el as HTMLElement | null }"
          class="agent-trace-reasoning-content"
        >
          <p class="agent-trace-reasoning-text">{{ seg.text }}</p>
        </div>
      </template>
      <!-- Tool calls for this turn -->
      <template v-else-if="seg.type === 'tools'">
        <div class="agent-trace-detail">
          <template v-for="iter in [seg.iteration]" :key="si + '-turn'">
          <!-- Single call: no "1 appel" header, just the one row -->
          <div v-if="iter.calls.length === 1" class="agent-trace-turn agent-trace-turn--single">
            <ul class="agent-trace-list">
              <li
                class="agent-trace-item"
                :class="{ 'agent-trace-item--error': iter.calls[0].success === false }"
              >
                <button
                  type="button"
                  class="agent-trace-item-row"
                  :aria-expanded="isCallExpanded(seg.index, 0)"
                  :aria-controls="`trace-call-${seg.index}-0`"
                  @click="toggleCallExpanded(seg.index, 0)"
                >
                  <span class="agent-trace-description">
                    <span class="agent-trace-desc-name">{{ getToolCallDescriptionParts(iter.calls[0]).base }}</span><span v-if="getToolCallDescriptionParts(iter.calls[0]).qualification" class="agent-trace-desc-qualification">{{ getToolCallDescriptionParts(iter.calls[0]).qualification }}</span>
                  </span>
                  <span
                    v-if="iter.calls[0].success === false"
                    class="agent-trace-status agent-trace-status--error"
                    title="Erreur"
                  >
                    Erreur
                  </span>
                  <span class="agent-trace-item-chevron agent-trace-chevron-hover" aria-hidden="true">
                    {{ isCallExpanded(seg.index, 0) ? '▼' : '▶' }}
                  </span>
                </button>
                <div
                  v-show="isCallExpanded(seg.index, 0)"
                  :id="`trace-call-${seg.index}-0`"
                  class="agent-trace-item-detail"
                >
                  <div class="agent-trace-item-tool-name" title="Tool name (debug)">
                    {{ iter.calls[0].name }}
                  </div>
                  <div v-if="iter.calls[0].arguments" class="agent-trace-field">
                    <span class="agent-trace-field-label">Arguments:</span>
                    <pre class="agent-trace-field-value">{{ truncate(iter.calls[0].arguments, TRUNCATE_LEN) }}</pre>
                  </div>
                  <div class="agent-trace-field">
                    <span class="agent-trace-field-label">Résultat:</span>
                    <pre v-if="errorMessage(iter.calls[0])" class="agent-trace-field-value agent-trace-field-value--error">{{ errorMessage(iter.calls[0]) }}</pre>
                    <pre v-else class="agent-trace-field-value">{{ truncate(iter.calls[0].result, TRUNCATE_LEN) }}</pre>
                  </div>
                  <div
                    v-if="iter.calls[0].success !== false"
                    class="agent-trace-status agent-trace-status--ok"
                    title="OK"
                  >
                    OK
                  </div>
                </div>
              </li>
            </ul>
          </div>
          <!-- Multiple calls: turn with "N appels" and collapsible list -->
          <div v-else class="agent-trace-turn">
            <button
              type="button"
              class="agent-trace-turn-toggle"
              :aria-expanded="isIterationExpanded(seg.index)"
              :aria-controls="`trace-turn-${seg.index}`"
              :id="`trace-turn-btn-${seg.index}`"
              @click="toggleIteration(seg.index)"
            >
              <span class="agent-trace-turn-chevron agent-trace-chevron-hover" aria-hidden="true">
                {{ isIterationExpanded(seg.index) ? '▼' : '▶' }}
              </span>
              <span class="agent-trace-turn-summary">{{ iterationSummary(iter) }}</span>
            </button>
            <div
              v-show="isIterationExpanded(seg.index)"
              :id="`trace-turn-${seg.index}`"
              class="agent-trace-turn-content"
              role="region"
              :aria-labelledby="`trace-turn-btn-${seg.index}`"
            >
              <ul class="agent-trace-list">
                <li
                  v-for="(tc, i) in iter.calls"
                  :key="`${seg.index}-${i}`"
                  class="agent-trace-item"
                  :class="{ 'agent-trace-item--error': tc.success === false }"
                >
                  <button
                    type="button"
                    class="agent-trace-item-row"
                    :aria-expanded="isCallExpanded(seg.index, i)"
                    :aria-controls="`trace-call-${seg.index}-${i}`"
                    @click="toggleCallExpanded(seg.index, i)"
                  >
                    <span class="agent-trace-description">
                    <span class="agent-trace-desc-name">{{ getToolCallDescriptionParts(tc).base }}</span><span v-if="getToolCallDescriptionParts(tc).qualification" class="agent-trace-desc-qualification">{{ getToolCallDescriptionParts(tc).qualification }}</span>
                  </span>
                    <span
                      v-if="tc.success === false"
                      class="agent-trace-status agent-trace-status--error"
                      title="Erreur"
                    >
                      Erreur
                    </span>
                    <span class="agent-trace-item-chevron agent-trace-chevron-hover" aria-hidden="true">
                      {{ isCallExpanded(seg.index, i) ? '▼' : '▶' }}
                    </span>
                  </button>
                  <div
                    v-show="isCallExpanded(seg.index, i)"
                    :id="`trace-call-${seg.index}-${i}`"
                    class="agent-trace-item-detail"
                  >
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
                    <div
                      v-if="tc.success !== false"
                      class="agent-trace-status agent-trace-status--ok"
                      title="OK"
                    >
                      OK
                    </div>
                  </div>
                </li>
              </ul>
            </div>
          </div>
        </template>
      </div>
    </template>
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

.agent-trace-model {
  margin: 0 0 0.25rem 0;
  font-size: 0.7rem;
  color: #6a6a7e;
}

.agent-trace-model-name {
  font-family: ui-monospace, monospace;
  color: #8a8aa0;
}

.agent-trace-thinking {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.agent-trace-thinking-text {
  font-style: italic;
  color: #6e6e86;
  animation: agent-trace-thinking-glow 1.8s ease-in-out infinite;
}

/** Header for streaming reasoning (non-clickable "Thinking..."). */
.agent-trace-reasoning-header {
  margin-top: 0.08rem;
  padding: 0.08rem 0;
}

.agent-trace-reasoning-header--streaming .agent-trace-activity-label {
  color: #9ca3c2;
  animation: agent-trace-thinking-glow 1.8s ease-in-out infinite;
}

@keyframes agent-trace-thinking-glow {
  0%,
  100% {
    opacity: 0.85;
    text-shadow: 0 0 0 transparent;
  }
  50% {
    opacity: 1;
    text-shadow: 0 0 8px rgba(156, 163, 194, 0.5);
  }
}

.agent-trace-reasoning-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  width: 100%;
  padding: 0.08rem 0;
  margin-top: 0.08rem;
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

/** Cursor-style: activity name in distinct color (Thought, Exploration, Lecture…). */
.agent-trace-activity-label {
  color: #9ca3c2;
}

/** Qualifier (e.g. " for 11s", " · 2 outils") in muted grey. */
.agent-trace-qualifier {
  color: #7a7a92;
}

/** Chevron visible only on hover (Cursor-style). */
.agent-trace-chevron-hover {
  opacity: 0;
  transition: opacity 0.15s ease;
}

.agent-trace-reasoning-toggle:hover .agent-trace-chevron-hover,
.agent-trace-turn-toggle:hover .agent-trace-chevron-hover,
.agent-trace-section-toggle:hover .agent-trace-chevron-hover,
.agent-trace-item-row:hover .agent-trace-chevron-hover {
  opacity: 1;
}

.agent-trace-reasoning-chevron {
  flex-shrink: 0;
  font-size: 0.7rem;
}

.agent-trace-reasoning-content {
  margin-left: 0.5em;
  margin-top: 0.1rem;
  padding: 0.2rem 0;
  padding-left: 0.25rem;
  max-height: 9.8rem;
  overflow-y: auto;
}

.agent-trace-reasoning-text {
  margin: 0;
  font-size: 0.8125rem;
  color: #7a7a92;
  white-space: pre-wrap;
  word-break: break-word;
}

/** Cursor-style: short summary in white below the grey reasoning block (up to 2 lines, then ellipsis). */
.agent-trace-reasoning-summary {
  margin: 0.25rem 0 0 0.5em;
  font-size: 0.875rem;
  color: #e8e8f0;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  text-overflow: ellipsis;
  word-break: break-word;
}

.agent-trace-detail {
  margin-top: 0.04rem;
  padding-top: 0;
  max-height: 14rem;
  overflow-y: auto;
  overflow-x: hidden;
}

.agent-trace-turn {
  margin-bottom: 0.02rem;
}

.agent-trace-turn:last-child {
  margin-bottom: 0;
}

.agent-trace-turn-toggle {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  width: 100%;
  padding: 0.04rem 0;
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
  margin-top: 0.04rem;
  padding-left: 0.25rem;
}

.agent-trace-list {
  margin: 0;
  padding-left: 0;
  list-style: none;
}

.agent-trace-item {
  margin: 0.08rem 0;
  padding: 0;
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

/** Clickable row: natural-language description only; click expands to show params/result/status. */
.agent-trace-item-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  padding: 0.08rem 0;
  background: none;
  border: none;
  color: inherit;
  font-size: inherit;
  text-align: left;
  cursor: pointer;
}

.agent-trace-item-row:hover {
  color: #8a8aa0;
}

.agent-trace-item-row .agent-trace-description {
  flex: 1;
  min-width: 0;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-trace-item-chevron {
  flex-shrink: 0;
  font-size: 0.65rem;
  width: 1em;
}

.agent-trace-item-detail {
  margin-left: 0.5rem;
  padding: 0.15rem 0 0.25rem 0;
  padding-left: 0.25rem;
  border-left: 1px solid rgba(120, 120, 140, 0.2);
}

.agent-trace-description {
  min-width: 0;
  font-weight: 500;
}

/** Operation name (e.g. "Chargement des temps sur la période") in primary trace color. */
.agent-trace-desc-name {
  color: #9ca3c2;
}

/** Qualification (e.g. "Janvier 2025 – Janvier 2026") same hue, attenuated. */
.agent-trace-desc-qualification {
  color: #6e6e86;
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

.agent-trace-item-detail .agent-trace-status {
  margin-top: 0.25rem;
  display: inline-block;
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
