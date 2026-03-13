<script setup lang="ts">
import { ref, computed } from 'vue'
import type { TimeLogEntry } from '../types'

const INITIAL_SHOWN = 6

// Stable palette for project-based card background (readable with white text)
const PROJECT_COLORS = [
  '#4a6edb',
  '#5a8a4a',
  '#c9a227',
  '#c45c3a',
  '#7b5fa2',
  '#00838f',
]

function projectColor(entry: TimeLogEntry): string {
  const key = entry.projectId ?? entry.projectName ?? ''
  if (!key) return 'rgba(0,0,0,0.25)'
  let h = 0
  for (let i = 0; i < key.length; i++) h = (h << 5) - h + key.charCodeAt(i)
  const idx = Math.abs(h) % PROJECT_COLORS.length
  return PROJECT_COLORS[idx]
}

const props = defineProps<{
  entries: TimeLogEntry[]
}>()

const emit = defineEmits<{
  selectEntry: [entry: TimeLogEntry]
  editEntry: [entry: TimeLogEntry]
  editProject: [entry: TimeLogEntry]
}>()

const flippedIds = ref<Set<string>>(new Set())

function toggleFlip(entry: TimeLogEntry) {
  const id = entry.id ?? ''
  if (!id) return
  const next = new Set(flippedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  flippedIds.value = next
}

function isFlipped(entry: TimeLogEntry): boolean {
  const id = entry.id ?? ''
  return id ? flippedIds.value.has(id) : false
}

function onCardClick(entry: TimeLogEntry, e: MouseEvent) {
  if (e.detail === 2) {
    emit('editEntry', entry)
    return
  }
  toggleFlip(entry)
  emit('selectEntry', entry)
}

function onProjectDblClick(entry: TimeLogEntry, e: MouseEvent) {
  e.stopPropagation()
  emit('editProject', entry)
}

const expanded = ref(false)

function formatDuration(minutes: number): string {
  if (minutes < 60) return `${minutes} min`
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return m > 0 ? `${h}h ${m}min` : `${h}h`
}

function formatLoggedAt(iso: string): string {
  try {
    const d = new Date(iso)
    return d.toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
    })
  } catch {
    return iso
  }
}

// Chronological: oldest first (top), most recent last (bottom)
const sortedEntries = computed(() =>
  [...props.entries].sort(
    (a, b) => new Date(a.loggedAt).getTime() - new Date(b.loggedAt).getTime()
  )
)

const displayedEntries = computed(() => {
  const all = sortedEntries.value
  if (expanded.value || all.length <= INITIAL_SHOWN) return all
  return all.slice(0, INITIAL_SHOWN)
})

const hasMore = computed(
  () => sortedEntries.value.length > INITIAL_SHOWN && !expanded.value
)

const moreCount = computed(
  () => sortedEntries.value.length - INITIAL_SHOWN
)
</script>

<template>
  <div class="log-entries-bubble">
    <div class="log-cards">
      <div
        v-for="(entry, i) in displayedEntries"
        :key="entry.id ?? i"
        class="card-wrapper"
        :class="{ 'card-wrapper--flipped': isFlipped(entry) }"
        @click="onCardClick(entry, $event)"
        @dblclick="emit('editEntry', entry)"
      >
        <div class="card-inner">
          <div
            class="card-face card-recto"
            :style="{ backgroundColor: projectColor(entry) }"
          >
            <span class="card-date">{{ formatLoggedAt(entry.loggedAt) }}</span>
            <span class="card-duration">{{ formatDuration(entry.durationMinutes) }}</span>
            <span
              class="card-project"
              title="Double-click to edit project"
              @dblclick.stop="onProjectDblClick(entry, $event)"
            >
              {{ entry.projectName || '—' }}
            </span>
          </div>
          <div class="card-face card-verso">
            <span v-if="entry.billable !== false" class="card-billable-icon" aria-hidden="true">$</span>
            <p v-if="entry.note" class="card-note">{{ entry.note }}</p>
            <p v-else class="card-note card-note--empty">—</p>
          </div>
        </div>
      </div>
    </div>
    <button
      v-if="hasMore"
      type="button"
      class="show-more"
      @click="expanded = true"
    >
      +{{ moreCount }} more
    </button>
  </div>
</template>

<style scoped>
.log-entries-bubble {
  margin-top: 0.75rem;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.2);
}

.log-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  padding: 0.5rem;
}

.card-wrapper {
  flex: 1 1 140px;
  min-width: 120px;
  max-width: 200px;
  aspect-ratio: 3 / 4;
  cursor: pointer;
  perspective: 600px;
}

.card-inner {
  position: relative;
  width: 100%;
  height: 100%;
  transition: transform 0.4s ease;
  transform-style: preserve-3d;
}

.card-wrapper--flipped .card-inner {
  transform: rotateY(180deg);
}

.card-face {
  position: absolute;
  inset: 0;
  backface-visibility: hidden;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 0.75rem;
  text-align: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}

.card-recto {
  color: rgba(255, 255, 255, 0.95);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
}

.card-date {
  font-size: 0.75rem;
  opacity: 0.9;
  margin-bottom: 0.25rem;
}

.card-duration {
  font-size: 1.1rem;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  margin-bottom: 0.35rem;
}

.card-project {
  font-size: 0.85rem;
  font-weight: 500;
  line-height: 1.2;
  word-break: break-word;
}

.card-verso {
  background: rgba(30, 30, 45, 0.98);
  color: #e8e8f0;
  transform: rotateY(180deg);
}

.card-billable-icon {
  font-size: 1.25rem;
  font-weight: 700;
  color: #7cb342;
  margin-bottom: 0.5rem;
}

.card-note {
  margin: 0;
  font-size: 0.8rem;
  line-height: 1.35;
  word-break: break-word;
  overflow: auto;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-note--empty {
  color: #8888a0;
}

.show-more {
  display: block;
  width: 100%;
  padding: 0.4rem 0.75rem;
  margin-top: 0.25rem;
  font-size: 0.8rem;
  color: #7cb342;
  background: transparent;
  border: none;
  cursor: pointer;
  text-align: center;
  transition: color 0.15s;
}

.show-more:hover {
  color: #8bc34a;
}

@media (max-width: 520px) {
  .log-cards {
    gap: 0.5rem;
    padding: 0.4rem;
  }

  .card-wrapper {
    flex: 1 1 100px;
    min-width: 100px;
    max-width: none;
  }

  .card-face {
    padding: 0.5rem;
  }

  .card-date {
    font-size: 0.7rem;
  }

  .card-duration {
    font-size: 1rem;
  }

  .card-project {
    font-size: 0.8rem;
  }

  .card-note {
    font-size: 0.75rem;
  }
}
</style>
