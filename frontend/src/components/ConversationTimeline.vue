<script setup lang="ts">
import { ref } from 'vue'
import MessageBubble from './MessageBubble.vue'
import LogEntriesBubble from './LogEntriesBubble.vue'
import type { Message, TimeLogEntry } from '../types'

defineProps<{
  messages: Message[]
  recentLogs?: TimeLogEntry[]
  isProcessing?: boolean
  hasNewMessageBelow?: boolean
}>()

const emit = defineEmits<{
  selectEntry: [entry: TimeLogEntry]
  editEntry: [entry: TimeLogEntry]
  indicatorClicked: []
}>()

const BOTTOM_THRESHOLD = 100

const timelineEl = ref<HTMLDivElement | null>(null)
const userAtBottom = ref(true)

function updateUserAtBottom() {
  const el = timelineEl.value
  if (!el) return
  const { scrollTop, clientHeight, scrollHeight } = el
  userAtBottom.value = scrollTop + clientHeight >= scrollHeight - BOTTOM_THRESHOLD
}

function scrollToBottom() {
  timelineEl.value?.scrollTo({
    top: timelineEl.value.scrollHeight,
    behavior: 'smooth',
  })
  userAtBottom.value = true
}

function handleIndicatorClick() {
  scrollToBottom()
  emit('indicatorClicked')
}

defineExpose({
  scrollToBottom,
  isUserAtBottom: () => userAtBottom.value,
})
</script>

<template>
  <div ref="timelineEl" class="timeline" @scroll="updateUserAtBottom">
    <div
      v-if="messages.length === 0 && !isProcessing"
      class="empty-state"
    >
      <template v-if="recentLogs?.length">
        <p class="empty-state-title">Dernières activités</p>
        <LogEntriesBubble
          :entries="recentLogs"
          @select-entry="emit('selectEntry', $event)"
          @edit-entry="emit('editEntry', $event)"
        />
        <p class="hint">Type in the field below or tap the mic to speak.</p>
      </template>
      <template v-else>
        <p>Say something like:</p>
        <p class="example">"30 minutes on HatCast working on the selection algorithm"</p>
        <p class="hint">Type in the field below or tap the mic to speak.</p>
      </template>
    </div>
    <MessageBubble
      v-for="msg in messages"
      :key="msg.id"
      :role="msg.role"
      :text="msg.text"
      :chart="msg.chart"
      :time-logs="msg.timeLogs"
      @select-entry="emit('selectEntry', $event)"
      @edit-entry="emit('editEntry', $event)"
    />
    <div v-if="isProcessing" class="processing-indicator">
      Processing...
    </div>
  </div>
  <!-- Floating indicator: fixed at bottom of timeline (above input), not in flex flow -->
  <button
    v-if="hasNewMessageBelow"
    type="button"
    class="new-message-indicator"
    @click="handleIndicatorClick"
  >
    <span class="new-message-indicator-arrow">↓</span>
    New message
  </button>
</template>

<style scoped>
.timeline {
  flex: 1;
  overflow-y: auto;
  padding: 1rem 0.75rem;
  padding-bottom: 3rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  position: relative;
}

.empty-state {
  padding: 1.5rem 0.5rem;
  text-align: center;
  color: #8888a0;
  font-size: 0.9rem;
}

.empty-state .example {
  margin: 0.75rem 0;
  padding: 0.75rem 1rem;
  background: #1a1a2e;
  border-radius: 8px;
  color: #a0a0c0;
  font-family: monospace;
}

.empty-state .empty-state-title {
  margin: 0 0 1rem;
  font-weight: 600;
  color: #a0a0c0;
}

.empty-state .hint {
  margin-top: 1rem;
  font-size: 0.8rem;
  color: #666680;
}

.processing-indicator {
  padding: 0.75rem 1rem;
  background: #2a2a3e;
  color: #8888a0;
  border-radius: 16px;
  font-size: 0.9rem;
  align-self: flex-start;
}

/* Sibling of timeline: sits between scroll area and input, always at bottom */
.new-message-indicator {
  flex-shrink: 0;
  align-self: center;
  margin: 0.25rem 0;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.5rem 1rem;
  background: #374151;
  color: #fff;
  border: 1px solid #4b5563;
  border-radius: 20px;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.5);
  transition: background 0.15s, border-color 0.15s;
}

.new-message-indicator:hover {
  background: #4b5563;
  border-color: #6b7280;
}

.new-message-indicator-arrow {
  font-size: 1rem;
  line-height: 1;
}
</style>
