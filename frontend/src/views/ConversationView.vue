<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import PushToTalkButton from '../components/PushToTalkButton.vue'
import ConversationTimeline from '../components/ConversationTimeline.vue'
import EntryEditModal from '../components/EntryEditModal.vue'
import { sendChatMessage } from '../services/chatClient'
import { processQueue } from '../sync/syncEngine'
import { loadDevSeed } from '../services/apiClient'
import type { ChartSpec, Message, TimeLogEntry } from '../types'

const MAX_CONTEXT_ENTRIES = 5

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
const lastSyncedAt = ref<Date | null>(null)
const inputRef = ref<InstanceType<typeof PushToTalkButton> | null>(null)
const selectedEntries = ref<TimeLogEntry[]>([])
const editingEntry = ref<TimeLogEntry | null>(null)

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

function handleRemoveFromContext(entry: TimeLogEntry) {
  selectedEntries.value = selectedEntries.value.filter((e) => e.id !== entry.id)
}

function handleEditModalClose() {
  editingEntry.value = null
}

async function handleEditSaved() {
  editingEntry.value = null
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
  messages.value.push({
    id: crypto.randomUUID(),
    role: 'assistant',
    text,
    timestamp: new Date(),
    ...(chart && { chart }),
    ...(timeLogs?.length && { timeLogs }),
  })
}

async function handleSubmit(text: string) {
  if (!text.trim()) return
  messages.value.push({
    id: crypto.randomUUID(),
    role: 'user',
    text: text.trim(),
    timestamp: new Date(),
  })

  const contextToSend = selectedEntries.value
  selectedEntries.value = []

  isProcessing.value = true
  try {
    const history = messages.value
      .slice(0, -1)
      .map((m) => ({ role: m.role, text: m.text }))
    const response = await sendChatMessage(text.trim(), history, contextToSend)
    const rawChart = response.data && typeof response.data === 'object' && 'chart' in response.data
      ? (response.data as { chart: unknown }).chart
      : undefined
    const chart = isValidChartSpec(rawChart) ? rawChart : undefined
    const rawTimeLogs = response.data && typeof response.data === 'object' && 'timeLogs' in response.data
      ? (response.data as { timeLogs: unknown }).timeLogs
      : undefined
    const timeLogs = isValidTimeLogEntries(rawTimeLogs) ? rawTimeLogs : undefined
    addAssistantMessage(response.assistantMessage, chart, timeLogs)

    // Pull server updates (e.g. time logs created by backend tools)
    await processQueue()
    lastSyncedAt.value = new Date()
  } catch {
    addAssistantMessage(
      'Unable to reach the assistant. Check that the backend is running and LLM_API_KEY is configured.'
    )
  } finally {
    isProcessing.value = false
  }
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

async function handleLoadSeed() {
  if (!isDev) return
  isSeeding.value = true
  try {
    const result = await loadDevSeed()
    await processQueue()
    lastSyncedAt.value = new Date()
    addAssistantMessage(
      `Seed loaded: ${result.projectsCreated} projects, ${result.timeLogsCreated} time logs. Try "Sur quoi j'ai travaillé cette semaine ?"`
    )
  } catch {
    addAssistantMessage('Seed load failed. Is the backend running with dev seed enabled?')
  } finally {
    isSeeding.value = false
  }
}
</script>

<template>
  <div class="conversation-view">
    <ConversationTimeline
      :messages="messages"
      :is-processing="isProcessing"
      @select-entry="handleSelectEntry"
      @edit-entry="handleEditEntry"
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
          @submit="handleSubmit"
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
            title="Load seed data (dev)"
            aria-label="Load seed data"
            @click="handleLoadSeed"
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
    />
  </div>
</template>

<style scoped>
.conversation-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.input-area {
  padding: 1rem;
  display: flex;
  gap: 0.75rem;
  align-items: flex-end;
  border-top: 1px solid #2a2a3e;
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
