<script setup lang="ts">
import MessageBubble from './MessageBubble.vue'
import type { Message } from '../types'

defineProps<{
  messages: Message[]
  isProcessing?: boolean
}>()
</script>

<template>
  <div class="timeline">
    <div v-if="messages.length === 0 && !isProcessing" class="empty-state">
      <p>Say something like:</p>
      <p class="example">"30 minutes on HatCast working on the selection algorithm"</p>
      <p class="hint">Type in the field below or tap the mic to speak.</p>
    </div>
    <MessageBubble
      v-for="msg in messages"
      :key="msg.id"
      :role="msg.role"
      :text="msg.text"
      :chart="msg.chart"
    />
    <div v-if="isProcessing" class="processing-indicator">
      Processing...
    </div>
  </div>
</template>

<style scoped>
.timeline {
  flex: 1;
  overflow-y: auto;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.empty-state {
  padding: 2rem;
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
</style>
