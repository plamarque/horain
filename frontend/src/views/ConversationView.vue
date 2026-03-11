<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import PushToTalkButton from '../components/PushToTalkButton.vue'
import ConversationTimeline from '../components/ConversationTimeline.vue'
import EntryEditModal from '../components/EntryEditModal.vue'
import ProjectEditModal from '../components/ProjectEditModal.vue'
import { sendChatMessage, sendChatMessageStream } from '../services/chatClient'
import { processQueue } from '../sync/syncEngine'
import { resetDevSeed, getRecentTimeLogs } from '../services/apiClient'
import type { ChartSpec, Message, TimeLogEntry } from '../types'

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

const messages = ref<Message[]>([])
const isProcessing = ref(false)
const streamingMessageId = ref<string | null>(null)
const abortControllerRef = ref<AbortController | null>(null)

/** True when the last assistant message is streaming; used so timeline hides "Processing..." as soon as the streaming bubble exists. */
const hasStreamingBubble = computed(() => messages.value.some((m) => m.isStreaming === true))
const lastSyncedAt = ref<Date | null>(null)
const inputRef = ref<InstanceType<typeof PushToTalkButton> | null>(null)
const timelineRef = ref<InstanceType<typeof ConversationTimeline> | null>(null)
const hasNewMessageBelow = ref(false)
const selectedEntries = ref<TimeLogEntry[]>([])
const editingEntry = ref<TimeLogEntry | null>(null)
const editingProjectId = ref<string | null>(null)
const recentLogs = ref<TimeLogEntry[]>([])

onMounted(async () => {
  if (messages.value.length > 0) return
  try {
    const logs = await getRecentTimeLogs(5)
    recentLogs.value = logs
  } catch {
    recentLogs.value = []
  }
})

// Refocus input when assistant finishes responding so user can type immediately
watch(isProcessing, async (now, was) => {
  if (was === true && now === false) {
    await nextTick()
    inputRef.value?.focusInput()
  }
})

function formatLastSynced(d: Date): string {
  return d.toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

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
  if (entry.projectId) editingProjectId.value = entry.projectId
}

function handleProjectModalClose() {
  editingProjectId.value = null
}

async function handleProjectSaved() {
  editingProjectId.value = null
  await processQueue()
  lastSyncedAt.value = new Date()
  try {
    const logs = await getRecentTimeLogs(5)
    recentLogs.value = logs
  } catch {
    // keep current recentLogs
  }
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
  }
  editingEntry.value = null
  await processQueue()
  lastSyncedAt.value = new Date()
  if (!patch?.id) {
    try {
      const logs = await getRecentTimeLogs(5)
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
    const logs = await getRecentTimeLogs(5)
    recentLogs.value = logs
  } catch {
    recentLogs.value = []
  }
  await processQueue()
  lastSyncedAt.value = new Date()
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
  timeLogs?: TimeLogEntry[]
) {
  const wasAtBottom = timelineRef.value?.isUserAtBottom() ?? true
  messages.value.push({
    id: crypto.randomUUID(),
    role: 'assistant',
    text,
    timestamp: new Date(),
    ...(chart && { chart }),
    ...(timeLogs?.length && { timeLogs }),
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
  selectedEntries.value = []

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
            const id = crypto.randomUUID()
            streamingMessageId.value = id
            const wasAtBottom = timelineRef.value?.isUserAtBottom() ?? true
            messages.value.push({
              id,
              role: 'assistant',
              text: chunk,
              timestamp: new Date(),
              isStreaming: true,
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
        onDone(payload) {
          const rawChart = payload.data && typeof payload.data === 'object' && 'chart' in payload.data
            ? (payload.data as { chart: unknown }).chart
            : undefined
          const chart = isValidChartSpec(rawChart) ? rawChart : undefined
          const rawTimeLogs = payload.data && typeof payload.data === 'object' && 'timeLogs' in payload.data
            ? (payload.data as { timeLogs: unknown }).timeLogs
            : undefined
          const timeLogs = isValidTimeLogEntries(rawTimeLogs) ? rawTimeLogs : undefined
          if (streamingMessageId.value) {
            const msg = messages.value.find((m) => m.id === streamingMessageId.value)
            if (msg) {
              msg.text = payload.assistantMessage ?? msg.text
              msg.isStreaming = false
              if (chart) msg.chart = chart
              if (timeLogs?.length) msg.timeLogs = timeLogs
            }
          } else {
            addAssistantMessage(payload.assistantMessage ?? '', chart, timeLogs)
          }
          streamingMessageId.value = null
          processQueue().then(() => {
            lastSyncedAt.value = new Date()
          })
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
      { signal: abortControllerRef.value?.signal }
    )
  } catch (err) {
    if ((err as Error).name === 'AbortError') return
    const status = (err as Error & { status?: number }).status
    if (status === 404 || status === 405) {
      try {
        const response = await sendChatMessage(text.trim(), history, contextToSend, {
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
        addAssistantMessage(response.assistantMessage, chart, timeLogs)
        await processQueue()
        lastSyncedAt.value = new Date()
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

async function handleSync() {
  isProcessing.value = true
  try {
    await processQueue()
    lastSyncedAt.value = new Date()
    addAssistantMessage('Sync completed.')
  } finally {
    isProcessing.value = false
  }
}

const isDev = import.meta.env.DEV
const isSeeding = ref(false)

async function handleResetSeed() {
  if (!isDev) return
  isSeeding.value = true
  try {
    await resetDevSeed()
    await processQueue()
    lastSyncedAt.value = new Date()
    const logs = await getRecentTimeLogs(5)
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
      @select-entry="handleSelectEntry"
      @edit-entry="handleEditEntry"
      @edit-project="handleEditProject"
      @indicator-clicked="handleIndicatorClicked"
    />
    <div class="input-area">
      <div class="input-col">
        <div v-if="selectedEntries.length" class="context-chips">
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
        <p class="last-synced">
          <span v-if="lastSyncedAt">Last synced {{ formatLastSynced(lastSyncedAt) }}</span>
          <span v-else>Not synced yet</span>
          <button
            class="sync-icon-btn"
            :disabled="isProcessing"
            title="Sync now"
            aria-label="Sync now"
            @click="handleSync"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 2v6h-6" />
              <path d="M3 12a9 9 0 0 1 15-6.7L21 8" />
              <path d="M3 22v-6h6" />
              <path d="M21 12a9 9 0 0 1-15 6.7L3 16" />
            </svg>
          </button>
          <button
            v-if="isDev"
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
    <ProjectEditModal
      v-if="editingProjectId"
      :project-id="editingProjectId"
      @close="handleProjectModalClose"
      @saved="handleProjectSaved"
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
  padding: 1rem
    max(0.75rem, env(safe-area-inset-right))
    max(1rem, env(safe-area-inset-bottom))
    max(0.75rem, env(safe-area-inset-left));
  display: flex;
  gap: 0.75rem;
  align-items: flex-end;
  border-top: 1px solid #2a2a3e;
  background: #0f0f1a;
}

/* Mobile: keep input bar always visible, extra bottom padding for gesture bar and rounded corners */
@media (max-width: 600px) {
  .input-area {
    position: sticky;
    bottom: 0;
    left: 0;
    right: 0;
    /* Fallback 34px when env() is 0 (some Android report no safe-area) */
    padding-bottom: max(1.5rem, env(safe-area-inset-bottom), 34px);
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
  font-size: 0.75rem;
  background: rgba(74, 110, 219, 0.2);
  color: #a0b8f0;
  border-radius: 8px;
}

.context-chip-remove {
  padding: 0;
  margin: 0;
  background: transparent;
  color: inherit;
  border: none;
  cursor: pointer;
  font-size: 1rem;
  line-height: 1;
  opacity: 0.8;
}

.context-chip-remove:hover {
  opacity: 1;
}

.last-synced {
  margin: 0;
  font-size: 0.7rem;
  color: #666680;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.25rem;
}

.sync-icon-btn {
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

.sync-icon-btn:hover:not(:disabled) {
  color: #e8e8f0;
}

.sync-icon-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
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
