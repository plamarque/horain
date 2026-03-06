<script setup lang="ts">
import { ref } from 'vue'

defineProps<{
  disabled?: boolean
}>()

const emit = defineEmits<{
  submit: [text: string]
}>()

const inputText = ref('')
const isExpanded = ref(false)

function toggle() {
  isExpanded.value = !isExpanded.value
  if (!isExpanded.value) inputText.value = ''
}

function submit() {
  const t = inputText.value.trim()
  if (t) {
    emit('submit', t)
    inputText.value = ''
    isExpanded.value = false
  }
}
</script>

<template>
  <div class="ptt-container">
    <div v-if="isExpanded" class="input-wrapper">
      <input
        v-model="inputText"
        type="text"
        placeholder="e.g. 30 minutes on HatCast working on the selection algorithm"
        class="text-input"
        @keydown.enter="submit"
      />
      <button class="send-btn" :disabled="!inputText.trim()" @click="submit">
        Send
      </button>
    </div>
    <button
      class="ptt-btn"
      :class="{ expanded: isExpanded }"
      :disabled="disabled"
      @click="toggle"
    >
      {{ isExpanded ? 'Cancel' : 'Type or tap to speak' }}
    </button>
  </div>
</template>

<style scoped>
.ptt-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.input-wrapper {
  display: flex;
  gap: 0.5rem;
  width: 100%;
}

.text-input {
  flex: 1;
  padding: 0.75rem 1rem;
  background: #1a1a2e;
  border: 1px solid #2a2a3e;
  border-radius: 12px;
  color: #e8e8f0;
  font-size: 0.9rem;
}

.text-input::placeholder {
  color: #666680;
}

.text-input:focus {
  outline: none;
  border-color: #4a4a6e;
}

.send-btn {
  padding: 0.75rem 1.25rem;
  background: #4a6edb;
  color: white;
  border: none;
  border-radius: 12px;
  font-weight: 500;
  cursor: pointer;
}

.send-btn:hover:not(:disabled) {
  background: #5a7eeb;
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ptt-btn {
  padding: 1rem 1.5rem;
  background: #2a2a3e;
  color: #e8e8f0;
  border: none;
  border-radius: 12px;
  font-size: 0.9rem;
  cursor: pointer;
}

.ptt-btn:hover:not(:disabled) {
  background: #3a3a4e;
}

.ptt-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ptt-btn.expanded {
  background: #3a3a4e;
}
</style>
