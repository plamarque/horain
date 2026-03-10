<script setup lang="ts">
import LogEntriesBubble from './LogEntriesBubble.vue'
import type { TimeLogEntry } from '../types'

defineProps<{
  entries: TimeLogEntry[]
  title?: string
}>()

const emit = defineEmits<{
  selectEntry: [entry: TimeLogEntry]
  editEntry: [entry: TimeLogEntry]
  editProject: [entry: TimeLogEntry]
}>()
</script>

<template>
  <div class="log-entries-block">
    <p v-if="title" class="log-entries-block-title">{{ title }}</p>
    <LogEntriesBubble
      :entries="entries"
      @select-entry="emit('selectEntry', $event)"
      @edit-entry="emit('editEntry', $event)"
      @edit-project="emit('editProject', $event)"
    />
  </div>
</template>

<style scoped>
.log-entries-block {
  width: 100%;
  align-self: stretch;
  display: flex;
  flex-direction: column;
  gap: 0;
}

.log-entries-block-title {
  margin: 0 0 1rem;
  font-weight: 600;
  color: #a0a0c0;
  font-size: 0.9rem;
}
</style>
