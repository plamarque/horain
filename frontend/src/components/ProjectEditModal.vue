<script setup lang="ts">
import { ref, watch } from 'vue'
import { getProjects, updateProject } from '../services/apiClient'

const props = defineProps<{
  projectId: string
}>()

const emit = defineEmits<{
  close: []
  saved: []
}>()

const name = ref('')
const description = ref('')
const billable = ref(true)
const saving = ref(false)
const error = ref('')

async function loadProject() {
  if (!props.projectId) return
  error.value = ''
  try {
    const projects = await getProjects()
    const project = projects.find((p) => p.id === props.projectId)
    if (project) {
      name.value = project.name
      description.value = project.description ?? ''
      billable.value = project.billable !== false
    } else {
      error.value = 'Project not found'
    }
  } catch {
    error.value = 'Failed to load project'
  }
}

watch(
  () => props.projectId,
  (id) => {
    if (id) loadProject()
  },
  { immediate: true }
)

async function save() {
  if (!props.projectId) return
  saving.value = true
  error.value = ''
  try {
    await updateProject(props.projectId, {
      name: name.value.trim(),
      description: description.value || undefined,
      billable: billable.value,
    })
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
      <h3 class="modal-title">Edit project</h3>
      <form class="modal-form" @submit.prevent="save">
        <div class="form-row">
          <label for="edit-project-name">Name</label>
          <input
            id="edit-project-name"
            v-model="name"
            type="text"
            class="form-input"
            required
          />
        </div>
        <div class="form-row">
          <label for="edit-project-description">Description</label>
          <input
            id="edit-project-description"
            v-model="description"
            type="text"
            class="form-input"
            placeholder="Optional"
          />
        </div>
        <div class="form-row form-row--checkbox">
          <label class="checkbox-label">
            <input
              id="edit-project-billable"
              v-model="billable"
              type="checkbox"
              class="form-checkbox"
            />
            <span>Facturable</span>
          </label>
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
  font-size: 1.25rem;
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
  font-size: 1rem;
  color: #8888a0;
}

.form-input {
  padding: 0.5rem 0.75rem;
  font-size: 1.125rem;
  background: #0f0f1a;
  border: 1px solid #2a2a3e;
  border-radius: 6px;
  color: #e8e8f0;
}

.form-input:focus {
  outline: none;
  border-color: #4a6edb;
}

.form-row--checkbox {
  flex-direction: row;
  align-items: center;
}

.checkbox-label {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  cursor: pointer;
  font-size: 1.125rem;
  color: #e8e8f0;
}

.form-checkbox {
  width: 1.1rem;
  height: 1.1rem;
  accent-color: #4a6edb;
}

.form-error {
  margin: 0;
  font-size: 1.0625rem;
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
  font-size: 1.125rem;
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
