<script setup lang="ts">
import ConversationView from './views/ConversationView.vue'

// Injected at build time from frontend/package.json
const appVersion = __APP_VERSION__
</script>

<template>
  <div class="app">
    <header class="header">
      <div class="header-left">
        <h1>Horain</h1>
        <span class="tagline">Voice-first time logging</span>
      </div>
      <span class="version">v{{ appVersion }}</span>
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
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
</style>
