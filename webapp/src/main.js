import { createApp, reactive } from 'vue'
import App from './App.vue'

const app = createApp(App)

// Global state
export const state = reactive({
  servers: [],
  activeServerId: null,
  serverStatuses: {},
  config: { theme: 'default', fontSize: 14, locale: 'en' },
})

app.mount('#app')
