<script setup lang="ts">
import { ref } from 'vue'
import {
  startListening,
  stopListening,
  isSpeechRecognitionSupported,
} from '../services/speechRecognition'

defineProps<{
  disabled?: boolean
}>()

const emit = defineEmits<{
  submit: [text: string]
  permissionError: [message: string]
}>()

const inputText = ref('')
const inputEl = ref<HTMLInputElement | null>(null)
const isListening = ref(false)

function focusInput() {
  inputEl.value?.focus()
}

defineExpose({ focusInput })
const isReady = ref(false)
const interimTranscript = ref('')

/**
 * Click-to-talk: click mic to start, click again to stop and submit.
 * Toggle behavior instead of hold-to-talk.
 */
function toggleVoiceInput() {
  if (isListening.value) {
    stopListening()
    return
  }
  if (!isSpeechRecognitionSupported()) {
    return
  }
  interimTranscript.value = ''
  startListening(
    (text) => {
      if (text) {
        emit('submit', text)
      }
      interimTranscript.value = ''
      isListening.value = false
    },
    (status) => {
      if (status === 'listening') {
        isListening.value = true
        isReady.value = false
      } else if (status === 'ready') {
        isReady.value = true
      } else if (status === 'stopped' || status === 'error') {
        isListening.value = false
        isReady.value = false
        interimTranscript.value = ''
      }
    },
    (errorCode) => {
      if (errorCode === 'not-allowed' || errorCode === 'service-not-allowed') {
        emit(
          'permissionError',
          'Microphone access denied. Use HTTPS (or localhost), allow microphone permission in your browser, and try again.'
        )
      } else if (errorCode === 'audio-capture') {
        emit('permissionError', 'No microphone found. Please connect a microphone.')
      } else if (errorCode === 'network') {
        emit('permissionError', 'Network error. Speech recognition requires an internet connection.')
      }
    },
    (interim) => {
      interimTranscript.value = interim
    }
  )
  isListening.value = true
}

function submitText() {
  const t = inputText.value.trim()
  if (t) {
    emit('submit', t)
    inputText.value = ''
  }
}
</script>

<template>
  <div class="input-bar">
    <div class="pill-wrapper">
      <input
        ref="inputEl"
        v-model="inputText"
        type="text"
        placeholder="Ask anything"
        class="text-input"
        :disabled="disabled"
        @keydown.enter="submitText"
      />
      <button
        class="mic-btn"
        :class="{ listening: isListening }"
        :disabled="disabled"
        type="button"
        :title="isListening ? 'Click to stop' : 'Click to speak'"
        @click="toggleVoiceInput"
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          width="22"
          height="22"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z" />
          <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
          <line x1="12" x2="12" y1="19" y2="22" />
        </svg>
      </button>
    </div>
    <div v-if="isListening && interimTranscript" class="interim-feedback">
      {{ interimTranscript }}
    </div>
  </div>
</template>

<style scoped>
.input-bar {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  position: relative;
}

.pill-wrapper {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.25rem;
  padding-right: 0.25rem;
  background: #1a1a2e;
  border: 1px solid #2a2a3e;
  border-radius: 999px;
}

.pill-wrapper:focus-within {
  border-color: #4a4a6e;
}

.text-input {
  flex: 1;
  padding: 0.5rem 1rem;
  background: transparent;
  border: none;
  color: #e8e8f0;
  font-size: 0.9rem;
}

.text-input::placeholder {
  color: #666680;
}

.text-input:focus {
  outline: none;
}

.text-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.mic-btn {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  color: #8888a0;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  transition: color 0.15s, background 0.15s;
}

.mic-btn:hover:not(:disabled) {
  color: #e8e8f0;
  background: rgba(255, 255, 255, 0.06);
}

.mic-btn.listening {
  color: #4a6edb;
}

.mic-btn.listening:hover:not(:disabled) {
  background: rgba(74, 110, 219, 0.2);
}

.mic-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.interim-feedback {
  padding: 0.5rem 0.75rem;
  background: #1a1a2e;
  border-radius: 8px;
  font-size: 0.85rem;
  color: #a0a0c0;
  border-left: 3px solid #4a6edb;
}
</style>
