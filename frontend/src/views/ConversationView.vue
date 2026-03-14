<script setup lang="ts">
import type { Ref } from 'vue'
import { ref, computed, watch, nextTick, onMounted, onUnmounted, inject } from 'vue'
import PushToTalkButton from '../components/PushToTalkButton.vue'
import ConversationTimeline from '../components/ConversationTimeline.vue'
import EntryEditModal from '../components/EntryEditModal.vue'
import { sendChatMessage, sendChatMessageStream } from '../services/chatClient'
import { resetDevSeed, getRecentTimeLogs } from '../services/apiClient'
import type { ProjectDto } from '../services/apiClient'
import type { AgentTrace, AssistantMessageSegment, ChartSpec, Message, TimeLogEntry } from '../types'

const openProjectEdit = inject<((projectId: string) => void)>('openProjectEdit')
const versionDisplay = inject<string>('versionDisplay', '')
const refreshApp = inject<() => void>('refreshApp', () => {})
const selectedProjects = inject<Ref<ProjectDto[]>>('selectedProjects', ref([]))
const removeProjectFromContext = inject<(projectId: string) => void>('removeProjectFromContext', () => {})
const clearSelectedProjectsAfterSend = inject<() => void>('clearSelectedProjectsAfterSend', () => {})

const MAX_CONTEXT_ENTRIES = 5
const MAX_AUTO_CONTEXT_ENTRIES = 10

function isValidChartSpec(v: unknown): v is ChartSpec {
  if (!v || typeof v !== 'object') return false
  const o = v as Record<string, unknown>
  return (
    ['stackedBar', 'pie', 'bar'].includes(String(o.type ?? '')) &&
    typeof o.title === 'string' &&
    Array.isArray(o.categories) &&
    Array.isArray(o.series) &&
    o.series.every(
      (s: unknown) =>
        s && typeof s === 'object' && typeof (s as { name?: unknown }).name === 'string' && Array.isArray((s as { data?: unknown }).data)
    )
  )
}

function isValidTimeLogEntries(v: unknown): v is TimeLogEntry[] {
  if (!Array.isArray(v)) return false
  return v.every(
    (e) =>
      e &&
      typeof e === 'object' &&
      typeof (e as { durationMinutes?: unknown }).durationMinutes === 'number' &&
      typeof (e as { loggedAt?: unknown }).loggedAt === 'string'
  )
}

/** Detect if a tool result indicates an error (backend convention: {"error": "..."}). */
function isToolResultError(result: string): boolean {
  if (!result || typeof result !== 'string') return false
  const trimmed = result.trim()
  if (trimmed.startsWith('{"error":')) return true
  try {
    const obj = JSON.parse(trimmed) as Record<string, unknown>
    return typeof obj?.error === 'string'
  } catch {
    return false
  }
}

/** Build agent trace from backend toolCalls payload (done event or non-streaming response). */
function buildAgentTrace(
  toolCalls: Array<{ name: string; arguments: string; result: string; iterationIndex?: number }> | undefined
): AgentTrace | undefined {
  if (!toolCalls?.length) return undefined
  return {
    toolCalls: toolCalls.map((tc) => {
      const result = tc.result ?? ''
      return {
        name: tc.name,
        arguments: tc.arguments ?? '',
        result,
        success: !isToolResultError(result),
        ...(typeof tc.iterationIndex === 'number' && { iterationIndex: tc.iterationIndex }),
      }
    }),
  }
}

const messages = ref<Message[]>([])
const isProcessing = ref(false)
const streamingMessageId = ref<string | null>(null)
/** Segments for interleaved display (text then tools per turn); reset when starting a new stream. */
const streamingSegments = ref<AssistantMessageSegment[]>([])
const abortControllerRef = ref<AbortController | null>(null)

/** True when the last assistant message is streaming; used so timeline hides "Processing..." as soon as the streaming bubble exists. */
const hasStreamingBubble = computed(() => messages.value.some((m) => m.isStreaming === true))
const inputRef = ref<InstanceType<typeof PushToTalkButton> | null>(null)
const timelineRef = ref<InstanceType<typeof ConversationTimeline> | null>(null)
const hasNewMessageBelow = ref(false)
const selectedEntries = ref<TimeLogEntry[]>([])
const editingEntry = ref<TimeLogEntry | null>(null)
const recentLogs = ref<TimeLogEntry[]>([])
const isRefreshing = ref(false)

/** Desktop: refocus input when assistant finishes so user can type without clicking. Mobile: no refocus (keyboard would reopen). */
const isDesktop = ref(false)
const DESKTOP_MEDIA = '(hover: hover)'
let mediaQuery: MediaQueryList | null = null
let mediaListener: ((e: MediaQueryListEvent) => void) | null = null

function refetchRecentLogs() {
  return getRecentTimeLogs(8)
    .then((logs) => { recentLogs.value = logs })
    .catch(() => { /* keep current recentLogs */ })
}

async function handlePullRefresh() {
  if (isRefreshing.value) return
  isRefreshing.value = true
  try {
    await refetchRecentLogs()
  } finally {
    isRefreshing.value = false
  }
}

function onProjectSaved() {
  refetchRecentLogs()
}

onMounted(async () => {
  mediaQuery = window.matchMedia(DESKTOP_MEDIA)
  isDesktop.value = mediaQuery.matches
  mediaListener = (e: MediaQueryListEvent) => { isDesktop.value = e.matches }
  mediaQuery.addEventListener('change', mediaListener)

  window.addEventListener('horain:projectSaved', onProjectSaved)

  if (messages.value.length > 0) return
  try {
    const logs = await getRecentTimeLogs(8)
    recentLogs.value = logs
  } catch {
    recentLogs.value = []
  }
})
onUnmounted(() => {
  if (mediaQuery && mediaListener) mediaQuery.removeEventListener('change', mediaListener)
  window.removeEventListener('horain:projectSaved', onProjectSaved)
})

watch(isProcessing, async (now, was) => {
  if (was === true && now === false && isDesktop.value) {
    await nextTick()
    inputRef.value?.focusInput()
  }
})

function handlePermissionError(message: string) {
  addAssistantMessage(message)
}

function handleSelectEntry(entry: TimeLogEntry) {
  if (!entry.id) return
  const exists = selectedEntries.value.some((e) => e.id === entry.id)
  if (!exists && selectedEntries.value.length < MAX_CONTEXT_ENTRIES) {
    selectedEntries.value = [...selectedEntries.value, entry]
  }
}

function handleEditEntry(entry: TimeLogEntry) {
  if (entry.id) editingEntry.value = entry
}

function handleEditProject(entry: TimeLogEntry) {
  if (entry.projectId && openProjectEdit) openProjectEdit(entry.projectId)
}

function handleRemoveFromContext(entry: TimeLogEntry) {
  selectedEntries.value = selectedEntries.value.filter((e) => e.id !== entry.id)
}

function handleEditModalClose() {
  editingEntry.value = null
}

async function handleEditSaved(patch?: Partial<TimeLogEntry> & { id: string }) {
  if (patch?.id) {
    const idx = recentLogs.value.findIndex((e) => e.id === patch.id)
    if (idx >= 0) {
      const next = [...recentLogs.value]
      next[idx] = { ...next[idx], ...patch } as TimeLogEntry
      recentLogs.value = next
    }
    messages.value = messages.value.map((m) => {
      if (!m.timeLogs?.length) return m
      const updated = m.timeLogs.some((e) => e.id === patch.id)
      if (!updated) return m
      return {
        ...m,
        timeLogs: m.timeLogs.map((e) => (e.id === patch.id ? { ...e, ...patch } as TimeLogEntry : e)),
      }
    })
  }
  editingEntry.value = null
  if (!patch?.id) {
    try {
      const logs = await getRecentTimeLogs(8)
      recentLogs.value = logs
    } catch {
      // keep current recentLogs
    }
  }
}

async function handleEntryDeleted(deletedEntry: TimeLogEntry) {
  const deletedId = deletedEntry?.id
  editingEntry.value = null
  if (deletedId) {
    selectedEntries.value = selectedEntries.value.filter((e) => e.id !== deletedId)
    messages.value = messages.value.map((m) => {
      if (!m.timeLogs?.length) return m
      const filtered = m.timeLogs.filter((e) => e.id !== deletedId)
      return filtered.length > 0 ? { ...m, timeLogs: filtered } : { ...m, timeLogs: undefined }
    })
  }
  try {
    const logs = await getRecentTimeLogs(8)
    recentLogs.value = logs
  } catch {
    recentLogs.value = []
  }
}

function formatEntryChipLabel(entry: TimeLogEntry): string {
  const p = entry.projectName || '?'
  const mins = entry.durationMinutes
  const d =
    mins < 60
      ? `${mins} min`
      : `${Math.floor(mins / 60)}h${mins % 60 ? ` ${mins % 60}min` : ''}`
  const date = entry.loggedAt
    ? new Date(entry.loggedAt).toLocaleDateString(undefined, {
        month: 'short',
        day: 'numeric',
      })
    : ''
  return `${p} · ${d} · ${date}`
}

function addAssistantMessage(
  text: string,
  chart?: ChartSpec,
  timeLogs?: TimeLogEntry[],
  turnId?: string | null,
  agentTrace?: AgentTrace
) {
  const wasAtBottom = timelineRef.value?.isUserAtBottom() ?? true
  messages.value.push({
    id: crypto.randomUUID(),
    role: 'assistant',
    text,
    timestamp: new Date(),
    ...(chart && { chart }),
    ...(timeLogs?.length && { timeLogs }),
    ...(turnId != null && { turnId }),
    ...(agentTrace && { agentTrace }),
  })
  nextTick(() => {
    const timeline = timelineRef.value
    if (!timeline) return
    if (wasAtBottom) {
      timeline.scrollToBottom()
    } else {
      hasNewMessageBelow.value = true
    }
  })
}

function handleIndicatorClicked() {
  hasNewMessageBelow.value = false
}

async function handleSubmit(text: string) {
  if (!text.trim()) return
  messages.value.push({
    id: crypto.randomUUID(),
    role: 'user',
    text: text.trim(),
    timestamp: new Date(),
  })

  let contextToSend = selectedEntries.value
  if (contextToSend.length === 0) {
    // Auto-inject timeLogs from last assistant message when user hasn't selected entries
    for (let i = messages.value.length - 1; i >= 0; i--) {
      const m = messages.value[i]
      if (m.role === 'assistant' && m.timeLogs?.length) {
        const withIds = m.timeLogs.filter((e): e is TimeLogEntry & { id: string } => !!e.id)
        contextToSend = withIds.slice(0, MAX_AUTO_CONTEXT_ENTRIES)
        break
      }
    }
  }
  const contextProjectsToSend = selectedProjects.value.map((p) => ({
    id: p.id,
    name: p.name,
    description: p.description,
  }))
  selectedEntries.value = []
  clearSelectedProjectsAfterSend()

  isProcessing.value = true
  streamingMessageId.value = null
  abortControllerRef.value = new AbortController()
  const history = messages.value
    .slice(0, -1)
    .map((m) => ({ role: m.role, text: m.text }))

  try {
    await sendChatMessageStream(
      text.trim(),
      {
        onChunk(chunk) {
          if (!streamingMessageId.value) {
            streamingSegments.value = []
            const id = crypto.randomUUID()
            streamingMessageId.value = id
            const wasAtBottom = timelineRef.value?.isUserAtBottom() ?? true
            messages.value.push({
              id,
              role: 'assistant',
              text: chunk,
              timestamp: new Date(),
              isStreaming: true,
              agentTrace: { toolCalls: [] },
            })
            nextTick(() => {
              if (wasAtBottom) timelineRef.value?.scrollToBottom()
              else hasNewMessageBelow.value = true
            })
          } else {
            const msg = messages.value.find((m) => m.id === streamingMessageId.value)
            if (msg) msg.text += chunk
          }
        },
        onReasoningChunk(reasoningDelta) {
          let id = streamingMessageId.value
          if (!id) {
            streamingSegments.value = []
            id = crypto.randomUUID()
            streamingMessageId.value = id
            const wasAtBottom = timelineRef.value?.isUserAtBottom() ?? true
            messages.value.push({
              id,
              role: 'assistant',
              text: '',
              timestamp: new Date(),
              isStreaming: true,
              agentTrace: { toolCalls: [], reasoningText: reasoningDelta },
            })
            nextTick(() => {
              if (wasAtBottom) timelineRef.value?.scrollToBottom()
              else hasNewMessageBelow.value = true
            })
            return
          }
          const msg = messages.value.find((m) => m.id === id)
          if (msg?.agentTrace) {
            msg.agentTrace.reasoningText = (msg.agentTrace.reasoningText ?? '') + reasoningDelta
          }
        },
        onAssistantSegment(text) {
          streamingSegments.value.push({ type: 'text', text })
        },
        onToolCall(call) {
          const display = {
            name: call.name,
            arguments: call.arguments,
            result: call.result,
            success: !isToolResultError(call.result ?? ''),
            ...(typeof call.iterationIndex === 'number' && { iterationIndex: call.iterationIndex }),
          }
          let id = streamingMessageId.value
          if (!id) {
            streamingSegments.value = []
            id = crypto.randomUUID()
            streamingMessageId.value = id
            const wasAtBottom = timelineRef.value?.isUserAtBottom() ?? true
            messages.value.push({
              id,
              role: 'assistant',
              text: '',
              timestamp: new Date(),
              isStreaming: true,
              agentTrace: { toolCalls: [display] },
            })
            streamingSegments.value.push({
              type: 'tools',
              iterationIndex: typeof call.iterationIndex === 'number' ? call.iterationIndex : 0,
              toolCalls: [display],
            })
            nextTick(() => {
              if (wasAtBottom) timelineRef.value?.scrollToBottom()
              else hasNewMessageBelow.value = true
            })
            return
          }
          const msg = messages.value.find((m) => m.id === id)
          if (msg?.agentTrace) {
            msg.agentTrace.toolCalls.push(display)
          }
          const iter = typeof call.iterationIndex === 'number' ? call.iterationIndex : 0
          const segs = streamingSegments.value
          const last = segs[segs.length - 1]
          if (last?.type === 'tools' && last.iterationIndex === iter) {
            last.toolCalls.push(display)
          } else {
            streamingSegments.value.push({ type: 'tools', iterationIndex: iter, toolCalls: [display] })
          }
        },
        onDone(payload) {
          const rawChart = payload.data && typeof payload.data === 'object' && 'chart' in payload.data
            ? (payload.data as { chart: unknown }).chart
            : undefined
          const chart = isValidChartSpec(rawChart) ? rawChart : undefined
          const rawTimeLogs = payload.data && typeof payload.data === 'object' && 'timeLogs' in payload.data
            ? (payload.data as { timeLogs: unknown }).timeLogs
            : undefined
          const timeLogs = isValidTimeLogEntries(rawTimeLogs) ? rawTimeLogs : undefined
          const agentTrace = buildAgentTrace(payload.toolCalls)
          if (streamingMessageId.value) {
            const msg = messages.value.find((m) => m.id === streamingMessageId.value)
            if (msg) {
              // Keep streamed text (intermediate turns); use payload only when no chunks were received (e.g. non-streaming)
              const streamedText = msg.text != null && msg.text.length > 0
              msg.text = streamedText ? msg.text : (payload.assistantMessage ?? '')
              msg.isStreaming = false
              if (chart) msg.chart = chart
              if (timeLogs?.length) msg.timeLogs = timeLogs
              if (payload.turnId != null) msg.turnId = payload.turnId
              msg.agentTrace = agentTrace ?? msg.agentTrace ?? { toolCalls: [] }
              if (payload.reasoningText != null) msg.agentTrace.reasoningText = payload.reasoningText
              if (payload.reasoningDurationMs != null) msg.agentTrace.reasoningDurationMs = payload.reasoningDurationMs
              if (payload.reasoningSummary != null) msg.agentTrace.reasoningSummary = payload.reasoningSummary
              if (streamingSegments.value.length > 0) {
                msg.segments = [...streamingSegments.value]
              }
            }
          } else {
            const mergedTrace: AgentTrace | undefined = agentTrace
              ? { ...agentTrace, reasoningText: payload.reasoningText, reasoningDurationMs: payload.reasoningDurationMs, reasoningSummary: payload.reasoningSummary }
              : (payload.reasoningText != null || payload.reasoningDurationMs != null || payload.reasoningSummary != null)
                ? { toolCalls: [], reasoningText: payload.reasoningText, reasoningDurationMs: payload.reasoningDurationMs, reasoningSummary: payload.reasoningSummary }
                : undefined
            addAssistantMessage(payload.assistantMessage ?? '', chart, timeLogs, payload.turnId, mergedTrace)
          }
          streamingMessageId.value = null
          streamingSegments.value = []
          getRecentTimeLogs(8).then((logs) => { recentLogs.value = logs }).catch(() => {})
        },
        onError(err) {
          if ((err as Error).name === 'AbortError') return
          const msg = (err as Error).message
          const fallback = 'Unable to reach the assistant. Check that the backend is running and LLM_API_KEY is configured.'
          addAssistantMessage(msg?.startsWith('API error') ? msg : fallback)
        },
      },
      history,
      contextToSend,
      contextProjectsToSend,
      { signal: abortControllerRef.value?.signal }
    )
  } catch (err) {
    if ((err as Error).name === 'AbortError') return
    const status = (err as Error & { status?: number }).status
    if (status === 404 || status === 405) {
      try {
        const response = await sendChatMessage(text.trim(), history, contextToSend, contextProjectsToSend, {
          signal: abortControllerRef.value?.signal,
        })
        const rawChart = response.data && typeof response.data === 'object' && 'chart' in response.data
          ? (response.data as { chart: unknown }).chart
          : undefined
        const chart = isValidChartSpec(rawChart) ? rawChart : undefined
        const rawTimeLogs = response.data && typeof response.data === 'object' && 'timeLogs' in response.data
          ? (response.data as { timeLogs: unknown }).timeLogs
          : undefined
        const timeLogs = isValidTimeLogEntries(rawTimeLogs) ? rawTimeLogs : undefined
        let agentTrace = buildAgentTrace(response.toolCalls)
        if (response.reasoningText != null || response.reasoningDurationMs != null || response.reasoningSummary != null) {
          agentTrace = agentTrace ?? { toolCalls: [] }
          if (response.reasoningText != null) agentTrace.reasoningText = response.reasoningText
          if (response.reasoningDurationMs != null) agentTrace.reasoningDurationMs = response.reasoningDurationMs
          if (response.reasoningSummary != null) agentTrace.reasoningSummary = response.reasoningSummary
        }
        addAssistantMessage(response.assistantMessage, chart, timeLogs, response.turnId, agentTrace)
        getRecentTimeLogs(8).then((logs) => { recentLogs.value = logs }).catch(() => {})
      } catch (fallbackErr) {
        if ((fallbackErr as Error).name === 'AbortError') return
        const msg = (fallbackErr as Error).message
        const fallback = 'Unable to reach the assistant. Check that the backend is running and LLM_API_KEY is configured.'
        addAssistantMessage(msg?.startsWith('API error') ? msg : fallback)
      }
    } else {
      const msg = (err as Error).message
      const fallback = 'Unable to reach the assistant. Check that the backend is running and LLM_API_KEY is configured.'
      addAssistantMessage(msg?.startsWith('API error') ? msg : fallback)
    }
  } finally {
    isProcessing.value = false
    abortControllerRef.value = null
    streamingMessageId.value = null
  }
}

function handleStop() {
  abortControllerRef.value?.abort()
}

const isDev = import.meta.env.DEV
const isSeeding = ref(false)

async function handleResetSeed() {
  if (!isDev) return
  isSeeding.value = true
  try {
    await resetDevSeed()
    const logs = await getRecentTimeLogs(8)
    recentLogs.value = logs
    // Clear conversation so the default view shows "Dernières activités" like on app launch
    messages.value = []
  } catch {
    addAssistantMessage('Seed reset failed. Is the backend running with dev seed enabled?')
  } finally {
    isSeeding.value = false
  }
}
</script>

<template>
  <div class="conversation-view">
    <ConversationTimeline
      ref="timelineRef"
      :messages="messages"
      :recent-logs="recentLogs"
      :is-processing="isProcessing"
      :has-streaming-bubble="hasStreamingBubble"
      :has-new-message-below="hasNewMessageBelow"
      :refreshing="isRefreshing"
      @select-entry="handleSelectEntry"
      @edit-entry="handleEditEntry"
      @edit-project="handleEditProject"
      @indicator-clicked="handleIndicatorClicked"
      @refresh="handlePullRefresh"
    />
    <div class="input-area">
      <div class="input-col">
        <div v-if="selectedEntries.length || selectedProjects.length" class="context-chips">
          <span
            v-for="proj in selectedProjects"
            :key="'proj-' + proj.id"
            class="context-chip context-chip--project"
          >
            {{ proj.name }}
            <button
              type="button"
              class="context-chip-remove"
              aria-label="Remove project from context"
              @click="removeProjectFromContext(proj.id)"
            >
              ×
            </button>
          </span>
          <span
            v-for="entry in selectedEntries"
            :key="entry.id"
            class="context-chip"
          >
            {{ formatEntryChipLabel(entry) }}
            <button
              type="button"
              class="context-chip-remove"
              aria-label="Remove from context"
              @click="handleRemoveFromContext(entry)"
            >
              ×
            </button>
          </span>
        </div>
        <PushToTalkButton
          ref="inputRef"
          :disabled="isProcessing"
          :processing="isProcessing"
          @submit="handleSubmit"
          @stop="handleStop"
          @permission-error="handlePermissionError"
        />
        <p class="input-footer">
          <template v-if="isDev">
            <button
              class="seed-icon-btn"
              :disabled="isProcessing || isSeeding"
              title="Reset seed (dev): clear DB and reload seed"
              aria-label="Reset seed"
              @click="handleResetSeed"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 22v-4" />
                <path d="M12 4a4 4 0 0 1 4 4c0 3-4 6-4 6s-4-3-4-6a4 4 0 0 1 4-4z" />
              </svg>
            </button>
          </template>
          <button
            type="button"
            class="version-inline"
            title="Refresh app"
            aria-label="Refresh app"
            @click="refreshApp()"
          >
            {{ versionDisplay }}
          </button>
        </p>
      </div>
    </div>
    <EntryEditModal
      v-if="editingEntry"
      :entry="editingEntry"
      @close="handleEditModalClose"
      @saved="handleEditSaved"
      @deleted="handleEntryDeleted"
    />
  </div>
</template>

<style scoped>
.conversation-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.input-area {
  flex-shrink: 0;
  padding: 1rem max(0.75rem, env(safe-area-inset-right)) max(1rem, env(safe-area-inset-bottom)) max(0.75rem, env(safe-area-inset-left));
  display: flex;
  gap: 0.75rem;
  align-items: flex-end;
  border-top: 1px solid #2a2a3e;
  background: #0f0f1a;
}

/* Mobile: minimal bottom padding to clear home indicator only */
@media (max-width: 600px) {
  .input-area {
    position: sticky;
    bottom: 0;
    left: 0;
    right: 0;
    padding-bottom: max(0.5rem, env(safe-area-inset-bottom), 16px);
    padding-left: max(0.75rem, env(safe-area-inset-left));
    padding-right: max(0.75rem, env(safe-area-inset-right));
  }
}

.input-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.context-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.context-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.25rem 0.5rem;
  font-size: 0.9375rem;
  background: rgba(74, 110, 219, 0.2);
  color: #a0b8f0;
  border-radius: 8px;
}

.context-chip--project {
  background: rgba(90, 138, 74, 0.25);
  color: #a8d098;
}

.context-chip-remove {
  padding: 0;
  margin: 0;
  background: transparent;
  color: inherit;
  border: none;
  cursor: pointer;
  font-size: 1.25rem;
  line-height: 1;
  opacity: 0.8;
}

.context-chip-remove:hover {
  opacity: 1;
}

.input-footer {
  margin: 0;
  font-size: 0.875rem;
  color: #666680;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.25rem;
}

.version-inline {
  padding: 0;
  margin: 0;
  font-size: 0.75rem;
  color: #5a5a70;
  background: none;
  border: none;
  cursor: pointer;
  font: inherit;
}

.version-inline:hover {
  color: #8888a0;
}

.seed-icon-btn {
  padding: 0;
  margin: 0;
  background: transparent;
  color: #666680;
  border: none;
  border-radius: 2px;
  cursor: pointer;
  display: inline-flex;
  transition: color 0.15s;
}

.seed-icon-btn:hover:not(:disabled) {
  color: #7cb342;
}

.seed-icon-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
