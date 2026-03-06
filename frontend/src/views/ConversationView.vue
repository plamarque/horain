<script setup lang="ts">
import { ref } from 'vue'
import PushToTalkButton from '../components/PushToTalkButton.vue'
import ConversationTimeline from '../components/ConversationTimeline.vue'
import { processTranscription } from '../agent/conversationAgent'
import { processQueue } from '../sync/syncEngine'
import type { Message } from '../types'

const messages = ref<Message[]>([])
const isProcessing = ref(false)

async function handleSubmit(text: string) {
  if (!text.trim()) return
  const userMsg: Message = {
    id: crypto.randomUUID(),
    role: 'user',
    text: text.trim(),
    timestamp: new Date(),
  }
  messages.value.push(userMsg)

  isProcessing.value = true
  try {
    const response = await processTranscription(text)
    const assistantMsg: Message = {
      id: crypto.randomUUID(),
      role: 'assistant',
      text: response.text,
      timestamp: new Date(),
    }
    messages.value.push(assistantMsg)
    if (response.success) {
      await processQueue()
    }
  } finally {
    isProcessing.value = false
  }
}

async function handleSync() {
  isProcessing.value = true
  try {
    await processQueue()
    const msg: Message = {
      id: crypto.randomUUID(),
      role: 'assistant',
      text: 'Sync completed.',
      timestamp: new Date(),
    }
    messages.value.push(msg)
  } finally {
    isProcessing.value = false
  }
}
</script>

<template>
  <div class="conversation-view">
    <ConversationTimeline :messages="messages" />
    <div class="input-area">
      <PushToTalkButton
        :disabled="isProcessing"
        @submit="handleSubmit"
      />
      <button
        class="sync-btn"
        :disabled="isProcessing"
        @click="handleSync"
      >
        Sync
      </button>
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
  align-items: center;
  border-top: 1px solid #2a2a3e;
}

.sync-btn {
  padding: 0.5rem 1rem;
  background: #2a2a3e;
  color: #e8e8f0;
  border: none;
  border-radius: 8px;
  font-size: 0.875rem;
  cursor: pointer;
}

.sync-btn:hover:not(:disabled) {
  background: #3a3a4e;
}

.sync-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
