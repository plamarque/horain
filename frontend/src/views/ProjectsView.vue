<script setup lang="ts">
import type { Ref } from 'vue'
import { ref, computed, watch, onMounted, onUnmounted, inject, nextTick } from 'vue'
import { getProjects } from '../services/apiClient'
import type { ProjectDto } from '../services/apiClient'
import type { ActivityPeriodCustom, ActivityPeriodPreset } from '../activityPeriod'
import PushToTalkButton from '../components/PushToTalkButton.vue'
import { projectCardBackgroundColor } from '../utils/projectCardColor'

const PULL_THRESHOLD = 72
const PULL_MAX = 100
const PULL_RESISTANCE = 0.5

function projectColor(project: ProjectDto): string {
  return projectCardBackgroundColor(project.id, project.name, project.cardColorIndex ?? null)
}

const openProjectEdit = inject<((projectId: string) => void)>('openProjectEdit')
const selectedProjects = inject<Ref<ProjectDto[]>>('selectedProjects', ref([]))
const addProjectToContext = inject<(project: ProjectDto) => void>('addProjectToContext', () => {})
const removeProjectFromContext = inject<(projectId: string) => void>('removeProjectFromContext', () => {})
const maxContextProjects = inject<number>('MAX_CONTEXT_PROJECTS', 5)
type ConversationApi = {
  submit: (text: string, options?: { clearProjectsAfterSend?: boolean }) => void
  stop: () => void
  isProcessing: Ref<boolean>
}
const conversationApi = inject<Ref<ConversationApi | null>>('conversationApi', ref(null))
const switchToConversationView = inject<() => void>('switchToConversationView', () => {})
const isProcessing = computed(() => conversationApi.value?.isProcessing?.value ?? false)

const getActivityRange = inject<() => { activityFrom: string; activityTo: string }>('getActivityRange', () => ({
  activityFrom: new Date(Date.now() - 28 * 86400000).toISOString(),
  activityTo: new Date().toISOString(),
}))
const activityPeriodPreset = inject<Ref<ActivityPeriodPreset>>('activityPeriodPreset')!
const activityPeriodCustom = inject<Ref<ActivityPeriodCustom>>('activityPeriodCustom')!

const projects = ref<ProjectDto[]>([])
const loading = ref(true)
const error = ref('')
const scrollEl = ref<HTMLDivElement | null>(null)
const pullDistance = ref(0)
const touchStartY = ref<number | null>(null)
const expandedIds = ref<Set<string>>(new Set())

async function loadProjects() {
  loading.value = true
  error.value = ''
  try {
    const range = getActivityRange()
    projects.value = await getProjects({
      activityFrom: range.activityFrom,
      activityTo: range.activityTo,
    })
  } catch {
    error.value = 'Failed to load projects'
    projects.value = []
  } finally {
    loading.value = false
  }
}

function atTop() {
  const el = scrollEl.value
  return el ? el.scrollTop <= 0 : false
}

function onTouchStart(e: TouchEvent) {
  if (e.touches.length !== 1 || loading.value) return
  if (atTop()) touchStartY.value = e.touches[0].clientY
  else touchStartY.value = null
}

function onTouchMove(e: TouchEvent) {
  if (touchStartY.value === null || e.touches.length !== 1) return
  const el = scrollEl.value
  if (!el || el.scrollTop > 0) {
    touchStartY.value = null
    pullDistance.value = 0
    return
  }
  const delta = e.touches[0].clientY - touchStartY.value
  if (delta > 0) {
    e.preventDefault()
    pullDistance.value = Math.min(delta * PULL_RESISTANCE, PULL_MAX)
  }
}

function onTouchEnd() {
  if (pullDistance.value >= PULL_THRESHOLD && !loading.value) {
    loadProjects()
  }
  touchStartY.value = null
  pullDistance.value = 0
}

function onEdit(p: ProjectDto, e: Event) {
  e.stopPropagation()
  if (openProjectEdit) openProjectEdit(p.id)
}

function toggleExpand(p: ProjectDto) {
  const id = p.id ?? ''
  if (!id) return
  const next = new Set(expandedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expandedIds.value = next
}

function isExpanded(p: ProjectDto): boolean {
  return p.id ? expandedIds.value.has(p.id) : false
}

function onCardClick(p: ProjectDto) {
  if (!selectedProjects.value.some((sp) => sp.id === p.id) && selectedProjects.value.length < maxContextProjects) {
    addProjectToContext(p)
  }
  toggleExpand(p)
}

function onDiscussionSubmit(text: string) {
  conversationApi.value?.submit(text, { clearProjectsAfterSend: false })
  switchToConversationView()
}

function onDiscussionStop() {
  conversationApi.value?.stop()
}

function isInContext(project: ProjectDto): boolean {
  return selectedProjects.value.some((sp) => sp.id === project.id)
}

/** Format project revenue for display: "0 €" or "2 500 €" (space as thousands separator). */
function formatRevenue(revenueCents: number | null | undefined): string {
  if (revenueCents == null || revenueCents === 0) return '0 €'
  const euros = Math.round(revenueCents / 100)
  return `${euros.toLocaleString('fr-FR', { useGrouping: true, minimumFractionDigits: 0, maximumFractionDigits: 0 })} €`
}

function activityCountLabel(count: number | null | undefined): string {
  const n = count ?? 0
  return n <= 1 ? `${n} activité loggée` : `${n} activités loggées`
}

const MINUTES_PER_WORK_DAY = 480

/** Same shape as LogEntriesBubble duration (minutes → h/min). */
function formatDurationPart(minutes: number): string {
  const m = Math.max(0, Math.floor(minutes))
  if (m < 60) return `${m} min`
  const h = Math.floor(m / 60)
  const rest = m % 60
  return rest > 0 ? `${h}h ${rest}min` : `${h}h`
}

/** Under 8 work hours: hours/minutes; from 8h up: days (8h) + remainder. */
function formatProjectScopedDuration(totalMinutes: number | null | undefined): string {
  const raw = totalMinutes ?? 0
  const m = Math.max(0, Math.floor(raw))
  if (m < MINUTES_PER_WORK_DAY) {
    return formatDurationPart(m)
  }
  const days = Math.floor(m / MINUTES_PER_WORK_DAY)
  const rem = m % MINUTES_PER_WORK_DAY
  const dayLabel = days === 1 ? '1 j' : `${days} j`
  if (rem === 0) return dayLabel
  return `${dayLabel} ${formatDurationPart(rem)}`
}

function scopedDurationTitle(totalMinutes: number | null | undefined): string {
  const m = Math.max(0, Math.floor(totalMinutes ?? 0))
  return `${m} minute${m === 1 ? '' : 's'} sur la période sélectionnée`
}

// Refetch when a project is saved or card color is cycled (edit modal)
function onProjectDataRefresh() {
  loadProjects()
}

watch([activityPeriodPreset, activityPeriodCustom], () => loadProjects(), { deep: true })

onMounted(() => {
  loadProjects()
  window.addEventListener('horain:projectSaved', onProjectDataRefresh)
  window.addEventListener('horain:projectUpdated', onProjectDataRefresh)
  nextTick(() => {
    const el = scrollEl.value
    if (el) {
      el.addEventListener('touchstart', onTouchStart, { passive: true })
      el.addEventListener('touchmove', onTouchMove, { passive: false })
      el.addEventListener('touchend', onTouchEnd, { passive: true })
    }
  })
})
onUnmounted(() => {
  window.removeEventListener('horain:projectSaved', onProjectDataRefresh)
  window.removeEventListener('horain:projectUpdated', onProjectDataRefresh)
  const el = scrollEl.value
  if (el) {
    el.removeEventListener('touchstart', onTouchStart)
    el.removeEventListener('touchmove', onTouchMove)
    el.removeEventListener('touchend', onTouchEnd)
  }
})
</script>

<template>
  <div class="projects-view">
    <div class="projects-header">
      <h2 class="projects-title">Projects</h2>
    </div>
    <div ref="scrollEl" class="projects-scroll">
      <div
        class="pull-indicator"
        :class="{ 'pull-indicator--active': pullDistance > 0 || (loading && projects.length > 0) }"
        :style="{ minHeight: pullDistance > 0 || (loading && projects.length > 0) ? `${Math.max(pullDistance, 48)}px` : '0' }"
        aria-live="polite"
        role="status"
      >
        <span v-if="loading && projects.length > 0" class="pull-indicator-text">
          <span class="pull-spinner" aria-hidden="true" />
          Refreshing…
        </span>
        <span v-else-if="pullDistance > 0" class="pull-indicator-text">
          {{ pullDistance >= PULL_THRESHOLD ? 'Release to refresh' : 'Pull to refresh' }}
        </span>
      </div>
      <p v-if="error" class="projects-error">{{ error }}</p>
      <div v-else-if="loading && projects.length === 0" class="projects-loading">Loading…</div>
      <div v-else class="project-cards">
        <div
          v-for="p in projects"
          :key="p.id"
          class="project-card"
          :class="{
            'project-card--in-context': isInContext(p),
            'project-card--expanded': isExpanded(p),
          }"
          :style="{ backgroundColor: projectColor(p) }"
          role="button"
          tabindex="0"
          :aria-expanded="isExpanded(p)"
          aria-label="Add to conversation context"
          @click="onCardClick(p)"
          @keydown.enter="onCardClick(p)"
          @keydown.space.prevent="onCardClick(p)"
        >
          <div class="project-card-body">
            <span class="project-card-name">{{ p.name }}</span>
            <div class="project-card-meta">
              <span v-if="p.billable !== false" class="project-card-billable" :aria-label="`Revenue: ${formatRevenue(p.revenueCents)}`">{{ formatRevenue(p.revenueCents) }}</span>
              <span v-if="p.billable !== false" class="project-card-meta-sep" aria-hidden="true">·</span>
              <span class="project-card-activity-badge" :title="activityCountLabel(p.timeLogCount)">
                <svg class="project-card-activity-icon" xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M8 6h13" /><path d="M8 12h13" /><path d="M8 18h13" /><path d="M3 6h.01" /><path d="M3 12h.01" /><path d="M3 18h.01" />
                </svg>
                <span class="project-card-activity-count">{{ p.timeLogCount ?? 0 }}</span>
              </span>
              <span class="project-card-meta-sep" aria-hidden="true">·</span>
              <span
                class="project-card-duration"
                :title="scopedDurationTitle(p.totalDurationMinutes)"
                :aria-label="scopedDurationTitle(p.totalDurationMinutes)"
              >{{ formatProjectScopedDuration(p.totalDurationMinutes) }}</span>
            </div>
            <div v-if="(p.topActivityTypes?.length ?? 0) > 0" class="project-card-tags">
              <span
                v-for="at in p.topActivityTypes"
                :key="at.code"
                class="project-card-tag"
                :title="`${at.label}: ${at.count} activité(s)`"
              >
                {{ at.code }} <span class="project-card-tag-count">{{ at.count }}</span>
              </span>
            </div>
            <p v-if="isExpanded(p) && p.description" class="project-card-desc">{{ p.description }}</p>
            <span v-else-if="isExpanded(p)" class="project-card-desc project-card-desc--empty">—</span>
          </div>
          <button
            v-if="isExpanded(p)"
            type="button"
            class="project-card-edit"
            aria-label="Edit project"
            title="Edit project"
            @click="onEdit(p, $event)"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
              <path d="m15 5 4 4" />
            </svg>
          </button>
        </div>
      </div>
      <p v-if="!loading && !error && projects.length === 0" class="projects-empty">
        No projects with activity in this period.
      </p>
    </div>
    <div v-if="selectedProjects.length > 0" class="discussion-bar">
      <div class="discussion-bar-inner">
        <p class="discussion-hint">Discussion sur ce projet — le message sera envoyé avec le projet en contexte.</p>
        <div class="context-chips">
          <span
            v-for="proj in selectedProjects"
            :key="'proj-' + proj.id"
            class="context-chip context-chip--project"
          >
            {{ proj.name }}
            <button
              type="button"
              class="context-chip-remove"
              aria-label="Remove project from context"
              @click="removeProjectFromContext(proj.id)"
            >
              ×
            </button>
          </span>
        </div>
        <PushToTalkButton
          :disabled="isProcessing"
          :processing="isProcessing"
          @submit="onDiscussionSubmit"
          @stop="onDiscussionStop"
          @permission-error="() => {}"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.projects-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
  padding: 1rem 1.25rem;
}

.projects-header {
  flex-shrink: 0;
  margin-bottom: 1rem;
}

.projects-scroll {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.pull-indicator {
  flex-shrink: 0;
  min-height: 0;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: min-height 0.15s ease-out;
}

.pull-indicator--active .pull-indicator-text {
  opacity: 1;
}

.pull-indicator-text {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1rem;
  color: #8888a0;
  opacity: 0.9;
}

.pull-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid #3a3a4e;
  border-top-color: #8b5cf6;
  border-radius: 50%;
  animation: pull-spin 0.7s linear infinite;
}

@keyframes pull-spin {
  to {
    transform: rotate(360deg);
  }
}

.projects-title {
  margin: 0;
  font-size: 1.375rem;
  font-weight: 600;
  color: #e8e8f0;
}

.projects-error {
  margin: 0;
  font-size: 1.125rem;
  color: #e57373;
}

.projects-loading {
  color: #8888a0;
  font-size: 1.125rem;
}

.projects-empty {
  color: #8888a0;
  font-size: 1.125rem;
  margin: 0;
}

/* Same grid logic as LogEntriesBubble for a consistent experience */
.project-cards {
  flex: 1;
  overflow: auto;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  grid-auto-rows: minmax(180px, auto);
  grid-auto-flow: row;
  gap: 0.75rem;
  padding: 0.5rem;
}

.project-card {
  min-width: 0;
  min-height: 0;
  aspect-ratio: 3 / 4;
  border-radius: 8px;
  padding: 0.5rem;
  display: flex;
  flex-direction: column;
  position: relative;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  color: rgba(255, 255, 255, 0.95);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
  cursor: pointer;
  transition: grid-column 0.3s ease, grid-row 0.3s ease;
  overflow: hidden;
}

/* Expanded: 2 columns × 2 rows, same as activity cards (no overlay) */
.project-card--expanded {
  grid-column: span 2;
  grid-row: span 2;
  aspect-ratio: auto;
  min-height: 4.5rem;
  padding-right: 2.25rem;
}

/* Selection state: subtle indicator only (no strong border) */
.project-card--in-context {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2), 0 0 0 1px rgba(255, 255, 255, 0.25);
}

.project-card-body {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

/* Collapsed: title at top, revenue / meta anchored toward bottom */
.project-card:not(.project-card--expanded) .project-card-meta {
  margin-top: auto;
}

.project-card:not(.project-card--expanded) .project-card-name {
  margin-bottom: 0;
}

/* Expanded: column flex; description grows into remaining space (tags optional) */
.project-card--expanded .project-card-body {
  gap: 0.4rem 0;
  padding-bottom: 0.25rem;
}

.project-card-meta {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  flex-wrap: wrap;
  min-width: 0;
}

.project-card-name {
  font-weight: 600;
  font-size: 1.2rem;
  color: inherit;
  word-break: break-word;
  margin-bottom: 0.35rem;
  overflow: hidden;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
}

.project-card--expanded .project-card-name {
  font-size: 1.25rem;
  margin-bottom: 0;
  -webkit-line-clamp: unset;
  line-clamp: unset;
}

.project-card-desc {
  margin: 0;
  font-size: 1rem;
  color: rgba(255, 255, 255, 0.85);
  line-height: 1.3;
  word-break: break-word;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  line-clamp: 3;
}

.project-card--expanded .project-card-desc {
  overflow: hidden;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 6;
  line-clamp: 6;
  min-height: 0;
  font-size: 1.05rem;
  line-height: 1.35;
}

.project-card-desc--empty {
  color: rgba(255, 255, 255, 0.6);
}

.project-card-billable {
  font-size: 1.2rem;
  font-weight: 700;
  color: #b8e086;
  margin-top: 0.15rem;
}

.project-card--expanded .project-card-billable {
  margin-top: 0;
  font-size: 1.25rem;
}

.project-card-meta-sep {
  opacity: 0.8;
  user-select: none;
}

/* Activity count badge (volume), all card states */
.project-card-activity-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.15rem 0.45rem;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.25);
  font-size: 0.9375rem;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.95);
}

.project-card-activity-icon {
  flex-shrink: 0;
  opacity: 0.9;
}

.project-card-activity-count {
  font-variant-numeric: tabular-nums;
}

.project-card-duration {
  display: inline-flex;
  align-items: center;
  padding: 0.15rem 0.45rem;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.2);
  font-size: 0.875rem;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.95);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Activity type tags (nature of work), all card states */
.project-card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  min-width: 0;
}

.project-card-tag {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  padding: 0.2rem 0.5rem;
  border-radius: 6px;
  font-size: 0.8125rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.02em;
  background: rgba(255, 255, 255, 0.2);
  color: rgba(255, 255, 255, 0.98);
}

.project-card-tag-count {
  font-variant-numeric: tabular-nums;
  opacity: 0.9;
  font-size: 0.75rem;
}

.project-card-edit {
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
  color: rgba(255, 255, 255, 0.9);
  cursor: pointer;
}

.project-card-edit:hover {
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
}

@media (max-width: 520px) {
  .project-cards {
    grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
    grid-auto-rows: minmax(140px, auto);
    gap: 0.5rem;
    padding: 0.4rem;
  }

  .project-card--expanded {
    grid-column: span 2;
    grid-row: span 2;
  }
}

/* Discussion bar: project context chips + input (same look as ConversationView input area) */
.discussion-bar {
  flex-shrink: 0;
  padding: 1rem max(0.75rem, env(safe-area-inset-right)) max(1rem, env(safe-area-inset-bottom)) max(0.75rem, env(safe-area-inset-left));
  border-top: 1px solid #2a2a3e;
  background: #0f0f1a;
}

@media (max-width: 600px) {
  .discussion-bar {
    padding-bottom: max(0.5rem, env(safe-area-inset-bottom), 16px);
    padding-left: max(0.75rem, env(safe-area-inset-left));
    padding-right: max(0.75rem, env(safe-area-inset-right));
  }
}

.discussion-bar-inner {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.discussion-hint {
  margin: 0;
  font-size: 0.8125rem;
  color: #8888a0;
}

.discussion-bar .context-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.discussion-bar .context-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.25rem 0.5rem;
  font-size: 0.9375rem;
  background: rgba(74, 110, 219, 0.2);
  color: #a0b8f0;
  border-radius: 8px;
}

.discussion-bar .context-chip--project {
  background: rgba(90, 138, 74, 0.25);
  color: #a8d098;
}

.discussion-bar .context-chip-remove {
  padding: 0;
  margin: 0;
  background: transparent;
  color: inherit;
  border: none;
  cursor: pointer;
  font-size: 1.25rem;
  line-height: 1;
  opacity: 0.8;
}

.discussion-bar .context-chip-remove:hover {
  opacity: 1;
}
</style>
