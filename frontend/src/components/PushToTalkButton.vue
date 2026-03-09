<script setup lang="ts">
import { ref, nextTick } from 'vue'
import AudioWaveform from './AudioWaveform.vue'
import {
  startListening,
  stopListening,
  isSpeechRecognitionSupported,
} from '../services/speechRecognition'

defineProps<{
  disabled?: boolean
  processing?: boolean
}>()

const emit = defineEmits<{
  submit: [text: string]
  stop: []
  permissionError: [message: string]
}>()

type VoiceState = 'idle' | 'recording' | 'transcribing'
const voiceState = ref<VoiceState>('idle')
const voiceAction = ref<'confirm' | 'cancel'>('cancel')
let graceTimeoutId: ReturnType<typeof setTimeout> | null = null

const inputText = ref('')
const inputEl = ref<HTMLInputElement | null>(null)
const savedCaretStart = ref(0)
const savedCaretEnd = ref(0)

function focusInput() {
  inputEl.value?.focus()
}

defineExpose({ focusInput })

/**
 * Insert transcript at caret position. Does not replace existing text.
 */
function insertAtCaret(transcript: string) {
  const start = savedCaretStart.value
  const end = savedCaretEnd.value
  const before = inputText.value.slice(0, start)
  const after = inputText.value.slice(end)
  inputText.value = before + transcript + after
  nextTick(() => {
    const el = inputEl.value
    if (el) {
      const newPos = start + transcript.length
      el.setSelectionRange(newPos, newPos)
      el.focus()
    }
  })
}

function startVoiceRecording() {
  if (!isSpeechRecognitionSupported()) {
    return
  }
  const el = inputEl.value
  savedCaretStart.value = el?.selectionStart ?? inputText.value.length
  savedCaretEnd.value = el?.selectionEnd ?? inputText.value.length

  voiceState.value = 'recording'
  voiceAction.value = 'cancel'

  startListening(
    (text) => {
      if (voiceAction.value === 'confirm') {
        insertAtCaret(text)
      }
      voiceState.value = 'idle'
    },
    undefined,
    (errorCode) => {
      voiceState.value = 'idle'
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
    }
  )
}

function handleVoiceCancel() {
  voiceAction.value = 'cancel'
  stopListening()
  voiceState.value = 'idle'
}

function handleVoiceConfirm() {
  voiceAction.value = 'confirm'
  voiceState.value = 'transcribing'
  const graceMs = Number(import.meta.env.VITE_STT_GRACE_MS) || 400
  graceTimeoutId = setTimeout(() => {
    graceTimeoutId = null
    stopListening()
  }, graceMs)
}

function handleTranscribingCancel() {
  if (graceTimeoutId) {
    clearTimeout(graceTimeoutId)
    graceTimeoutId = null
  }
  voiceAction.value = 'cancel'
  stopListening()
  voiceState.value = 'idle'
}

function submitText() {
  const t = inputText.value.trim()
  if (t) {
    emit('submit', t)
    inputText.value = ''
  }
}

function handleStop() {
  emit('stop')
}

const showIdleView = () => voiceState.value === 'idle'
const showRecordingView = () => voiceState.value === 'recording'
const showTranscribingView = () => voiceState.value === 'transcribing'
</script>

<template>
  <div class="input-bar">
    <div class="pill-wrapper">
      <!-- Idle: input + mic + send -->
      <template v-if="showIdleView()">
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
          class="action-btn mic-btn"
          :disabled="disabled"
          type="button"
          title="Click to speak"
          aria-label="Click to speak"
          @click="startVoiceRecording"
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
        <button
          v-if="inputText.trim().length > 0 && !disabled && !processing"
          class="action-btn send-btn"
          type="button"
          title="Send"
          aria-label="Send"
          @click="submitText"
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
            <path d="M12 19V5" />
            <path d="m5 12 7-7 7 7" />
          </svg>
        </button>
        <button
          v-if="processing"
          class="action-btn stop-btn"
          type="button"
          title="Stop"
          aria-label="Stop"
          @click="handleStop"
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
            <rect x="6" y="6" width="12" height="12" rx="1" />
          </svg>
        </button>
      </template>

      <!-- Recording: waveform + Cancel + Confirm -->
      <template v-else-if="showRecordingView()">
        <div class="recording-content">
          <div class="waveform-slot">
            <AudioWaveform :active="true" />
          </div>
          <div class="recording-actions">
            <button
            class="action-btn cancel-btn"
            type="button"
            title="Cancel"
            aria-label="Cancel"
            @click="handleVoiceCancel"
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
              <line x1="18" x2="6" y1="6" y2="18" />
              <line x1="6" x2="18" y1="6" y2="18" />
            </svg>
          </button>
          <button
            class="action-btn confirm-btn"
            type="button"
            title="Confirm"
            aria-label="Confirm"
            @click="handleVoiceConfirm"
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
              <polyline points="20 6 9 17 4 12" />
            </svg>
          </button>
          </div>
        </div>
      </template>

      <!-- Transcribing: brief indicator + Cancel to abort -->
      <template v-else-if="showTranscribingView()">
        <div class="transcribing-content">
          <span class="transcribing-text">Transcribing…</span>
          <button
            class="action-btn cancel-btn"
            type="button"
            title="Cancel"
            aria-label="Cancel"
            @click="handleTranscribingCancel"
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
              <line x1="18" x2="6" y1="6" y2="18" />
              <line x1="6" x2="18" y1="6" y2="18" />
            </svg>
          </button>
        </div>
      </template>
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

@media (max-width: 600px) {
  .pill-wrapper {
    padding: 0.5rem;
    padding-right: 0.4rem;
    min-height: 56px;
  }
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

@media (max-width: 600px) {
  .text-input {
    padding: 0.75rem 1rem;
    font-size: 1.0625rem; /* 17px - avoids zoom on iOS */
    min-height: 48px;
  }
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

.recording-content {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.25rem 0.5rem;
  min-width: 0;
}

.waveform-slot {
  flex: 1;
  min-width: 0;
}

.recording-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.transcribing-content {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.25rem 0.5rem;
}

.transcribing-text {
  flex: 1;
  font-size: 0.9rem;
  color: #8888a0;
}

.action-btn {
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

.action-btn:hover:not(:disabled) {
  color: #e8e8f0;
  background: rgba(255, 255, 255, 0.06);
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.cancel-btn:hover:not(:disabled) {
  color: #ef5350;
  background: rgba(198, 40, 40, 0.2);
}

.confirm-btn {
  color: #4a6edb;
}

.confirm-btn:hover:not(:disabled) {
  color: #6b8aeb;
  background: rgba(74, 110, 219, 0.2);
}

.send-btn:hover:not(:disabled) {
  color: #e8e8f0;
  background: rgba(255, 255, 255, 0.06);
}

.stop-btn {
  color: #c62828;
}

.stop-btn:hover:not(:disabled) {
  color: #ef5350;
  background: rgba(198, 40, 40, 0.2);
}

@media (max-width: 600px) {
  .action-btn {
    width: 56px;
    height: 56px;
    min-width: 56px;
    min-height: 56px;
  }
}
</style>
