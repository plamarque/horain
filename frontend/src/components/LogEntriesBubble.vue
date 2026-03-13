<script setup lang="ts">
import { ref, computed, watch, nextTick, onUnmounted } from 'vue'
import type { TimeLogEntry } from '../types'

const INITIAL_SHOWN = 8
const LONG_PRESS_MS = 500

// Stable palette for project-based card background (readable with white text)
const PROJECT_COLORS = [
  '#4a6edb',
  '#5a8a4a',
  '#c9a227',
  '#c45c3a',
  '#7b5fa2',
  '#00838f',
  '#b91c1c',
  '#047857',
  '#b45309',
  '#6d28d9',
  '#be185d',
  '#0e7490',
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

// Context menu (right-click or long-press)
const contextMenuEntry = ref<TimeLogEntry | null>(null)
const contextMenuPosition = ref<{ x: number; y: number }>({ x: 0, y: 0 })
const contextMenuVisible = ref(false)
const contextMenuRef = ref<HTMLElement | null>(null)
let closeContextMenuOnDocClick: ((e: Event) => void) | null = null

// Long-press: suppress the following click so we don't flip/select after opening menu
const longPressSuppressClick = ref(false)
let longPressTimer: ReturnType<typeof setTimeout> | null = null
let longPressTouchEntry: TimeLogEntry | null = null
const lastTouchPosition = ref<{ x: number; y: number }>({ x: 0, y: 0 })

function openContextMenu(entry: TimeLogEntry, x: number, y: number) {
  contextMenuEntry.value = entry
  contextMenuPosition.value = { x, y }
  contextMenuVisible.value = true
  // Close on document click only when the click is outside the context menu (so clicking a menuitem still works)
  closeContextMenuOnDocClick = (e: Event) => {
    const menuEl = contextMenuRef.value
    const target = e.target as Node
    if (menuEl && (e.target === menuEl || menuEl.contains(target))) {
      return
    }
    closeContextMenu()
    document.removeEventListener('click', closeContextMenuOnDocClick!, true)
    closeContextMenuOnDocClick = null
  }
  document.addEventListener('click', closeContextMenuOnDocClick, true)
}

function closeContextMenu() {
  contextMenuVisible.value = false
  contextMenuEntry.value = null
  if (closeContextMenuOnDocClick) {
    document.removeEventListener('click', closeContextMenuOnDocClick, true)
    closeContextMenuOnDocClick = null
  }
}

function onContextMenu(entry: TimeLogEntry, e: MouseEvent) {
  e.preventDefault()
  openContextMenu(entry, e.clientX, e.clientY)
}

function onContextMenuKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') closeContextMenu()
}

function onEditEntryFromMenu() {
  const entry = contextMenuEntry.value
  if (entry) {
    closeContextMenu()
    emit('editEntry', entry)
  }
}

function onEditProjectFromMenu() {
  const entry = contextMenuEntry.value
  if (entry?.projectId) {
    closeContextMenu()
    emit('editProject', entry)
  }
}

function onTouchStart(entry: TimeLogEntry, e: TouchEvent) {
  longPressTouchEntry = entry
  if (e.touches.length > 0) {
    lastTouchPosition.value = { x: e.touches[0].clientX, y: e.touches[0].clientY }
  }
  longPressTimer = setTimeout(() => {
    longPressTimer = null
    if (longPressTouchEntry) {
      const { x, y } = lastTouchPosition.value
      longPressSuppressClick.value = true
      setTimeout(() => { longPressSuppressClick.value = false }, 100)
      openContextMenu(longPressTouchEntry, x, y)
      longPressTouchEntry = null
    }
  }, LONG_PRESS_MS)
}

function onTouchMove() {
  if (longPressTimer) {
    clearTimeout(longPressTimer)
    longPressTimer = null
  }
  longPressTouchEntry = null
}

function onTouchEnd() {
  if (longPressTimer) {
    clearTimeout(longPressTimer)
    longPressTimer = null
  }
  longPressTouchEntry = null
}

watch(contextMenuVisible, (visible) => {
  if (visible) {
    nextTick(() => contextMenuRef.value?.focus())
  }
})

onUnmounted(() => {
  if (longPressTimer) clearTimeout(longPressTimer)
  if (closeContextMenuOnDocClick) {
    document.removeEventListener('click', closeContextMenuOnDocClick, true)
  }
})

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
  if (longPressSuppressClick.value) return
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
        @contextmenu.prevent="onContextMenu(entry, $event)"
        @touchstart.passive="onTouchStart(entry, $event)"
        @touchmove="onTouchMove"
        @touchend="onTouchEnd"
        @touchcancel="onTouchEnd"
      >
        <div class="card-inner">
          <div
            class="card-face card-recto"
            :style="{ backgroundColor: projectColor(entry) }"
          >
            <span class="card-date">{{ formatLoggedAt(entry.loggedAt) }}</span>
            <span class="card-duration">{{ formatDuration(entry.durationMinutes) }}</span>
            <p class="card-recto-note" :title="entry.note || undefined">
              {{ entry.note || '—' }}
            </p>
          </div>
          <div class="card-face card-verso">
            <button
              type="button"
              class="card-verso-edit"
              aria-label="Edit entry"
              title="Edit entry"
              @click.stop="emit('editEntry', entry)"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
                <path d="m15 5 4 4" />
              </svg>
            </button>
            <span
              class="card-verso-project"
              title="Double-click to edit project"
              @dblclick.stop="onProjectDblClick(entry, $event)"
            >
              {{ entry.projectName || '—' }}
            </span>
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
    <!-- Context menu (right-click or long-press): Edit entry / Edit project -->
    <div
      v-if="contextMenuVisible && contextMenuEntry"
      ref="contextMenuRef"
      class="context-menu"
      role="menu"
      tabindex="-1"
      :style="{ left: contextMenuPosition.x + 'px', top: contextMenuPosition.y + 'px' }"
      @keydown.esc="onContextMenuKeydown"
    >
      <button
        type="button"
        role="menuitem"
        class="context-menu-item"
        @click="onEditEntryFromMenu"
      >
        Edit entry
      </button>
      <button
        v-if="contextMenuEntry?.projectId"
        type="button"
        role="menuitem"
        class="context-menu-item"
        @click="onEditProjectFromMenu"
      >
        Edit project
      </button>
    </div>
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
  padding: 0.85rem;
  text-align: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}

.card-recto {
  color: rgba(255, 255, 255, 0.95);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
  justify-content: flex-start;
}

.card-date {
  font-size: 0.9375rem;
  opacity: 0.9;
  margin-bottom: 0.3rem;
}

.card-duration {
  font-size: 1.375rem;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  margin-bottom: 0.55rem;
}

.card-recto-note {
  margin: 0;
  font-size: 1.25rem;
  line-height: 1.35;
  color: rgba(255, 255, 255, 0.9);
  word-break: break-word;
  overflow: hidden;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  flex: 1;
  min-height: 0;
}

.card-project {
  font-size: 1.0625rem;
  font-weight: 500;
  line-height: 1.4;
  word-break: break-word;
}

.card-verso {
  background: rgba(30, 30, 45, 0.98);
  color: #e8e8f0;
  transform: rotateY(180deg);
  position: relative;
}

.card-verso-edit {
  position: absolute;
  top: 0.35rem;
  right: 0.35rem;
  width: 26px;
  height: 26px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.3);
  border: none;
  border-radius: 6px;
  color: #a0a0c0;
  cursor: pointer;
}

.card-verso-edit:hover {
  background: rgba(0, 0, 0, 0.5);
  color: #e8e8f0;
}

.card-verso-project {
  font-size: 1.0625rem;
  font-weight: 600;
  line-height: 1.4;
  word-break: break-word;
  margin-bottom: 0.5rem;
  color: inherit;
}

.card-billable-icon {
  font-size: 1.5625rem;
  font-weight: 700;
  color: #7cb342;
  margin-bottom: 0.5rem;
}

.card-note {
  margin: 0;
  font-size: 1.25rem;
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
  font-size: 1.25rem;
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

.context-menu {
  position: fixed;
  z-index: 1001;
  min-width: 140px;
  padding: 0.25rem 0;
  background: #1a1a2e;
  border: 1px solid #2a2a3e;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
  outline: none;
}

.context-menu-item {
  display: block;
  width: 100%;
  padding: 0.5rem 1rem;
  text-align: left;
  font-size: 1.125rem;
  color: #e8e8f0;
  background: none;
  border: none;
  cursor: pointer;
}

.context-menu-item:hover {
  background: #2a2a3e;
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
    padding: 0.6rem;
  }

  .card-date {
    font-size: 0.875rem;
  }

  .card-duration {
    font-size: 1.25rem;
    margin-bottom: 0.45rem;
  }

  .card-recto-note {
    font-size: 0.9375rem;
    -webkit-line-clamp: 3;
    line-clamp: 3;
  }

  .card-project,
  .card-verso-project {
    font-size: 1.25rem;
    line-height: 1.4;
  }

  .card-note {
    font-size: 0.9375rem;
  }
}
</style>
