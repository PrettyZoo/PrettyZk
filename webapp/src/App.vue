<template>
  <div id="app-root" :data-theme="state.config.theme" :style="{ fontSize: state.config.fontSize + 'px' }">
    <!-- Sidebar -->
    <Sidebar
      :servers="state.servers"
      :statuses="state.serverStatuses"
      :locale="currentLocale"
      :font-size="state.config.fontSize"
      @select="onSelectServer"
      @add="onAddServer"
      @edit="onEditServer"
      @delete="onDeleteServer"
      @logs="onOpenLogs"
      @toggle-theme="onToggleTheme"
      @toggle-lang="onToggleLang"
      @update:font-size="onFontSizeChange"
      @config-export="onConfigExport"
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

<script setup lang="ts">
import { ref, reactive, defineAsyncComponent, onMounted } from 'vue'
import { state } from './main.ts'
import { api } from './api.ts'
import { setLocale, getLocale, t } from './i18n.ts'
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
const activeServerId = ref<string | null>(null)
const editServerId = ref<string | null>(null)
const currentLocale = ref(getLocale())

function onToggleLang() {
  const newLocale = getLocale() === 'zh' ? 'en' : 'zh'
  setLocale(newLocale)
  currentLocale.value = newLocale
  state.config.locale = newLocale === 'zh' ? 'zh-CN' : 'en'
  api.updateLocale(state.config.locale).catch(e => console.warn(e))
}

const dialog = reactive<{
  show: boolean; title: string; message: string; resolve: ((v: boolean) => void) | null
}>({
  show: false, title: '', message: '', resolve: null
})
// Legacy globals for cross-component toast/dialog access
;(window as any).__dialog = dialog
;(window as any).__toast = null

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

function onSelectServer(id: string) {
  activeServerId.value = id
  currentView.value = 'node-browser'
}

function onAddServer() {
  editServerId.value = null
  currentView.value = 'server-form'
}

function onEditServer(id: string) {
  editServerId.value = id
  currentView.value = 'server-form'
}

async function onDeleteServer(id: string) {
  const ok = await showDialog(t('server.deleteTitle'), t('server.deleteConfirm'))
  if (!ok) return
  try {
    await api.deleteServer(id)
    state.servers = (await api.listServers()) || []
    ;(window as any).__toast?.(t('server.deleted'), 'success')
  } catch (e: any) {
    ;(window as any).__toast?.(t('server.deleteFailed', { msg: e.message }), 'error')
  }
}

function onOpenLogs() { currentView.value = 'logs' }

function onToggleTheme() {
  const newTheme = state.config.theme === 'dark' ? 'default' : 'dark'
  state.config.theme = newTheme
  document.documentElement.setAttribute('data-theme', newTheme)
  api.updateTheme(newTheme).catch(e => console.warn(e))
}

function onServerSaved(id: string) {
  activeServerId.value = id
  currentView.value = 'node-browser'
  api.listServers().then(s => state.servers = s || []).catch(e => console.warn(e))
}

async function onConfigExport() {
  try {
    const data = await api.exportConfig()
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = 'prettyzk-config.json'; a.click()
    URL.revokeObjectURL(url)
    ;(window as any).__toast?.(t('config.exportSuccess'), 'success')
  } catch (e: any) {
    ;(window as any).__toast?.(t('config.exportFailed', { msg: e.message }), 'error')
  }
}

function onFontSizeChange(val: number) {
  state.config.fontSize = val
  document.documentElement.style.fontSize = val + 'px'
}

function onBackFromNodeBrowser() {
  currentView.value = 'welcome'
  api.listServers().then(s => state.servers = s || []).catch(e => console.warn(e))
}

function onDialogConfirm() { dialog.resolve?.(true); dialog.show = false }
function onDialogCancel() { dialog.resolve?.(false); dialog.show = false }

function showDialog(title: string, message: string): Promise<boolean> {
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
