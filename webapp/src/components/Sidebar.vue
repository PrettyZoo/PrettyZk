<template>
  <nav class="sidebar">
    <div class="sidebar-header">
      <img class="brand-logo" :src="iconUrl" alt="PrettyZk" />
      <span class="brand">PrettyZk</span>
    </div>

    <div class="sidebar-toolbar">
      <n-input
        v-model:value="filter"
        size="small"
        clearable
        :placeholder="t('sidebar.search')"
        class="sidebar-search"
      >
        <template #prefix>
          <Search :size="14" />
        </template>
      </n-input>
      <n-button block dashed type="primary" size="small" @click="$emit('add')">
        <template #icon><Plus :size="15" /></template>
        {{ t('sidebar.newConnection') }}
      </n-button>
    </div>

    <div class="server-list-label">{{ t('sidebar.servers') }}</div>

    <div class="server-list">
      <div
        v-for="s in filteredServers"
        :key="s.id"
        class="server-card"
        :class="{ selected: s.id === selectedId }"
        @click="$emit('select', s.id)"
        @dblclick="$emit('edit', s.id)"
        @contextmenu.prevent="openMenu($event, s)"
      >
        <div class="server-status" :class="statuses[s.id] || 'disconnected'"></div>
        <div class="server-info">
          <div class="server-name">{{ s.alias || s.host + ':' + s.port }}</div>
          <div class="server-host">{{ s.host }}:{{ s.port }}</div>
        </div>
        <div class="server-right">
          <n-button
            quaternary
            circle
            size="tiny"
            :title="t('server.editServer')"
            @click.stop="$emit('edit', s.id)"
          >
            <template #icon><Pencil :size="13" /></template>
          </n-button>
          <n-button
            quaternary
            circle
            size="tiny"
            :title="t('server.delete')"
            @click.stop="$emit('delete', s.id)"
          >
            <template #icon><Trash2 :size="13" /></template>
          </n-button>
        </div>
      </div>
      <div v-if="filteredServers.length === 0" class="server-empty">
        {{ filter ? t('sidebar.noMatch') : t('sidebar.noServers') }}
      </div>
    </div>

    <div class="sidebar-footer">
      <div class="footer-group">
        <button class="footer-btn" @click="$emit('config-export')" :title="t('sidebar.exportTooltip')">
          <Download :size="15" />
        </button>
        <button class="footer-btn" @click="onImportConfig" :title="t('sidebar.importTooltip')">
          <Upload :size="15" />
        </button>
        <button class="footer-btn" @click="$emit('logs')" :title="t('sidebar.logs')">
          <FileText :size="15" />
        </button>
      </div>
      <div class="footer-group">
        <button class="footer-btn" @click="$emit('toggle-lang')" :title="locale === 'zh' ? 'English' : '中文'">
          <Languages :size="15" />
        </button>
        <button class="footer-btn" @click="showFontSlider = !showFontSlider" :title="t('sidebar.fontTooltip')">
          <Type :size="15" />
        </button>
      </div>
      <button class="footer-btn theme-btn" @click="$emit('toggle-theme')" :title="isDark ? t('sidebar.themeLight') : t('sidebar.themeDark')">
        <Sun v-if="isDark" :size="15" />
        <Moon v-else :size="15" />
      </button>
    </div>

    <div v-if="showFontSlider" class="font-slider-panel">
      <input type="range" min="10" max="24" :value="fontSize" @input="onFontChange" class="font-slider" />
      <span class="font-size-label">{{ fontSize }}px</span>
    </div>

    <div v-if="menu.show" class="ctx-backdrop" @click="menu.show = false" @contextmenu.prevent="menu.show = false"></div>
    <div v-if="menu.show" class="ctx-menu" :style="{ left: menu.x + 'px', top: menu.y + 'px' }">
      <button class="ctx-item" @click="runMenu('connect')">{{ t('server.connect') }}</button>
      <button class="ctx-item" @click="runMenu('disconnect')">{{ t('server.disconnect') }}</button>
      <div class="ctx-sep"></div>
      <button class="ctx-item" @click="runMenu('edit')">{{ t('server.editServer') }}</button>
      <button class="ctx-item danger" @click="runMenu('delete')">{{ t('server.delete') }}</button>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { NButton, NInput } from 'naive-ui'
import {
  Download,
  FileText,
  Languages,
  Moon,
  Pencil,
  Plus,
  Search,
  Sun,
  Trash2,
  Type,
  Upload,
} from 'lucide-vue-next'
import { t, getLocale } from '../i18n.ts'
import iconUrl from '../assets/icon.png'
import { api } from '../api.ts'
import { state } from '../state.ts'
import { toast } from '../composables/feedback.ts'

const props = defineProps({
  servers: { type: Array, default: () => [] },
  statuses: { type: Object, default: () => ({}) },
  selectedId: { type: String, default: null },
  locale: { type: String, default: 'en' },
  fontSize: { type: Number, default: 14 },
})
const emit = defineEmits([
  'select',
  'add',
  'edit',
  'delete',
  'connect',
  'disconnect',
  'logs',
  'toggle-theme',
  'toggle-lang',
  'update:fontSize',
  'config-export',
])

const showFontSlider = ref(false)
const filter = ref('')
const menu = reactive<{ show: boolean; x: number; y: number; server: any }>({
  show: false,
  x: 0,
  y: 0,
  server: null,
})

const filteredServers = computed(() => {
  const q = filter.value.trim().toLowerCase()
  if (!q) return props.servers
  return props.servers.filter((s: any) => {
    const text = `${s.alias || ''} ${s.host} ${s.port}`.toLowerCase()
    return text.includes(q)
  })
})

const isDark = computed(() => state.config.theme === 'dark')

function openMenu(event: MouseEvent, server: any) {
  menu.show = true
  menu.x = event.clientX
  menu.y = event.clientY
  menu.server = server
}

function runMenu(action: string) {
  const server = menu.server
  menu.show = false
  if (!server) return
  if (action === 'connect') emit('connect', server.id)
  if (action === 'disconnect') emit('disconnect', server.id)
  if (action === 'edit') emit('edit', server.id)
  if (action === 'delete') emit('delete', server.id)
}

async function onImportConfig() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file) return
    try {
      const text = await file.text()
      await api.importConfig(text)
      toast(t('sidebar.importSuccess'), 'success')
    } catch (e: any) {
      toast(t('sidebar.importFailed', { msg: e.message }), 'error')
    }
  }
  input.click()
}

function onFontChange(e: Event) {
  const val = parseInt((e.target as HTMLInputElement).value, 10)
  emit('update:fontSize', val)
  api.updateFontSize(val).catch(() => {})
}
</script>

<style scoped>
.sidebar {
  width: var(--sidebar-width, 280px);
  min-width: var(--sidebar-width, 280px);
  background: var(--sidebar-bg);
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border-color);
  overflow: hidden;
  user-select: none;
}
.sidebar-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 14px 12px;
}
.brand-logo {
  width: 28px;
  height: 28px;
  border-radius: 7px;
  flex-shrink: 0;
}
.brand {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: 0.2px;
}
.sidebar-toolbar {
  padding: 0 12px 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.sidebar-search :deep(.n-input__border),
.sidebar-search :deep(.n-input__state-border) {
  border-radius: 6px;
}
.server-list-label {
  padding: 8px 16px 6px;
  font-size: 10px;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 1.1px;
}
.server-list {
  flex: 1;
  overflow-y: auto;
  padding: 2px 8px 8px;
}
.server-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 7px;
  cursor: pointer;
  margin-bottom: 1px;
  border-left: 3px solid transparent;
  transition: background 0.12s, border-color 0.12s;
}
.server-card:hover {
  background: var(--sidebar-hover);
}
.server-card.selected {
  background: var(--accent-subtle);
  border-left-color: var(--accent);
}
.server-card.selected .server-name {
  color: var(--text-primary);
}
.server-status {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--text-muted);
  transition: background 0.3s;
}
.server-status.connected {
  background: var(--success);
}
.server-status.disconnected {
  background: var(--border-color);
}
.server-status.connecting {
  background: var(--warning);
  animation: pulse 1s infinite;
}
.server-info {
  flex: 1;
  min-width: 0;
}
.server-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.server-host {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 1px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.server-right {
  display: flex;
  align-items: center;
  gap: 0;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.12s;
}
.server-card:hover .server-right,
.server-card.selected .server-right {
  opacity: 1;
}
.server-empty {
  padding: 24px 16px;
  font-size: 12px;
  color: var(--text-muted);
  text-align: center;
  line-height: 1.6;
}
.sidebar-footer {
  display: flex;
  align-items: center;
  border-top: 1px solid var(--border-color);
  padding: 4px 6px;
  gap: 2px;
}
.footer-group {
  display: flex;
  gap: 1px;
}
.footer-btn {
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--sidebar-text);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, color 0.15s;
}
.footer-btn:hover {
  background: var(--sidebar-hover);
  color: var(--text-primary);
}
.footer-btn.theme-btn {
  margin-left: auto;
}
.font-slider-panel {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-top: 1px solid var(--border-color);
  background: var(--sidebar-bg);
}
.font-slider {
  flex: 1;
  height: 4px;
  cursor: pointer;
  accent-color: var(--accent);
}
.font-size-label {
  font-size: 11px;
  color: var(--text-muted);
  min-width: 30px;
  text-align: right;
}
.ctx-backdrop {
  position: fixed;
  inset: 0;
  z-index: 9997;
}
.ctx-menu {
  position: fixed;
  z-index: 9998;
  min-width: 150px;
  padding: 4px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.16);
}
.ctx-item {
  display: block;
  width: 100%;
  padding: 7px 12px;
  border: none;
  border-radius: 5px;
  background: transparent;
  color: var(--text-primary);
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}
.ctx-item:hover {
  background: var(--bg-hover);
}
.ctx-item.danger {
  color: var(--danger);
}
.ctx-sep {
  height: 1px;
  margin: 4px 8px;
  background: var(--border-color);
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
</style>
