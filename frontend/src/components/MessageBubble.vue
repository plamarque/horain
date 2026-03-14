<script setup lang="ts">
import { computed, ref } from 'vue'
import ChartBubble from './ChartBubble.vue'
import LogEntriesBlock from './LogEntriesBlock.vue'
import AgentTraceBlock from './AgentTraceBlock.vue'
import { renderMarkdown } from '../utils/markdown'
import { sendFeedback } from '../services/chatClient'
import type { AgentTrace, ChartSpec, TimeLogEntry } from '../types'

const props = defineProps<{
  role: 'user' | 'assistant'
  text: string
  chart?: ChartSpec
  timeLogs?: TimeLogEntry[]
  isStreaming?: boolean
  /** Backend turn id for feedback (assistant only). */
  turnId?: string | null
  /** Agent tool execution trace (assistant only). Session-only. */
  agentTrace?: AgentTrace | null
}>()

const emit = defineEmits<{
  selectEntry: [entry: TimeLogEntry]
  editEntry: [entry: TimeLogEntry]
  editProject: [entry: TimeLogEntry]
}>()

const feedbackSent = ref<'up' | 'down' | null>(null)
const feedbackSending = ref(false)

async function handleFeedback(rating: 'up' | 'down') {
  if (!props.turnId || feedbackSending.value || feedbackSent.value) return
  feedbackSending.value = true
  try {
    await sendFeedback(props.turnId, rating)
    feedbackSent.value = rating
  } catch {
    // Silent; optional UX could show a toast
  } finally {
    feedbackSending.value = false
  }
}

const formattedContent = computed(() => {
  if (!props.text) return ''
  return props.role === 'assistant' ? renderMarkdown(props.text) : props.text
})

const useHtml = computed(() => props.role === 'assistant')
</script>

<template>
  <div class="message-block">
    <div class="message-row" :class="role">
      <img
        v-if="role === 'assistant'"
        src="/favicon.svg"
        alt="Horain"
        class="avatar"
      >
      <div class="bubble" :class="role">
        <div v-if="text && useHtml && !isStreaming" class="content content--markdown" v-html="formattedContent" />
        <div v-else-if="text" class="content">{{ isStreaming ? text : formattedContent }}</div>
        <span v-if="isStreaming" class="streaming-cursor" aria-hidden="true" />
      </div>
    </div>
    <AgentTraceBlock
      v-if="role === 'assistant'"
      :agent-trace="agentTrace"
      :is-streaming="isStreaming ?? false"
    />
    <ChartBubble v-if="chart" :spec="chart" class="chart-standalone" />
    <LogEntriesBlock
      v-if="timeLogs?.length"
      :entries="timeLogs"
      @select-entry="emit('selectEntry', $event)"
      @edit-entry="emit('editEntry', $event)"
      @edit-project="emit('editProject', $event)"
    />
    <div
      v-if="role === 'assistant' && turnId && !isStreaming"
      class="feedback-row"
      role="group"
      aria-label="Feedback on this response"
    >
      <button
        type="button"
        class="feedback-btn"
        :class="{ active: feedbackSent === 'up', disabled: feedbackSending || feedbackSent !== null }"
        :disabled="feedbackSending || feedbackSent !== null"
        aria-label="Good response"
        @click="handleFeedback('up')"
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <path d="M7 10v12" />
          <path
            d="M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88Z"
          />
        </svg>
      </button>
      <button
        type="button"
        class="feedback-btn"
        :class="{ active: feedbackSent === 'down', disabled: feedbackSending || feedbackSent !== null }"
        :disabled="feedbackSending || feedbackSent !== null"
        aria-label="Bad response"
        @click="handleFeedback('down')"
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <path d="M17 14V2" />
          <path
            d="M9 18.12 10 14H4.17a2 2 0 0 1-1.92-2.56l2.33-8A2 2 0 0 1 6.5 2H20a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2h-2.76a2 2 0 0 0-1.79 1.11L12 22a3.13 3.13 0 0 1-3-3.88Z"
          />
        </svg>
      </button>
    </div>
  </div>
</template>

<style scoped>
.message-block {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: 100%;
  gap: 0.5rem;
}

.message-row {
  display: flex;
  align-items: flex-end;
  gap: 0.5rem;
  max-width: 85%;
}

.message-row.user {
  align-self: flex-end;
  max-width: 85%;
}

.message-row.assistant {
  align-self: flex-start;
  max-width: 85%;
}

.message-row.assistant .bubble {
  max-width: calc(100% - 36px);
}

.avatar {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: contain;
  background: #2d2640;
}

.bubble {
  padding: 0.75rem 1rem;
  border-radius: 16px;
}

.message-row.user {
  max-width: 85%;
}

.message-row.user .bubble {
  max-width: 100%;
}

.bubble.user {
  background: #4a6edb;
  color: white;
}

.bubble.assistant {
  background: #2d2640;
  color: #e8e8f0;
  border: 1px solid rgba(139, 92, 246, 0.35);
}

.content {
  font-size: 1.125rem;
  white-space: pre-wrap;
  word-break: break-word;
}

.content--markdown {
  white-space: normal;
}

.content--markdown :deep(p) {
  margin: 0 0 0.5em;
}

.content--markdown :deep(p:last-child) {
  margin-bottom: 0;
}

.content--markdown :deep(ul) {
  margin: 0.5em 0;
  padding-left: 1.25em;
}

.content--markdown :deep(li) {
  margin: 0.2em 0;
}

.content--markdown :deep(strong) {
  font-weight: 600;
}

.streaming-cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: currentColor;
  animation: blink 1s step-end infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.chart-standalone {
  align-self: stretch;
  width: 100%;
}

.feedback-row {
  display: flex;
  gap: 0.25rem;
  margin-top: 0.25rem;
}

.feedback-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.35rem;
  min-width: 32px;
  min-height: 32px;
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  color: inherit;
  cursor: pointer;
  opacity: 0.7;
}

.feedback-btn:hover:not(:disabled) {
  opacity: 1;
  background: rgba(255, 255, 255, 0.08);
}

.feedback-btn.active {
  opacity: 1;
  border-color: rgba(139, 92, 246, 0.6);
}

.feedback-btn:disabled {
  cursor: default;
  opacity: 0.5;
}
</style>
