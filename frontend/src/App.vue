<script setup lang="ts">
import ConversationView from './views/ConversationView.vue'
</script>

<template>
  <div class="app">
    <header class="header">
      <h1>Horain</h1>
      <span class="tagline">Voice-first time logging</span>
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

body {
  margin: 0;
  font-family: system-ui, -apple-system, sans-serif;
  background: #0f0f1a;
  color: #e8e8f0;
  min-height: 100vh;
}

.app {
  max-width: 480px;
  margin: 0 auto;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.header {
  padding: 1rem 1.25rem;
  border-bottom: 1px solid #2a2a3e;
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

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
</style>
