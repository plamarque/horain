import { createApp } from 'vue'
import App from './App.vue'
import { initSyncEngine } from './sync/syncEngine'
import './pwa/network'
import 'katex/dist/katex.min.css'

const app = createApp(App)
app.mount('#app')

// Initialize sync on startup
initSyncEngine()
