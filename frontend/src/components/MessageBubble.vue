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
    <div class="bubble" :class="role">
      <div v-if="text && useHtml && !isStreaming" class="content content--markdown" v-html="formattedContent" />
      <div v-else-if="text" class="content">{{ isStreaming ? text : formattedContent }}</div>
      <span v-if="isStreaming" class="streaming-cursor" aria-hidden="true" />
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

.bubble {
  max-width: 85%;
  padding: 0.75rem 1rem;
  border-radius: 16px;
  align-self: flex-start;
}

.bubble.user {
  align-self: flex-end;
  background: #4a6edb;
  color: white;
}

.bubble.assistant {
  background: #2a2a3e;
  color: #e8e8f0;
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
