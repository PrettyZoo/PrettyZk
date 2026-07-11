<template>
  <div id="app-root" :data-theme="state.config.theme">
    <!-- Sidebar -->
    <Sidebar
      :servers="state.servers"
      :statuses="state.serverStatuses"
      :locale="currentLocale"
      @select="onSelectServer"
      @add="onAddServer"
      @edit="onEditServer"
      @delete="onDeleteServer"
      @logs="onOpenLogs"
      @toggle-theme="onToggleTheme"
      @toggle-lang="onToggleLang"
    />
    <!-- Main Content -->
    <main class="main-content">
      <Toast />
      <Dialog
        v-if="dialog.show"
        :title="dialog.title"
        :message="dialog.message"
        @confirm="onDialogConfirm"
        @cancel="onDialogCancel"
      />
      <WelcomeScreen v-if="currentView === 'welcome'" @add="onAddServer" />
      <ServerForm
        v-else-if="currentView === 'server-form'"
        :server-id="editServerId"
        @saved="onServerSaved"
        @cancel="currentView = 'welcome'"
      />
      <NodeBrowser
        v-else-if="currentView === 'node-browser'"
        :server-id="activeServerId"
        @back="onBackFromNodeBrowser"
      />
      <LogViewer v-else-if="currentView === 'logs'" @back="currentView = 'welcome'" />
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, computed, defineAsyncComponent, onMounted } from 'vue'
import { state } from './main.js'
import { api } from './api.js'
import { setLocale, getLocale, t } from './i18n.js'
import './style.css'

import Sidebar from './components/Sidebar.vue'
import WelcomeScreen from './components/WelcomeScreen.vue'
import ServerForm from './components/ServerForm.vue'
import Toast from './components/Toast.vue'
import Dialog from './components/Dialog.vue'

// Large components loaded on demand
const NodeBrowser = defineAsyncComponent(() => import('./components/NodeBrowser.vue'))
const LogViewer = defineAsyncComponent(() => import('./components/LogViewer.vue'))

const currentView = ref('welcome')
const activeServerId = ref(null)
const editServerId = ref(null)
const currentLocale = ref(getLocale())

function onToggleLang() {
  const newLocale = getLocale() === 'zh' ? 'en' : 'zh'
  setLocale(newLocale)
  currentLocale.value = newLocale
  state.config.locale = newLocale === 'zh' ? 'zh-CN' : 'en'
  api.updateLocale(state.config.locale).catch(e => console.warn(e))
}

const dialog = reactive({
  show: false, title: '', message: '', resolve: null
})
window.__dialog = dialog
window.__toast = null

onMounted(async () => {
  try { state.servers = (await api.listServers()) || [] } catch (e) { console.warn('load servers', e) }
  try {
    const config = await api.getConfig()
    state.config = config
    document.documentElement.setAttribute('data-theme', config.theme || 'default')
    // Load locale
    if (config.locale) {
      const lang = config.locale.startsWith('zh') ? 'zh' : 'en'
      setLocale(lang)
      currentLocale.value = lang
    }
  } catch (e) { console.warn('load config', e) }
})

function onSelectServer(id) {
  activeServerId.value = id
  currentView.value = 'node-browser'
}

function onAddServer() {
  editServerId.value = null
  currentView.value = 'server-form'
}

function onEditServer(id) {
  editServerId.value = id
  currentView.value = 'server-form'
}

async function onDeleteServer(id) {
  const ok = await showDialog('Delete Server', 'Are you sure you want to delete this server?')
  if (!ok) return
  try {
    await api.deleteServer(id)
    state.servers = (await api.listServers()) || []
    window.__toast?.('Server deleted', 'success')
  } catch (e) {
    window.__toast?.('Failed to delete: ' + e.message, 'error')
  }
}

function onOpenLogs() { currentView.value = 'logs' }

function onToggleTheme() {
  const newTheme = state.config.theme === 'dark' ? 'default' : 'dark'
  state.config.theme = newTheme
  document.documentElement.setAttribute('data-theme', newTheme)
  api.updateTheme(newTheme).catch(e => console.warn(e))
}

function onServerSaved(id) {
  activeServerId.value = id
  currentView.value = 'node-browser'
  api.listServers().then(s => state.servers = s || []).catch(e => console.warn(e))
}

function onBackFromNodeBrowser() {
  currentView.value = 'welcome'
  api.listServers().then(s => state.servers = s || []).catch(e => console.warn(e))
}

function onDialogConfirm() { dialog.resolve?.(true); dialog.show = false }
function onDialogCancel() { dialog.resolve?.(false); dialog.show = false }

function showDialog(title, message) {
  return new Promise(resolve => {
    dialog.title = title
    dialog.message = message
    dialog.show = true
    dialog.resolve = resolve
  })
}
</script>

<style>
#app-root { display: flex; height: 100vh; width: 100vw; overflow: hidden; }
.main-content { flex:1; overflow:hidden; display:flex; flex-direction:column; background:var(--bg-primary); min-width:0; }
</style>
