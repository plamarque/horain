<script setup lang="ts">
import { ref, watch } from 'vue'
import { getProjects, updateTimeLog } from '../services/apiClient'
import type { TimeLogEntry } from '../types'
import type { ProjectDto } from '../services/apiClient'

const props = defineProps<{
  entry: TimeLogEntry
}>()

const emit = defineEmits<{
  close: []
  saved: []
}>()

const projects = ref<ProjectDto[]>([])
const projectId = ref('')
const durationMinutes = ref(0)
const note = ref('')
const loggedAt = ref('')
const saving = ref(false)
const error = ref('')

async function loadProjects() {
  try {
    projects.value = await getProjects()
  } catch {
    projects.value = []
  }
}

watch(
  () => props.entry,
  (entry) => {
    if (entry) {
      projectId.value = entry.projectId || ''
      durationMinutes.value = entry.durationMinutes || 0
      note.value = entry.note || ''
      loggedAt.value = entry.loggedAt
        ? new Date(entry.loggedAt).toISOString().slice(0, 16)
        : new Date().toISOString().slice(0, 16)
      error.value = ''
      loadProjects()
    }
  },
  { immediate: true }
)

function formatLoggedAtForApi(val: string): string {
  const d = new Date(val)
  return d.toISOString()
}

async function save() {
  if (!props.entry?.id) return
  saving.value = true
  error.value = ''
  try {
    const patch: Parameters<typeof updateTimeLog>[1] = {
      durationMinutes: durationMinutes.value,
      note: note.value,
      loggedAt: formatLoggedAtForApi(loggedAt.value),
    }
    if (projectId.value) patch.projectId = projectId.value
    await updateTimeLog(props.entry.id, patch)
    emit('saved')
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Update failed'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal">
      <h3 class="modal-title">Edit entry</h3>
      <form class="modal-form" @submit.prevent="save">
        <div class="form-row">
          <label for="edit-project">Project</label>
          <select id="edit-project" v-model="projectId" class="form-input">
            <option value="">—</option>
            <option v-for="p in projects" :key="p.id" :value="p.id">
              {{ p.name }}
            </option>
          </select>
        </div>
        <div class="form-row">
          <label for="edit-duration">Duration (minutes)</label>
          <input
            id="edit-duration"
            v-model.number="durationMinutes"
            type="number"
            min="1"
            class="form-input"
            required
          />
        </div>
        <div class="form-row">
          <label for="edit-note">Note</label>
          <input
            id="edit-note"
            v-model="note"
            type="text"
            class="form-input"
            placeholder="Optional"
          />
        </div>
        <div class="form-row">
          <label for="edit-logged-at">Date & time</label>
          <input
            id="edit-logged-at"
            v-model="loggedAt"
            type="datetime-local"
            class="form-input"
          />
        </div>
        <p v-if="error" class="form-error">{{ error }}</p>
        <div class="modal-actions">
          <button type="button" class="btn btn-secondary" @click="emit('close')">
            Cancel
          </button>
          <button type="submit" class="btn btn-primary" :disabled="saving">
            {{ saving ? 'Saving...' : 'Save' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  background: #1a1a2e;
  border: 1px solid #2a2a3e;
  border-radius: 12px;
  padding: 1.25rem;
  min-width: 320px;
  max-width: 90vw;
}

.modal-title {
  margin: 0 0 1rem;
  font-size: 1rem;
  color: #e8e8f0;
}

.modal-form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.form-row {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.form-row label {
  font-size: 0.8rem;
  color: #8888a0;
}

.form-input {
  padding: 0.5rem 0.75rem;
  font-size: 0.9rem;
  background: #0f0f1a;
  border: 1px solid #2a2a3e;
  border-radius: 6px;
  color: #e8e8f0;
}

.form-input:focus {
  outline: none;
  border-color: #4a6edb;
}

.form-error {
  margin: 0;
  font-size: 0.85rem;
  color: #e57373;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 0.5rem;
}

.btn {
  padding: 0.5rem 1rem;
  font-size: 0.9rem;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}

.btn-secondary {
  background: #2a2a3e;
  color: #e8e8f0;
}

.btn-secondary:hover {
  background: #3a3a4e;
}

.btn-primary {
  background: #4a6edb;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #5a7eeb;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
