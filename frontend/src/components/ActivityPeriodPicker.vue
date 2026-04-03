<script setup lang="ts">
import { ref, inject, computed, watch, onMounted, onUnmounted, type Ref } from 'vue'
import type { ActivityPeriodCustom, ActivityPeriodPreset } from '../activityPeriod'
import { formatActivityPeriodSummary } from '../activityPeriod'

const preset = inject<Ref<ActivityPeriodPreset>>('activityPeriodPreset')!
const custom = inject<Ref<ActivityPeriodCustom>>('activityPeriodCustom')!

const open = ref(false)
const panelEl = ref<HTMLElement | null>(null)
const triggerEl = ref<HTMLElement | null>(null)

const summary = computed(() => formatActivityPeriodSummary(preset.value, custom.value))

const draftFrom = ref('')
const draftTo = ref('')

watch(open, (isOpen) => {
  if (isOpen) {
    draftFrom.value = custom.value.fromYmd
    draftTo.value = custom.value.toYmd
  }
})

function select(p: ActivityPeriodPreset) {
  preset.value = p
  if (p !== 'custom') {
    open.value = false
  }
}

function applyCustom() {
  custom.value = { fromYmd: draftFrom.value, toYmd: draftTo.value }
  preset.value = 'custom'
  open.value = false
}

function toggle() {
  open.value = !open.value
}

function onDocPointerDown(e: MouseEvent | TouchEvent) {
  if (!open.value) return
  const t = e.target as Node
  if (panelEl.value?.contains(t) || triggerEl.value?.contains(t)) return
  open.value = false
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') open.value = false
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocPointerDown, true)
  document.addEventListener('keydown', onKeydown)
})
onUnmounted(() => {
  document.removeEventListener('pointerdown', onDocPointerDown, true)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div class="period-picker">
    <button
      ref="triggerEl"
      type="button"
      class="period-picker-trigger"
      :aria-expanded="open"
      aria-haspopup="dialog"
      aria-label="Activity period"
      @click="toggle"
    >
      <span class="period-picker-label">{{ summary }}</span>
      <span class="period-picker-chevron" aria-hidden="true">{{ open ? '▲' : '▼' }}</span>
    </button>
    <div
      v-show="open"
      ref="panelEl"
      class="period-picker-panel"
      role="dialog"
      aria-label="Choose activity period"
    >
      <p class="period-picker-hint">Projects and recent activity use this window.</p>
      <ul class="period-picker-presets">
        <li>
          <button type="button" class="period-picker-option" @click="select('rolling_28d')">Last 28 days</button>
        </li>
        <li>
          <button type="button" class="period-picker-option" @click="select('rolling_7d')">Last 7 days</button>
        </li>
        <li>
          <button type="button" class="period-picker-option" @click="select('calendar_month')">This month</button>
        </li>
        <li>
          <button type="button" class="period-picker-option" @click="select('calendar_prev_month')">
            Previous month
          </button>
        </li>
      </ul>
      <div class="period-picker-custom">
        <span class="period-picker-custom-title">Custom (local dates)</span>
        <div class="period-picker-custom-row">
          <label class="period-picker-date-label">
            From
            <input v-model="draftFrom" type="date" class="period-picker-date" />
          </label>
          <label class="period-picker-date-label">
            To
            <input v-model="draftTo" type="date" class="period-picker-date" />
          </label>
        </div>
        <button type="button" class="period-picker-apply" @click="applyCustom">Apply custom</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.period-picker {
  position: relative;
  min-width: 0;
  max-width: 100%;
}

.period-picker-trigger {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  max-width: 100%;
  padding: 0.35rem 0.5rem;
  font-size: 0.8125rem;
  color: #a8a8c0;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid #2a2a3e;
  border-radius: 8px;
  cursor: pointer;
  font: inherit;
}

.period-picker-trigger:hover {
  color: #c8c8e0;
  border-color: #3a3a4e;
}

.period-picker-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.period-picker-chevron {
  flex-shrink: 0;
  font-size: 0.625rem;
  opacity: 0.7;
}

.period-picker-panel {
  position: absolute;
  top: calc(100% + 6px);
  left: 50%;
  transform: translateX(-50%);
  z-index: 50;
  min-width: min(280px, 92vw);
  padding: 0.65rem 0.75rem;
  background: #16162a;
  border: 1px solid #2a2a3e;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
}

.period-picker-hint {
  margin: 0 0 0.5rem;
  font-size: 0.75rem;
  color: #6a6a80;
  line-height: 1.35;
}

.period-picker-presets {
  list-style: none;
  margin: 0;
  padding: 0;
}

.period-picker-presets li {
  margin: 0;
}

.period-picker-option {
  display: block;
  width: 100%;
  text-align: left;
  padding: 0.4rem 0.35rem;
  margin: 0;
  font-size: 0.875rem;
  color: #e0e0f0;
  background: none;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font: inherit;
}

.period-picker-option:hover {
  background: rgba(255, 255, 255, 0.06);
}

.period-picker-custom {
  margin-top: 0.65rem;
  padding-top: 0.65rem;
  border-top: 1px solid #2a2a3e;
}

.period-picker-custom-title {
  display: block;
  font-size: 0.75rem;
  color: #6a6a80;
  margin-bottom: 0.4rem;
}

.period-picker-custom-row {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.period-picker-date-label {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  font-size: 0.75rem;
  color: #8888a0;
}

.period-picker-date {
  padding: 0.3rem 0.35rem;
  font-size: 0.8125rem;
  color: #e8e8f0;
  background: #0f0f1a;
  border: 1px solid #2a2a3e;
  border-radius: 6px;
}

.period-picker-apply {
  margin-top: 0.5rem;
  padding: 0.4rem 0.65rem;
  font-size: 0.8125rem;
  color: #e8e8f0;
  background: rgba(74, 110, 219, 0.35);
  border: 1px solid #4a6edb;
  border-radius: 6px;
  cursor: pointer;
  font: inherit;
}

.period-picker-apply:hover {
  background: rgba(74, 110, 219, 0.5);
}
</style>
