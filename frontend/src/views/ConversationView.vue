<script setup lang="ts">
import { ref } from 'vue'
import PushToTalkButton from '../components/PushToTalkButton.vue'
import ConversationTimeline from '../components/ConversationTimeline.vue'
import { sendChatMessage } from '../services/chatClient'
import {
  processTranscription,
  type ConversationContext,
} from '../agent/conversationAgent'
import { processQueue } from '../sync/syncEngine'
import type { Message } from '../types'

const messages = ref<Message[]>([])
const isProcessing = ref(false)
/** Fallback: pending context for rule-based agent when LLM is not configured */
const pendingContext = ref<ConversationContext | null>(null)

function handleHoldHint() {
  addAssistantMessage('Hold the button while speaking, then release when done.')
}

function handlePermissionError(message: string) {
  addAssistantMessage(message)
}

function addAssistantMessage(text: string) {
  messages.value.push({
    id: crypto.randomUUID(),
    role: 'assistant',
    text,
    timestamp: new Date(),
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

    // Fallback: if LLM not configured, use local rule-based agent
    if (response.assistantMessage.includes('LLM is not configured')) {
      const ruleResponse = await processTranscription(
        text.trim(),
        pendingContext.value ?? undefined
      )
      addAssistantMessage(ruleResponse.text)
      if (ruleResponse.success) {
        pendingContext.value = null
      } else if (ruleResponse.pendingContext) {
        pendingContext.value = ruleResponse.pendingContext
      } else {
        pendingContext.value = null
      }
    } else {
      addAssistantMessage(response.assistantMessage)
      pendingContext.value = null
    }

    // Pull server updates (e.g. time logs created by backend tools)
    await processQueue()
  } catch (e) {
    // Network error: fall back to local rule-based agent
    try {
      const ruleResponse = await processTranscription(
        text.trim(),
        pendingContext.value ?? undefined
      )
      addAssistantMessage(ruleResponse.text)
      if (ruleResponse.success) {
        pendingContext.value = null
      } else if (ruleResponse.pendingContext) {
        pendingContext.value = ruleResponse.pendingContext
      } else {
        pendingContext.value = null
      }
      await processQueue()
    } catch {
      addAssistantMessage('An error occurred. Please try again.')
      pendingContext.value = null
    }
  } finally {
    isProcessing.value = false
  }
}

async function handleSync() {
  isProcessing.value = true
  try {
    await processQueue()
    addAssistantMessage('Sync completed.')
  } finally {
    isProcessing.value = false
  }
}
</script>

<template>
  <div class="conversation-view">
    <ConversationTimeline :messages="messages" :is-processing="isProcessing" />
    <div class="input-area">
      <PushToTalkButton
        :disabled="isProcessing"
        @submit="handleSubmit"
        @hold-hint="handleHoldHint"
        @permission-error="handlePermissionError"
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
