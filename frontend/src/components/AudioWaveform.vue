<script setup lang="ts">
import { ref, onUnmounted, watch } from 'vue'

/**
 * Real-time audio level waveform using Web Audio API.
 * Bar heights reflect actual microphone input level.
 */
const props = defineProps<{
  active: boolean
}>()

const barCount = 12
const levels = ref<number[]>(Array(barCount).fill(0))
const hasRealAudio = ref(false)

let stream: MediaStream | null = null
let audioContext: AudioContext | null = null
let analyser: AnalyserNode | null = null
let source: MediaStreamAudioSourceNode | null = null
let gainNode: GainNode | null = null
let rafId = 0

const timeData = new Uint8Array(256)

function startAudioCapture() {
  if (!props.active) return
  navigator.mediaDevices
    .getUserMedia({ audio: true })
    .then(async (s) => {
      hasRealAudio.value = true
      stream = s
      audioContext = new AudioContext()
      analyser = audioContext.createAnalyser()
      analyser.fftSize = 256
      analyser.smoothingTimeConstant = 0.5
      source = audioContext.createMediaStreamSource(stream)
      gainNode = audioContext.createGain()
      gainNode.gain.value = 0
      source.connect(analyser)
      analyser.connect(gainNode)
      gainNode.connect(audioContext.destination)
      if (audioContext.state === 'suspended') {
        await audioContext.resume()
      }
      updateLevels()
    })
    .catch(() => {
      hasRealAudio.value = false
      levels.value = Array(barCount).fill(0)
    })
}

function updateLevels() {
  if (!analyser || !props.active) return
  analyser.getByteTimeDomainData(timeData)
  const chunkSize = Math.floor(256 / barCount)
  const next = levels.value.slice()
  for (let i = 0; i < barCount; i++) {
    const start = i * chunkSize
    const end = Math.min(start + chunkSize, 256)
    let maxDev = 0
    for (let j = start; j < end; j++) {
      const dev = Math.abs(timeData[j] - 128)
      if (dev > maxDev) maxDev = dev
    }
    next[i] = Math.min(1, (maxDev / 128) * 2)
  }
  levels.value = next
  rafId = requestAnimationFrame(updateLevels)
}

function stopAudioCapture() {
  cancelAnimationFrame(rafId)
  rafId = 0
  gainNode?.disconnect()
  gainNode = null
  source?.disconnect()
  source = null
  analyser?.disconnect()
  analyser = null
  audioContext?.close()
  audioContext = null
  stream?.getTracks().forEach((t) => t.stop())
  stream = null
  hasRealAudio.value = false
  levels.value = Array(barCount).fill(0)
}

watch(
  () => props.active,
  (active) => {
    if (active) {
      startAudioCapture()
    } else {
      stopAudioCapture()
    }
  }
)

onUnmounted(() => {
  stopAudioCapture()
})
</script>

<template>
  <div class="waveform">
    <span
      v-for="(level, i) in levels"
      :key="i"
      class="wave-bar"
      :class="{ fallback: !hasRealAudio }"
      :style="hasRealAudio ? { '--level': level } : { '--i': i }"
    />
  </div>
</template>

<style scoped>
.waveform {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 32px;
}

.wave-bar {
  display: block;
  width: 4px;
  height: 24px;
  min-height: 4px;
  background: #4a6edb;
  border-radius: 2px;
  transform-origin: center bottom;
  transition: transform 0.05s ease-out;
}

.wave-bar:not(.fallback) {
  transform: scaleY(calc(0.15 + var(--level, 0) * 0.85));
}

.wave-bar.fallback {
  animation: wave-fallback 0.6s ease-in-out infinite;
  animation-delay: calc(var(--i, 0) * 0.05s);
}

@keyframes wave-fallback {
  0%, 100% { transform: scaleY(0.3); }
  50% { transform: scaleY(1); }
}
</style>
