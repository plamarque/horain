<script setup lang="ts">
import type { Ref } from 'vue'
import { ref, provide } from 'vue'
import ConversationView from './views/ConversationView.vue'
import ProjectsView from './views/ProjectsView.vue'
import ProjectEditModal from './components/ProjectEditModal.vue'
import ActivityPeriodPicker from './components/ActivityPeriodPicker.vue'
import type { ProjectDto } from './services/apiClient'
import type { ActivityPeriodCustom, ActivityPeriodPreset } from './activityPeriod'
import { computeActivityRange } from './activityPeriod'

const MAX_CONTEXT_PROJECTS = 5

/** Conversation API (submit, stop, isProcessing) set by ConversationView so ProjectsView can send messages with project context. */
export type ConversationApi = {
  submit: (text: string, options?: { clearProjectsAfterSend?: boolean }) => void
  stop: () => void
  isProcessing: Ref<boolean>
}
const conversationApi = ref<ConversationApi | null>(null)
provide<Ref<ConversationApi | null>>('conversationApi', conversationApi as Ref<ConversationApi | null>)

// Injected at build time from frontend/package.json and git
const appVersion = __APP_VERSION__
const gitSha = __GIT_SHA__
const versionForDisplay = appVersion.replace(/-SNAPSHOT$/, '')
const versionDisplay =
  appVersion.endsWith('-SNAPSHOT') && gitSha
    ? `v${versionForDisplay} (${gitSha})`
    : `v${versionForDisplay}`

function refreshApp(): void {
  window.location.reload()
}

type View = 'conversation' | 'projects'
const view = ref<View>('conversation')
const editingProjectId = ref<string | null>(null)
const selectedProjects = ref<ProjectDto[]>([])

function addProjectToContext(project: ProjectDto) {
  if (!project.id) return
  if (selectedProjects.value.some((p) => p.id === project.id)) return
  if (selectedProjects.value.length >= MAX_CONTEXT_PROJECTS) return
  selectedProjects.value = [...selectedProjects.value, project]
}

function removeProjectFromContext(projectId: string) {
  selectedProjects.value = selectedProjects.value.filter((p) => p.id !== projectId)
}

function clearSelectedProjectsAfterSend() {
  selectedProjects.value = []
}

function openProjectEdit(projectId: string) {
  editingProjectId.value = projectId
}

function switchToConversationView() {
  view.value = 'conversation'
}

provide<((projectId: string) => void)>('openProjectEdit', openProjectEdit)
provide('selectedProjects', selectedProjects)
provide('addProjectToContext', addProjectToContext)
provide('removeProjectFromContext', removeProjectFromContext)
provide('clearSelectedProjectsAfterSend', clearSelectedProjectsAfterSend)
provide('MAX_CONTEXT_PROJECTS', MAX_CONTEXT_PROJECTS)
provide<() => void>('switchToConversationView', switchToConversationView)

function onProjectModalClose() {
  editingProjectId.value = null
}

function onProjectSaved() {
  editingProjectId.value = null
  window.dispatchEvent(new CustomEvent('horain:projectSaved'))
}

provide<string>('versionDisplay', versionDisplay)
provide<() => void>('refreshApp', refreshApp)

const activityPeriodPreset = ref<ActivityPeriodPreset>('rolling_28d')
const activityPeriodCustom = ref<ActivityPeriodCustom>({ fromYmd: '', toYmd: '' })

function getActivityRange(): { activityFrom: string; activityTo: string } {
  return computeActivityRange(activityPeriodPreset.value, activityPeriodCustom.value)
}

provide<Ref<ActivityPeriodPreset>>('activityPeriodPreset', activityPeriodPreset)
provide<Ref<ActivityPeriodCustom>>('activityPeriodCustom', activityPeriodCustom)
provide('getActivityRange', getActivityRange)
</script>

<template>
  <div class="app">
    <header class="header">
      <div class="header-left">
        <h1>Horain</h1>
        <button
          type="button"
          class="version-header"
          title="Refresh app"
          aria-label="Refresh app"
          @click="refreshApp()"
        >
          {{ versionDisplay }}
        </button>
      </div>
      <div class="header-center">
        <ActivityPeriodPicker />
      </div>
      <div class="header-right">
        <button
          v-if="view === 'conversation'"
          type="button"
          class="header-link"
          @click="view = 'projects'"
        >
          Projects
        </button>
        <button
          v-else
          type="button"
          class="header-link"
          @click="view = 'conversation'"
        >
          Back
        </button>
      </div>
    </header>
    <main class="main">
      <ConversationView v-show="view === 'conversation'" />
      <ProjectsView v-show="view === 'projects'" />
    </main>
    <ProjectEditModal
      v-if="editingProjectId"
      :project-id="editingProjectId"
      @close="onProjectModalClose"
      @saved="onProjectSaved"
    />
  </div>
</template>

<style>
* {
  box-sizing: border-box;
}

/* Discrete scrollbar matching dark theme */
* {
  scrollbar-width: thin;
  scrollbar-color: #3a3a4e #1a1a2e;
}

*::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

*::-webkit-scrollbar-track {
  background: #1a1a2e;
}

*::-webkit-scrollbar-thumb {
  background: #3a3a4e;
  border-radius: 4px;
}

*::-webkit-scrollbar-thumb:hover {
  background: #4a4a5e;
}

body {
  margin: 0;
  font-family: system-ui, -apple-system, sans-serif;
  background: #0f0f1a;
  color: #e8e8f0;
  min-height: 100vh;
  /* Prevent full-page scroll on mobile so input bar stays visible */
  overflow: hidden;
}

.app {
  max-width: 540px;
  margin: 0 auto;
  min-height: 100vh;
  min-height: 100dvh;
  height: 100vh;
  height: 100dvh;
  display: flex;
  flex-direction: column;
  padding-left: env(safe-area-inset-left, 0);
  padding-right: env(safe-area-inset-right, 0);
  padding-bottom: env(safe-area-inset-bottom, 0);
}

.header {
  padding: 1rem 1.25rem;
  border-bottom: 1px solid #2a2a3e;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
}

.header-center {
  flex: 1;
  min-width: 0;
  display: flex;
  justify-content: center;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.header h1 {
  margin: 0;
  font-size: 1.5625rem;
  font-weight: 600;
}

.version-header {
  padding: 0;
  margin: 0;
  font-size: 0.8125rem;
  color: #666680;
  background: none;
  border: none;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.version-header:hover {
  color: #8888a0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-shrink: 0;
}

.header-link {
  font-size: 1rem;
  color: #6a6a80;
  background: none;
  border: none;
  padding: 0;
  font: inherit;
  cursor: pointer;
}

.header-link:hover {
  color: #8888a0;
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}
</style>
