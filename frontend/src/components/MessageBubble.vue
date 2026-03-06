<script setup lang="ts">
defineProps<{
  role: 'user' | 'assistant'
  text: string
}>()
</script>

<template>
  <div class="bubble" :class="role">
    <div class="content">{{ text }}</div>
  </div>
</template>

<style scoped>
.bubble {
  max-width: 85%;
  padding: 0.75rem 1rem;
  border-radius: 16px;
  align-self: flex-start;
}

.bubble.user {
  align-self: flex-end;
  background: #4a6edb;
  color: white;
}

.bubble.assistant {
  background: #2a2a3e;
  color: #e8e8f0;
}

.content {
  font-size: 0.9rem;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
