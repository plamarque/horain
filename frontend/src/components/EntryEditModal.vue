<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { getProjects, getActivityTypes, updateTimeLog, deleteTimeLog, isValidTimeLogId } from '../services/apiClient'
import type { TimeLogEntry } from '../types'
import type { ProjectDto, ActivityTypeDto } from '../services/apiClient'

const props = defineProps<{
  entry: TimeLogEntry
}>()

const emit = defineEmits<{
  close: []
  saved: [patch: Partial<TimeLogEntry> & { id: string }]
  deleted: [entry: TimeLogEntry]
}>()

const projects = ref<ProjectDto[]>([])
const activityTypes = ref<ActivityTypeDto[]>([])
/** Set when GET /activity-types fails (e.g. network, CORS, API key); avoids a silent empty nature dropdown. */
const activityTypesLoadError = ref('')
const projectId = ref('')
const activityTypeCode = ref('')
const durationMinutes = ref(0)
const note = ref('')
const billable = ref(true)
const loggedAt = ref('')
const saving = ref(false)
const deleting = ref(false)
const confirmDelete = ref(false)
const error = ref('')

const canEditOrDelete = computed(() => isValidTimeLogId(props.entry?.id ?? ''))

async function loadProjects() {
  try {
    projects.value = await getProjects()
  } catch {
    projects.value = []
  }
}

async function loadActivityTypes() {
  activityTypesLoadError.value = ''
  try {
    activityTypes.value = await getActivityTypes()
  } catch (e) {
    activityTypes.value = []
    activityTypesLoadError.value =
      e instanceof Error ? e.message : 'Could not load activity types. Check API URL, key, and CORS.'
  }
}

watch(
  () => props.entry,
  async (entry) => {
    if (entry) {
      projectId.value = entry.projectId || ''
      activityTypeCode.value = entry.activityTypeCode || ''
      durationMinutes.value = entry.durationMinutes || 0
      note.value = entry.note || ''
      billable.value = entry.billable !== false
      loggedAt.value = entry.loggedAt
        ? new Date(entry.loggedAt).toISOString().slice(0, 10)
        : new Date().toISOString().slice(0, 10)
      error.value = ''
      confirmDelete.value = false
      await loadProjects()
      await loadActivityTypes()
      if (projects.value.length && entry.projectName) {
        const currentId = projectId.value
        const idInList = currentId && projects.value.some((p) => p.id === currentId)
        if (!idInList) {
          const byName = projects.value.find(
            (p) => p.name === entry.projectName || (entry.projectName != null && p.name.toLowerCase() === entry.projectName.toLowerCase())
          )
          if (byName) projectId.value = byName.id
        }
      }
    }
  },
  { immediate: true }
)

/** Sends date-only (YYYY-MM-DD) as noon UTC so the calendar day is preserved. */
function formatLoggedAtForApi(val: string): string {
  return `${val}T12:00:00.000Z`
}

async function save() {
  if (!props.entry?.id) return
  if (!isValidTimeLogId(props.entry.id)) {
    error.value = 'This entry cannot be edited (invalid id from conversation).'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const patch: Parameters<typeof updateTimeLog>[1] = {
      durationMinutes: durationMinutes.value,
      note: note.value,
      billable: billable.value,
      loggedAt: formatLoggedAtForApi(loggedAt.value),
      activityTypeCode: activityTypeCode.value || null,
    }
    if (projectId.value) patch.projectId = projectId.value
    const updated = await updateTimeLog(props.entry.id, patch)
    const savedPatch: Partial<TimeLogEntry> & { id: string } = {
      id: props.entry.id,
      durationMinutes: updated.durationMinutes,
      note: updated.note,
      billable: updated.billable,
      loggedAt: updated.loggedAt,
      projectId: updated.projectId,
      activityTypeCode: updated.activityTypeCode ?? undefined,
      activityTypeLabel: updated.activityTypeLabel,
      dailyRateCents: updated.dailyRateCents,
    }
    emit('saved', savedPatch)
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Update failed'
  } finally {
    saving.value = false
  }
}

function cancelDelete() {
  confirmDelete.value = false
}

async function doDelete() {
  if (!props.entry?.id) return
  if (!isValidTimeLogId(props.entry.id)) {
    error.value = 'This entry cannot be deleted (invalid id from conversation).'
    return
  }
  deleting.value = true
  error.value = ''
  try {
    await deleteTimeLog(props.entry.id)
    emit('deleted', props.entry)
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Delete failed'
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <div class="entry-edit-screen" role="dialog" aria-labelledby="entry-edit-title">
    <header class="entry-edit-header">
      <button
        type="button"
        class="entry-edit-back"
        aria-label="Back"
        @click="emit('close')"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="m12 19-7-7 7-7" />
        </svg>
      </button>
      <h2 id="entry-edit-title" class="entry-edit-title">Edit entry</h2>
    </header>
    <div class="entry-edit-body">
      <p v-if="!canEditOrDelete" class="form-error form-error--banner">
        This entry cannot be edited or deleted (invalid id). It may come from a conversation display bug.
      </p>
      <form class="entry-edit-form" @submit.prevent="save">
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
          <label for="edit-activity-type">Nature d'activité</label>
          <p v-if="activityTypesLoadError" class="form-error form-error--inline" role="alert">
            {{ activityTypesLoadError }}
          </p>
          <select id="edit-activity-type" v-model="activityTypeCode" class="form-input">
            <option value="">—</option>
            <option
              v-for="a in activityTypes"
              :key="a.code"
              :value="a.code"
              :title="a.description || a.label"
            >
              {{ a.label }} ({{ (a.dailyRateCents / 100).toFixed(0) }} €/j)
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
        <div class="form-row form-row--checkbox">
          <label class="checkbox-label">
            <input
              id="edit-billable"
              v-model="billable"
              type="checkbox"
              class="form-checkbox"
            />
            <span>Facturable</span>
          </label>
        </div>
        <div class="form-row">
          <label for="edit-logged-at">Date</label>
          <input
            id="edit-logged-at"
            v-model="loggedAt"
            type="date"
            class="form-input"
          />
        </div>
        <p v-if="error" class="form-error">{{ error }}</p>
        <div v-if="confirmDelete" class="entry-edit-actions entry-edit-actions--confirm">
          <p class="confirm-text">Delete this entry permanently?</p>
          <div class="entry-edit-actions">
            <button type="button" class="btn btn-secondary" @click="cancelDelete">
              Cancel
            </button>
            <button
              type="button"
              class="btn btn-danger"
              :disabled="deleting"
              @click="doDelete"
            >
              {{ deleting ? 'Deleting...' : 'Delete' }}
            </button>
          </div>
        </div>
        <div v-else class="entry-edit-actions">
          <button
            v-if="entry?.id && canEditOrDelete"
            type="button"
            class="btn btn-danger"
            @click="confirmDelete = true"
          >
            Delete
          </button>
          <button type="button" class="btn btn-secondary" @click="emit('close')">
            Cancel
          </button>
          <button type="submit" class="btn btn-primary" :disabled="saving || !canEditOrDelete">
            {{ saving ? 'Saving...' : 'Save' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.entry-edit-screen {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: #0f0f1a;
  display: flex;
  flex-direction: column;
  padding-left: env(safe-area-inset-left, 0);
  padding-right: env(safe-area-inset-right, 0);
  padding-bottom: env(safe-area-inset-bottom, 0);
}

.entry-edit-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem 1.25rem;
  border-bottom: 1px solid #2a2a3e;
  background: #1a1a2e;
}

.entry-edit-back {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  padding: 0;
  border: none;
  background: transparent;
  color: #e8e8f0;
  cursor: pointer;
  border-radius: 8px;
}

.entry-edit-back:hover {
  background: rgba(255, 255, 255, 0.08);
}

.entry-edit-title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: #e8e8f0;
}

.entry-edit-body {
  flex: 1;
  overflow: auto;
  padding: 1.25rem;
  max-width: 540px;
  margin: 0 auto;
  width: 100%;
}

.entry-edit-form {
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

.form-error--banner {
  margin-bottom: 0.75rem;
  padding: 0.5rem 0.75rem;
  background: rgba(229, 115, 115, 0.12);
  border-radius: 6px;
}

.form-error--inline {
  margin-bottom: 0.35rem;
  font-size: 0.95rem;
}

.entry-edit-actions {
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

.btn-danger {
  background: #8b2635;
  color: white;
  margin-right: auto;
}

.entry-edit-actions--confirm {
  flex-direction: column;
  align-items: stretch;
  gap: 0.75rem;
}

.btn-danger:hover:not(:disabled) {
  background: #a52f42;
}

.btn-danger:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.confirm-text {
  margin: 0;
  font-size: 1.125rem;
  color: #e8e8f0;
}
</style>
