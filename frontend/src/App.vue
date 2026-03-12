<script setup lang="ts">
import ConversationView from './views/ConversationView.vue'

// Injected at build time from frontend/package.json and git
const appVersion = __APP_VERSION__
const gitSha = __GIT_SHA__
const versionDisplay =
  appVersion.endsWith('-SNAPSHOT') && gitSha
    ? `v${appVersion} (${gitSha})`
    : `v${appVersion}`

function refreshApp(): void {
  window.location.reload()
}
</script>

<template>
  <div class="app">
    <header class="header">
      <div class="header-left">
        <h1>Horain</h1>
        <span class="tagline">Voice-first time logging</span>
      </div>
      <button
        type="button"
        class="version"
        title="Refresh app"
        aria-label="Refresh app"
        @click="refreshApp"
      >
        {{ versionDisplay }}
      </button>
    </header>
    <main class="main">
      <ConversationView />
    </main>
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
  /* PWA standalone: keep content above home indicator / gesture bar */
  padding-bottom: env(safe-area-inset-bottom, 0);
  padding-left: env(safe-area-inset-left, 0);
  padding-right: env(safe-area-inset-right, 0);
}

@media (max-width: 600px) {
  .app {
    /* Fallback when env(safe-area-inset-bottom) is 0 (e.g. some Android) */
    padding-bottom: max(34px, env(safe-area-inset-bottom));
  }
}

.header {
  padding: 1rem 1.25rem;
  border-bottom: 1px solid #2a2a3e;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.header h1 {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
}

.tagline {
  font-size: 0.75rem;
  color: #8888a0;
}

.version {
  font-size: 0.7rem;
  color: #6a6a80;
  flex-shrink: 0;
  background: none;
  border: none;
  padding: 0;
  font: inherit;
  cursor: pointer;
}

.version:hover {
  color: #8888a0;
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
</style>
