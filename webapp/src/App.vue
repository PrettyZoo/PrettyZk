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
    </div>
  </n-config-provider>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref } from 'vue'
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
</style>
