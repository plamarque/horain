<script setup lang="ts">
import { ref } from 'vue'
import AudioWaveform from './AudioWaveform.vue'
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
  holdHint: []
  permissionError: [message: string]
}>()

const inputText = ref('')
const isExpanded = ref(false)
const isListening = ref(false)
const isReady = ref(false)
const interimTranscript = ref('')
const pointerDownAt = ref<number>(0)

/**
 * Push-to-talk: press and hold to record, release to send.
 * Uses pointer events to handle both touch and mouse uniformly.
 */
function onPointerDown() {
  if (isExpanded.value) return
  if (!isSpeechRecognitionSupported()) {
    isExpanded.value = true
    return
  }
  pointerDownAt.value = Date.now()
  interimTranscript.value = ''
  const started = startListening(
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
  if (started) {
    isListening.value = true
  }
}

function onPointerUp() {
  if (!isListening.value) return
  const holdDuration = Date.now() - pointerDownAt.value
  stopListening()
  // Quick tap (< 600ms) with no speech yet: show hint to hold longer
  if (holdDuration < 600) {
    emit('holdHint')
  }
}

function toggleTextInput() {
  isExpanded.value = !isExpanded.value
  if (!isExpanded.value) inputText.value = ''
}

function submitText() {
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
        @keydown.enter="submitText"
      />
      <button class="send-btn" :disabled="!inputText.trim()" @click="submitText">
        Send
      </button>
    </div>
    <div v-else class="ptt-column">
      <AudioWaveform v-if="isListening" :active="isListening" />
      <div v-if="isListening && interimTranscript" class="interim-feedback">
        {{ interimTranscript }}
      </div>
      <div class="ptt-row">
      <button
        class="ptt-btn"
        :class="{ listening: isListening }"
        :disabled="disabled"
        type="button"
        @pointerdown.prevent="onPointerDown"
        @pointerup.prevent="onPointerUp"
        @pointerleave="onPointerUp"
        @pointercancel.prevent="onPointerUp"
      >
        {{
          isListening
            ? (isReady ? 'Speak now' : 'Getting ready...')
            : isSpeechRecognitionSupported()
              ? 'Hold to speak'
              : 'Tap to type'
        }}
      </button>
      <button
        class="type-btn"
        :disabled="disabled"
        title="Type instead of speak"
        @click="toggleTextInput"
      >
        Type
      </button>
    </div>
    </div>
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

.ptt-column {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.interim-feedback {
  padding: 0.5rem 0.75rem;
  background: #1a1a2e;
  border-radius: 8px;
  font-size: 0.85rem;
  color: #a0a0c0;
  min-height: 1.5em;
  border-left: 3px solid #4a6edb;
}

.ptt-row {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.ptt-btn {
  flex: 1;
  padding: 1rem 1.5rem;
  min-height: 56px;
  background: #2a2a3e;
  color: #e8e8f0;
  border: none;
  border-radius: 12px;
  font-size: 0.9rem;
  cursor: pointer;
  user-select: none;
  touch-action: none;
  -webkit-tap-highlight-color: transparent;
}

.ptt-btn:hover:not(:disabled) {
  background: #3a3a4e;
}

.ptt-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ptt-btn.listening {
  background: #4a6edb;
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.85;
  }
}

.type-btn {
  padding: 1rem 1rem;
  min-height: 56px;
  background: #2a2a3e;
  color: #8888a0;
  border: none;
  border-radius: 12px;
  font-size: 0.875rem;
  cursor: pointer;
}

.type-btn:hover:not(:disabled) {
  background: #3a3a4e;
  color: #e8e8f0;
}

.type-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
