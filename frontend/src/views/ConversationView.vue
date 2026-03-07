<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import PushToTalkButton from '../components/PushToTalkButton.vue'
import ConversationTimeline from '../components/ConversationTimeline.vue'
import { sendChatMessage } from '../services/chatClient'
import { processQueue } from '../sync/syncEngine'
import { loadDevSeed } from '../services/apiClient'
import type { ChartSpec, Message } from '../types'

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

const messages = ref<Message[]>([])
const isProcessing = ref(false)
const lastSyncedAt = ref<Date | null>(null)
const inputRef = ref<InstanceType<typeof PushToTalkButton> | null>(null)

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

function addAssistantMessage(text: string, chart?: import('../types').ChartSpec) {
  messages.value.push({
    id: crypto.randomUUID(),
    role: 'assistant',
    text,
    timestamp: new Date(),
    ...(chart && { chart }),
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

  isProcessing.value = true
  try {
    const history = messages.value
      .slice(0, -1)
      .map((m) => ({ role: m.role, text: m.text }))
    const response = await sendChatMessage(text.trim(), history)
    const rawChart = response.data && typeof response.data === 'object' && 'chart' in response.data
      ? (response.data as { chart: unknown }).chart
      : undefined
    const chart = isValidChartSpec(rawChart) ? rawChart : undefined
    addAssistantMessage(response.assistantMessage, chart)

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
    <ConversationTimeline :messages="messages" :is-processing="isProcessing" />
    <div class="input-area">
      <div class="input-col">
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
