import { createApp } from 'vue'
import App from './App.vue'
import { initSyncEngine } from './sync/syncEngine'
import './pwa/network'

const app = createApp(App)
app.mount('#app')

// Initialize sync on startup
initSyncEngine()
