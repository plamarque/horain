<script setup lang="ts">
import { ref, computed } from 'vue'
import type { TimeLogEntry } from '../types'

const INITIAL_SHOWN = 6

const props = defineProps<{
  entries: TimeLogEntry[]
}>()

const emit = defineEmits<{
  selectEntry: [entry: TimeLogEntry]
  editEntry: [entry: TimeLogEntry]
}>()

function onRowClick(entry: TimeLogEntry, e: MouseEvent) {
  if (e.detail === 2) {
    emit('editEntry', entry)
  } else {
    emit('selectEntry', entry)
  }
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
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return iso
  }
}

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
    <div class="log-table-wrapper">
      <table class="log-table">
        <thead>
          <tr>
            <th>Date</th>
            <th>Projet</th>
            <th>Durée</th>
            <th>Note</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(entry, i) in displayedEntries"
            :key="entry.id ?? i"
            class="log-row log-row--clickable"
            @click="onRowClick(entry, $event)"
          >
            <td class="log-date">{{ formatLoggedAt(entry.loggedAt) }}</td>
            <td class="log-project">{{ entry.projectName || '—' }}</td>
            <td class="log-duration">{{ formatDuration(entry.durationMinutes) }}</td>
            <td class="log-note">{{ entry.note || '—' }}</td>
          </tr>
        </tbody>
      </table>
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

.log-table-wrapper {
  overflow-x: auto;
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

.log-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
  min-width: 320px;
}

.log-table th,
.log-table td {
  padding: 0.5rem 0.75rem;
  text-align: left;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.log-table th {
  color: #8888a0;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  font-size: 0.7rem;
}

.log-table tbody tr:last-child td {
  border-bottom: none;
}

.log-row:hover {
  background: rgba(255, 255, 255, 0.03);
}

.log-row--clickable {
  cursor: pointer;
}

.log-date {
  white-space: nowrap;
  color: #a0a0c0;
}

.log-project {
  color: #e8e8f0;
  font-weight: 500;
}

.log-duration {
  color: #7cb342;
  font-variant-numeric: tabular-nums;
}

.log-note {
  color: #8888a0;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 520px) {
  .log-table {
    font-size: 0.8rem;
  }

  .log-table th,
  .log-table td {
    padding: 0.4rem 0.5rem;
  }

  .log-note {
    max-width: 100px;
  }
}
</style>
