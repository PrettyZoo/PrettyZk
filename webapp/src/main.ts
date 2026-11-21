import { createApp } from 'vue'
import App from './App.vue'

// Tell the Rust shell the page is alive, so startup errors can be shown as an
// in-app banner instead of a standalone fallback page.
window.__PRETTYZK_FRONTEND_READY__ = true
// If the Rust fallback banner was shown before the page finished loading, the
// in-app banner takes over from here.
document.getElementById('prettyzk-backend-error-banner')?.remove()

const app = createApp(App)
app.mount('#app')
