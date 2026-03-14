<script setup lang="ts">
import type { Ref } from 'vue'
import { ref, onMounted, onUnmounted, inject, nextTick } from 'vue'
import { getProjects } from '../services/apiClient'
import type { ProjectDto } from '../services/apiClient'

const PULL_THRESHOLD = 72
const PULL_MAX = 100
const PULL_RESISTANCE = 0.5

// Same palette and hash as LogEntriesBubble so project cards match log entry card colors
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

function projectColor(project: ProjectDto): string {
  const key = project.id ?? project.name ?? ''
  if (!key) return 'rgba(0,0,0,0.25)'
  let h = 0
  for (let i = 0; i < key.length; i++) h = (h << 5) - h + key.charCodeAt(i)
  const idx = Math.abs(h) % PROJECT_COLORS.length
  return PROJECT_COLORS[idx]
}

const openProjectEdit = inject<((projectId: string) => void)>('openProjectEdit')
const selectedProjects = inject<Ref<ProjectDto[]>>('selectedProjects', ref([]))
const addProjectToContext = inject<(project: ProjectDto) => void>('addProjectToContext', () => {})
const maxContextProjects = inject<number>('MAX_CONTEXT_PROJECTS', 5)

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
    projects.value = await getProjects()
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

function isInContext(project: ProjectDto): boolean {
  return selectedProjects.value.some((sp) => sp.id === project.id)
}

function truncate(s: string | undefined, maxLen: number): string {
  if (!s) return '—'
  return s.length <= maxLen ? s : s.slice(0, maxLen) + '…'
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

// Refetch when a project is saved (e.g. from edit modal)
function onProjectSaved() {
  loadProjects()
}

onMounted(() => {
  loadProjects()
  window.addEventListener('horain:projectSaved', onProjectSaved)
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
  window.removeEventListener('horain:projectSaved', onProjectSaved)
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
              <template v-if="isExpanded(p)">
                <span v-if="p.billable !== false" class="project-card-meta-sep" aria-hidden="true">·</span>
                <span class="project-card-activity-badge" :title="activityCountLabel(p.timeLogCount)">
                  <svg class="project-card-activity-icon" xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M8 6h13" /><path d="M8 12h13" /><path d="M8 18h13" /><path d="M3 6h.01" /><path d="M3 12h.01" /><path d="M3 18h.01" />
                  </svg>
                  <span class="project-card-activity-count">{{ p.timeLogCount ?? 0 }}</span>
                </span>
              </template>
            </div>
            <div v-if="isExpanded(p) && (p.topActivityTypes?.length ?? 0) > 0" class="project-card-tags">
              <span
                v-for="at in p.topActivityTypes"
                :key="at.code"
                class="project-card-tag"
                :title="`${at.label}: ${at.count} activité(s)`"
              >
                {{ at.code }} <span class="project-card-tag-count">{{ at.count }}</span>
              </span>
            </div>
            <p v-if="p.description" class="project-card-desc">{{ isExpanded(p) ? p.description : truncate(p.description, 60) }}</p>
            <span v-else class="project-card-desc project-card-desc--empty">—</span>
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
      <p v-if="!loading && !error && projects.length === 0" class="projects-empty">No projects yet.</p>
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

/* Expanded: grid layout; name, meta, tags, description fills remaining space */
.project-card--expanded .project-card-body {
  display: grid;
  grid-template-rows: auto auto auto 1fr;
  gap: 0.4rem 0;
  align-content: space-between;
  padding-bottom: 0.25rem;
}

.project-card-meta {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  flex-wrap: wrap;
  min-width: 0;
}

.project-card:not(.project-card--expanded) .project-card-meta-sep,
.project-card:not(.project-card--expanded) .project-card-activity-badge {
  display: none;
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

/* Distinctive badge for activity count (volume) in expanded mode */
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

/* Activity type tags (nature of work) in expanded mode */
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
</style>
