<template>
  <n-config-provider :theme="naiveTheme" :theme-overrides="themeOverrides">
    <div
      id="app-root"
      :data-theme="state.config.theme"
      :style="{ fontSize: state.config.fontSize + 'px' }"
    >
      <Sidebar
        :servers="state.servers"
        :statuses="state.serverStatuses"
        :selected-id="activeServerId"
        :locale="currentLocale"
        :font-size="state.config.fontSize"
        @select="onSelectServer"
        @add="onAddServer"
        @edit="onEditServer"
        @delete="onDeleteServer"
        @connect="onConnectServer"
        @disconnect="onDisconnectServer"
        @logs="onOpenLogs"
        @toggle-theme="onToggleTheme"
        @toggle-lang="onToggleLang"
        @update:font-size="onFontSizeChange"
        @config-export="onConfigExport"
      />

      <main class="main-content">
        <WelcomeScreen
          v-if="currentView === 'welcome'"
          :servers="state.servers"
          :version="version"
          @add="onAddServer"
          @select="onSelectServer"
        />
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
        <LogViewer
          v-else-if="currentView === 'logs'"
          @back="currentView = 'welcome'"
        />
        <StatusBar
          :server="activeServer"
          :status="activeServerId ? state.serverStatuses[activeServerId] : ''"
          :version="version"
        />
      </main>

      <div v-if="!backendReady && !backendError" class="backend-hint">
        <span class="backend-hint-spinner"></span>
        {{ t('app.startingBackend') }}
      </div>
      <div v-else-if="backendError" class="backend-hint backend-hint-error">
        {{ backendError }}
      </div>
    </div>
  </n-config-provider>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, onUnmounted, ref } from 'vue'
import { NConfigProvider } from 'naive-ui'
import { state } from './state.ts'
import { api } from './api.ts'
import { setLocale, getLocale, t } from './i18n.ts'
import { naiveTheme as resolveNaiveTheme, darkThemeOverrides, lightThemeOverrides } from './theme.ts'
import { toast, confirm } from './composables/feedback.ts'
import './styles/tokens.css'
import './styles/global.css'

import Sidebar from './components/Sidebar.vue'
import WelcomeScreen from './components/WelcomeScreen.vue'
import ServerForm from './components/ServerForm.vue'
import StatusBar from './components/StatusBar.vue'

const NodeBrowser = defineAsyncComponent(() => import('./components/NodeBrowser.vue'))
const LogViewer = defineAsyncComponent(() => import('./components/LogViewer.vue'))

const currentView = ref('welcome')
const activeServerId = ref<string | null>(null)
const editServerId = ref<string | null>(null)
const currentLocale = ref(getLocale())
const version = ref('')

// The shell renders immediately. Only API-backed parts wait for the local
// Java service; in a plain browser the UI is served by that service already.
const backendReady = ref(
  !(typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window) ||
    Boolean(window.__PRETTYZK_BACKEND__),
)
const backendError = ref(window.__PRETTYZK_BACKEND_ERROR__ ?? '')

function onBackendReady() {
  backendReady.value = true
  backendError.value = ''
}

function onBackendError(event: Event) {
  const detail = (event as CustomEvent).detail
  backendError.value =
    typeof detail === 'string' && detail ? detail : t('app.backendFailed')
  backendReady.value = false
}

if (typeof window !== 'undefined') {
  window.addEventListener('prettyzk-backend-ready', onBackendReady)
  window.addEventListener('prettyzk-backend-error', onBackendError)
}
onUnmounted(() => {
  window.removeEventListener('prettyzk-backend-ready', onBackendReady)
  window.removeEventListener('prettyzk-backend-error', onBackendError)
})

const naiveTheme = computed(() => resolveNaiveTheme(state.config.theme === 'dark'))
const themeOverrides = computed(() => {
  const base = state.config.theme === 'dark' ? darkThemeOverrides : lightThemeOverrides
  return {
    ...base,
    common: { ...(base.common ?? {}), fontSize: state.config.fontSize + 'px' },
  }
})

const activeServer = computed(() =>
  state.servers.find((server) => server.id === activeServerId.value) ?? null,
)

function onToggleLang() {
  const newLocale = getLocale() === 'zh' ? 'en' : 'zh'
  setLocale(newLocale)
  currentLocale.value = newLocale
  state.config.locale = newLocale === 'zh' ? 'zh-CN' : 'en'
  api.updateLocale(state.config.locale).catch((e) => console.warn(e))
}

onMounted(async () => {
  try {
    state.servers = (await api.listServers()) || []
  } catch (e) {
    console.warn('load servers', e)
  }
  try {
    const config = await api.getConfig()
    state.config = config
    document.documentElement.setAttribute('data-theme', config.theme || 'default')
    if (config.locale) {
      const lang = config.locale.startsWith('zh') ? 'zh' : 'en'
      setLocale(lang)
      currentLocale.value = lang
    }
  } catch (e) {
    console.warn('load config', e)
  }
  try {
    version.value = (await api.version()).version
  } catch (e) {
    version.value = '3.0.0'
  }
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
  const ok = await confirm(t('server.deleteTitle'), t('server.deleteConfirm'))
  if (!ok) return
  try {
    await api.deleteServer(id)
    state.servers = (await api.listServers()) || []
    if (activeServerId.value === id) {
      activeServerId.value = null
      currentView.value = 'welcome'
    }
    toast(t('server.deleted'), 'success')
  } catch (e: any) {
    toast(t('server.deleteFailed', { msg: e.message }), 'error')
  }
}

async function onConnectServer(id: string) {
  try {
    await api.connect(id)
    state.serverStatuses[id] = 'connected'
    activeServerId.value = id
    currentView.value = 'node-browser'
    toast(t('server.connected'), 'success')
  } catch (e: any) {
    state.serverStatuses[id] = 'disconnected'
    toast(t('server.connectionFailed', { msg: e.message }), 'error')
  }
}

async function onDisconnectServer(id: string) {
  try {
    await api.disconnect(id)
    state.serverStatuses[id] = 'disconnected'
    toast(t('server.disconnected'), 'info')
  } catch (e: any) {
    toast(t('server.disconnectFailed', { msg: e.message }), 'error')
  }
}

function onOpenLogs() {
  currentView.value = 'logs'
}

function onToggleTheme() {
  const newTheme = state.config.theme === 'dark' ? 'default' : 'dark'
  state.config.theme = newTheme
  document.documentElement.setAttribute('data-theme', newTheme)
  api.updateTheme(newTheme).catch((e) => console.warn(e))
}

function onServerSaved(id: string) {
  activeServerId.value = id
  currentView.value = 'node-browser'
  api
    .listServers()
    .then((servers) => (state.servers = servers || []))
    .catch((e) => console.warn(e))
}

async function onConfigExport() {
  try {
    const data = await api.exportConfig()
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'prettyzk-config.json'
    a.click()
    URL.revokeObjectURL(url)
    toast(t('config.exportSuccess'), 'success')
  } catch (e: any) {
    toast(t('config.exportFailed', { msg: e.message }), 'error')
  }
}

function onFontSizeChange(val: number) {
  state.config.fontSize = val
  api.updateFontSize(val).catch(() => {})
}

function onBackFromNodeBrowser() {
  currentView.value = 'welcome'
  api
    .listServers()
    .then((servers) => (state.servers = servers || []))
    .catch((e) => console.warn(e))
}
</script>

<style>
#app-root {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
}
.main-content {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: var(--bg-primary);
  min-width: 0;
}
.backend-hint {
  position: fixed;
  top: 10px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2000;
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: min(80vw, 640px);
  padding: 6px 12px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: var(--toolbar-bg);
  color: var(--text-secondary);
  font-size: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  pointer-events: none;
}
.backend-hint-error {
  border-color: #b42318;
  border-radius: 8px;
  background: #b42318;
  color: #fff;
  pointer-events: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
.backend-hint-spinner {
  width: 10px;
  height: 10px;
  flex-shrink: 0;
  border: 2px solid var(--border-color);
  border-top-color: var(--success);
  border-radius: 50%;
  animation: backend-spin 0.8s linear infinite;
}
@keyframes backend-spin {
  to { transform: rotate(360deg); }
}
</style>
