<script setup lang="ts">
import { ref } from 'vue'
import MessageBubble from './MessageBubble.vue'
import LogEntriesBlock from './LogEntriesBlock.vue'
import type { Message, TimeLogEntry } from '../types'

defineProps<{
  messages: Message[]
  recentLogs?: TimeLogEntry[]
  isProcessing?: boolean
  /** When true, a message has isStreaming; hide "Processing..." and show the streaming bubble. */
  hasStreamingBubble?: boolean
  hasNewMessageBelow?: boolean
}>()

const emit = defineEmits<{
  selectEntry: [entry: TimeLogEntry]
  editEntry: [entry: TimeLogEntry]
  editProject: [entry: TimeLogEntry]
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
        <LogEntriesBlock
          title="Dernières activités"
          :entries="recentLogs"
          @select-entry="emit('selectEntry', $event)"
          @edit-entry="emit('editEntry', $event)"
          @edit-project="emit('editProject', $event)"
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
      :is-streaming="msg.isStreaming"
      @select-entry="emit('selectEntry', $event)"
      @edit-entry="emit('editEntry', $event)"
      @edit-project="emit('editProject', $event)"
    />
    <div
      v-if="isProcessing && !hasStreamingBubble"
      class="processing-indicator"
      aria-live="polite"
      role="status"
    >
      <span class="sr-only">Horain réfléchit</span>
      <span class="thinking-dot" aria-hidden="true" />
      <span class="thinking-dot" aria-hidden="true" />
      <span class="thinking-dot" aria-hidden="true" />
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

.empty-state .hint {
  margin-top: 1rem;
  font-size: 0.8rem;
  color: #666680;
}

.processing-indicator {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  background: #2d2640;
  border: 1px solid rgba(139, 92, 246, 0.35);
  border-radius: 16px;
  align-self: flex-start;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.thinking-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #8b5cf6;
  opacity: 0.4;
  animation: thinking-pulse 1.2s ease-in-out infinite;
}

.thinking-dot:nth-child(2) {
  animation-delay: 0s;
}

.thinking-dot:nth-child(3) {
  animation-delay: 0.2s;
}

.thinking-dot:nth-child(4) {
  animation-delay: 0.4s;
}

@keyframes thinking-pulse {
  0%,
  100% {
    opacity: 0.4;
    transform: scale(1);
  }
  50% {
    opacity: 1;
    transform: scale(1.15);
  }
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
