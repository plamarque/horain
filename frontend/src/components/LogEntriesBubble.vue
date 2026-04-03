<script setup lang="ts">
import { ref, computed, watch, nextTick, onUnmounted } from 'vue'
import type { TimeLogEntry } from '../types'
import { projectCardBackgroundColor } from '../utils/projectCardColor'

const INITIAL_SHOWN = 20
const LONG_PRESS_MS = 500

function projectColor(entry: TimeLogEntry): string {
  return projectCardBackgroundColor(
    entry.projectId,
    entry.projectName,
    entry.projectCardColorIndex ?? null
  )
}

/** Entry value in euros when billable and activity type has daily rate (TJM 8h). */
function entryAmountEur(entry: TimeLogEntry): number | null {
  if (entry.billable === false || entry.durationMinutes == null) return null
  const cents = entry.dailyRateCents
  if (cents == null || cents <= 0) return null
  const eur = (entry.durationMinutes / 480) * (cents / 100)
  return eur
}

function formatAmountEur(amount: number): string {
  return Number.isInteger(amount) ? `${amount} €` : `${amount.toFixed(1)} €`
}

const props = defineProps<{
  entries: TimeLogEntry[]
}>()

const emit = defineEmits<{
  selectEntry: [entry: TimeLogEntry]
  editEntry: [entry: TimeLogEntry]
  editProject: [entry: TimeLogEntry]
}>()

const expandedIds = ref<Set<string>>(new Set())

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

function toggleExpand(entry: TimeLogEntry) {
  const id = entry.id ?? ''
  if (!id) return
  const next = new Set(expandedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expandedIds.value = next
}

function isExpanded(entry: TimeLogEntry): boolean {
  const id = entry.id ?? ''
  return id ? expandedIds.value.has(id) : false
}

function onCardClick(entry: TimeLogEntry, e: MouseEvent) {
  if (longPressSuppressClick.value) return
  if (e.detail === 2) {
    emit('editEntry', entry)
    return
  }
  toggleExpand(entry)
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
// Reverse chronological: most recent first (top-left), then descending
const sortedEntries = computed(() =>
  [...props.entries].sort(
    (a, b) => new Date(b.loggedAt).getTime() - new Date(a.loggedAt).getTime()
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
        :class="{ 'card-wrapper--expanded': isExpanded(entry) }"
        @click="onCardClick(entry, $event)"
        @dblclick="emit('editEntry', entry)"
        @contextmenu.prevent="onContextMenu(entry, $event)"
        @touchstart.passive="onTouchStart(entry, $event)"
        @touchmove="onTouchMove"
        @touchend="onTouchEnd"
        @touchcancel="onTouchEnd"
      >
        <div
          class="card-face"
          :style="{ backgroundColor: projectColor(entry) }"
        >
          <button
            v-if="isExpanded(entry)"
            type="button"
            class="card-edit"
            aria-label="Edit entry"
            title="Edit entry"
            @click.stop="emit('editEntry', entry)"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
              <path d="m15 5 4 4" />
            </svg>
          </button>
          <!-- Collapsed: top row = date (left) + activity code (right); then duration. Expanded: meta row, project+amount row, note fills rest -->
          <div class="card-row card-row--meta">
            <div class="card-meta-top">
              <span class="card-date">{{ formatLoggedAt(entry.loggedAt) }}</span>
              <span
                v-if="entry.activityTypeCode"
                class="card-tag"
                :title="entry.activityTypeLabel || entry.activityTypeCode"
              >
                {{ entry.activityTypeCode }}
              </span>
            </div>
            <span class="card-duration">{{ formatDuration(entry.durationMinutes) }}</span>
          </div>
          <div class="card-row card-row--project">
            <span
              class="card-project"
              title="Double-click to edit project"
              @click.stop
              @dblclick.stop="onProjectDblClick(entry, $event)"
            >
              {{ entry.projectName || '—' }}
            </span>
            <span v-if="entry.billable !== false" class="card-amount">
              <span v-if="entryAmountEur(entry) != null" class="card-amount-value">{{ formatAmountEur(entryAmountEur(entry)!) }}</span>
              <span v-else class="card-amount-icon" aria-hidden="true">€</span>
            </span>
          </div>
          <p class="card-note" :title="entry.note || undefined">
            {{ entry.note || '—' }}
          </p>
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
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  grid-auto-rows: minmax(180px, auto);
  grid-auto-flow: row;
  gap: 0.75rem;
  padding: 0.5rem;
}

.card-wrapper {
  min-width: 0;
  min-height: 0;
  aspect-ratio: 3 / 4;
  cursor: pointer;
  transition: grid-column 0.3s ease, grid-row 0.3s ease;
}

/* Expanded card: 2 columns × 1 row; height follows content (fits 1–2 sentences), long notes scroll */
.card-wrapper--expanded {
  grid-column: span 2;
  grid-row: span 1;
  aspect-ratio: auto;
  min-height: 0;
  align-self: start;
}

.card-face {
  position: relative;
  width: 100%;
  height: 100%;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  align-items: center;
  padding: 0.65rem 0.85rem 0.85rem;
  text-align: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  color: rgba(255, 255, 255, 0.95);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
}

/* Expanded: compact grid; note row auto-sized so short notes don’t leave empty space */
.card-wrapper--expanded .card-face {
  display: grid;
  grid-template-rows: auto auto auto auto auto;
  grid-template-columns: auto 1fr;
  row-gap: 0.6rem;
  column-gap: 0.75rem;
  justify-content: stretch;
  align-content: start;
  justify-items: start;
  align-items: start;
  text-align: left;
  padding: 0.75rem 0.85rem 0.85rem;
  padding-right: 2.5rem;
}

/* Flatten structure so date, tag, duration, project, amount, note become direct grid children */
.card-wrapper--expanded .card-row--meta,
.card-wrapper--expanded .card-meta-top,
.card-wrapper--expanded .card-row--project {
  display: contents;
}

.card-wrapper--expanded .card-date {
  grid-row: 1;
  grid-column: 1 / -1;
  margin-bottom: 0.1rem;
}

.card-wrapper--expanded .card-project {
  grid-row: 2;
  grid-column: 1 / -1;
  font-size: 1.25rem;
  font-weight: 700;
}

.card-wrapper--expanded .card-duration {
  grid-row: 3;
  grid-column: 1;
  margin-right: 0.35rem;
}

.card-wrapper--expanded .card-amount {
  grid-row: 3;
  grid-column: 2;
  align-self: center;
}

/* Tag and note full width so no empty column left of text */
.card-wrapper--expanded .card-tag {
  grid-row: 4;
  grid-column: 1 / -1;
  align-self: start;
}

.card-wrapper--expanded .card-note {
  grid-row: 5;
  grid-column: 1 / -1;
  margin-top: 0.1rem;
  margin-bottom: 0;
}

.card-edit {
  position: absolute;
  top: 0.35rem;
  right: 0.35rem;
  width: 26px;
  height: 26px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.25);
  border: none;
  border-radius: 6px;
  color: rgba(255, 255, 255, 0.85);
  cursor: pointer;
}

.card-edit:hover {
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
}

/* Collapsed: vertical stack (meta top row, duration, note) */
.card-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem 0.5rem;
}

.card-row--meta {
  margin-bottom: 0.4rem;
}

/* Top line: date top-left, activity code top-right */
.card-meta-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  width: 100%;
  gap: 0.35rem;
  min-width: 0;
}

.card-wrapper:not(.card-wrapper--expanded) .card-row--meta {
  flex-direction: column;
  align-items: stretch;
  align-self: stretch;
  width: 100%;
  gap: 0.35rem;
  margin-bottom: 0.5rem;
}

.card-wrapper:not(.card-wrapper--expanded) .card-row--project {
  display: none;
}

.card-date {
  font-size: 0.9375rem;
  opacity: 0.9;
  flex-shrink: 0;
}

.card-tag {
  display: inline-block;
  font-size: 0.75rem;
  font-weight: 600;
  padding: 0.2rem 0.5rem;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.25);
  color: rgba(255, 255, 255, 0.98);
  text-transform: uppercase;
  letter-spacing: 0.02em;
  flex-shrink: 0;
}

.card-duration {
  font-size: 1.5rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
}

.card-wrapper:not(.card-wrapper--expanded) .card-duration {
  margin-top: 0.65rem;
  margin-bottom: 0.35rem;
}

.card-wrapper--expanded .card-duration {
  font-size: 1.125rem;
  margin-right: 0.5rem;
}

.card-sep {
  opacity: 0.7;
  font-weight: 600;
  user-select: none;
}

.card-project {
  font-size: 1.0625rem;
  font-weight: 500;
  line-height: 1.4;
  word-break: break-word;
  flex: 1;
  min-width: 0;
}

.card-wrapper--expanded .card-project {
  font-size: 1.25rem;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.card-amount {
  display: inline-flex;
  align-items: baseline;
  gap: 0.2rem;
  flex-shrink: 0;
}

.card-amount-value {
  font-size: 1.125rem;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.95);
}


.card-amount-icon {
  font-size: 1.25rem;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.9);
}

.card-note {
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

.card-wrapper--expanded .card-note {
  -webkit-line-clamp: unset;
  line-clamp: unset;
  font-size: 1.25rem;
  line-height: 1.5;
  margin: 0;
  margin-top: 0.25rem;
  align-self: stretch;
  min-height: 0;
  max-height: 14em;
  overflow-y: auto;
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
    grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
    grid-auto-rows: minmax(140px, auto);
    gap: 0.5rem;
    padding: 0.4rem;
  }

  /* Expanded card 2 cols × 1 row on mobile too */
  .card-wrapper--expanded {
    grid-column: span 2;
    grid-row: span 1;
  }

  .card-face {
    padding: 0.6rem;
    padding-top: 2rem;
  }

  .card-wrapper--expanded .card-face {
    padding: 0.5rem 0.6rem;
    padding-right: 2.25rem;
  }

  .card-date {
    font-size: 0.875rem;
  }

  .card-duration {
    font-size: 1.25rem;
  }

  .card-tag {
    font-size: 0.6875rem;
    padding: 0.15rem 0.4rem;
  }

  .card-project {
    font-size: 1rem;
    line-height: 1.4;
  }

  .card-note {
    font-size: 0.9375rem;
  }
}
</style>
