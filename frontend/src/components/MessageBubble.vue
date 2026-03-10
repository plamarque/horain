<script setup lang="ts">
import { computed } from 'vue'
import ChartBubble from './ChartBubble.vue'
import LogEntriesBlock from './LogEntriesBlock.vue'
import { renderMarkdown } from '../utils/markdown'
import type { ChartSpec, TimeLogEntry } from '../types'

const props = defineProps<{
  role: 'user' | 'assistant'
  text: string
  chart?: ChartSpec
  timeLogs?: TimeLogEntry[]
  isStreaming?: boolean
}>()

const emit = defineEmits<{
  selectEntry: [entry: TimeLogEntry]
  editEntry: [entry: TimeLogEntry]
}>()

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
    <ChartBubble v-if="chart" :spec="chart" class="chart-standalone" />
    <LogEntriesBlock
      v-if="timeLogs?.length"
      :entries="timeLogs"
      @select-entry="emit('selectEntry', $event)"
      @edit-entry="emit('editEntry', $event)"
    />
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
  font-size: 0.9rem;
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
</style>
