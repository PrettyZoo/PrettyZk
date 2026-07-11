<template>
  <nav class="sidebar">
    <div class="sidebar-header">
      <span class="logo">🦓</span>
      <span class="brand">PrettyZk</span>
    </div>

    <div class="sidebar-toolbar">
      <button class="toolbar-btn" @click="$emit('add')" title="New Server">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M8 3v10M3 8h10" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
        <span>{{ t('sidebar.newConnection') }}</span>
      </button>
    </div>

    <div class="server-list-label">{{ t('sidebar.servers') }}</div>

    <div class="server-list">
      <div
        v-for="s in servers"
        :key="s.id"
        class="server-card"
        :class="{ selected: s.id === selectedId }"
        @click="$emit('select', s.id)"
        @dblclick="$emit('edit', s.id)"
        @contextmenu.prevent="showContextMenu($event, s)"
      >
        <div class="server-status" :class="statuses[s.id] || 'disconnected'"></div>
        <div class="server-info">
          <div class="server-name">{{ s.alias || s.host + ':' + s.port }}</div>
          <div class="server-host">{{ s.host }}:{{ s.port }}</div>
        </div>
        <div class="server-right">
          <div class="server-connected-dot" v-if="(statuses[s.id] || 'disconnected') === 'connected'" title="Connected"></div>
          <button class="server-edit-btn" @click.stop="$emit('edit', s.id)" title="Edit">✎</button>
          <button class="server-del-btn" @click.stop="$emit('delete', s.id)" title="Delete">✕</button>
        </div>
      </div>
      <div v-if="!servers || servers.length === 0" class="server-empty">
        No servers yet. Click "+" to add one.
      </div>
    </div>

    <div class="sidebar-footer">
      <button class="footer-btn" @click="$emit('config-export')" title="Export Config">📤</button>
      <button class="footer-btn" @click="onImportConfig" title="Import Config">📥</button>
      <button class="footer-btn" @click="$emit('logs')" title="Logs">📋 {{ t('sidebar.logs') }}</button>
      <button class="footer-btn" @click="$emit('toggle-lang')" title="Switch Language">{{ locale === 'zh' ? 'English' : '中文' }}</button>
      <button class="footer-btn" @click="showFontSlider = !showFontSlider" title="Font Size">🔤</button>
      <button class="footer-btn" @click="$emit('toggle-theme')" title="Toggle Dark/Light">{{ isDark ? '☀️' : '🌙' }}</button>
    </div>
    <div v-if="showFontSlider" class="font-slider-panel">
      <input type="range" min="10" max="24" :value="fontSize" @input="onFontChange" class="font-slider" />
      <span class="font-size-label">{{ fontSize }}px</span>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { t, getLocale } from '../i18n.ts'
import { api } from '../api.ts'

const props = defineProps({
  servers: { type: Array, default: () => [] },
  statuses: { type: Object, default: () => ({}) },
  selectedId: { type: String, default: null },
  locale: { type: String, default: 'en' },
  fontSize: { type: Number, default: 14 },
})
const emit = defineEmits(['select', 'add', 'edit', 'delete', 'logs', 'toggle-theme', 'toggle-lang', 'update:fontSize', 'config-export'])

const showFontSlider = ref(false)

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
      window.__toast?.('Config imported, please restart', 'success')
    } catch (e) {
      window.__toast?.('Import failed: ' + e.message, 'error')
    }
  }
  input.click()
}

function onFontChange(e) {
  const val = parseInt(e.target.value)
  emit('update:fontSize', val)
  api.updateFontSize(val).catch(() => {})
}
const isDark = computed(() => document.documentElement.getAttribute('data-theme') === 'dark')
function showContextMenu(e, s) {}
</script>

<style scoped>
.sidebar {
  width: var(--sidebar-width, 270px); min-width: var(--sidebar-width, 270px);
  background: var(--sidebar-bg); display: flex; flex-direction: column;
  border-right: 1px solid var(--border-color);
  overflow: hidden; user-select: none;
}
.sidebar-header {
  display: flex; align-items: center; gap: 10px;
  padding: 18px 16px 14px; border-bottom: 1px solid var(--border-color);
}
.logo {
  width: 32px; height: 32px;
  display: flex; align-items: center; justify-content: center;
  font-size: 22px; flex-shrink: 0;
}
.brand { font-size: 15px; font-weight: 700; color: var(--text-primary); letter-spacing: 0.3px; }
.sidebar-toolbar { padding: 12px 12px 8px; }
.toolbar-btn {
  width: 100%; padding: 9px 12px; border: 1px dashed var(--border-color);
  border-radius: 8px; background: transparent; color: var(--sidebar-text);
  cursor: pointer; display: flex; align-items: center; gap: 8px;
  font-size: 13px; font-weight: 500; transition: all 0.15s;
}
.toolbar-btn:hover { background: var(--sidebar-hover); border-color: var(--accent); color: var(--text-primary); }
.sidebar-toolbar + .server-list-label { padding-top: 4px; }
.server-list-label {
  padding: 12px 16px 6px; font-size: 10px; font-weight: 700;
  color: var(--text-muted); text-transform: uppercase; letter-spacing: 1.2px;
}
.server-list { flex: 1; overflow-y: auto; padding: 2px 8px 8px; }
.server-card {
  display: flex; align-items: center; gap: 8px;
  padding: 9px 10px; border-radius: 8px;
  cursor: pointer; transition: all 0.12s;
  margin-bottom: 1px;
}
.server-card:hover { background: var(--sidebar-hover); }
.server-card.selected { background: var(--sidebar-hover); }
.server-card.selected .server-name { color: #fff; }
.server-card .server-name { transition: color 0.12s; }
.server-status {
  width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0;
  background: var(--text-muted); transition: all 0.3s;
}
.server-status.connected { background: var(--success-light); box-shadow: 0 0 6px rgba(76,175,128,0.4); }
.server-status.disconnected { background: var(--border-color); }
.server-status.connecting { background: var(--warning-light); animation: pulse 1s infinite; }
.server-info { flex: 1; min-width: 0; }
.server-name { font-size: 13px; font-weight: 500; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.server-host { font-size: 11px; color: var(--text-muted); margin-top: 1px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.server-right { display: flex; align-items: center; gap: 2px; flex-shrink: 0; opacity: 1; }
.server-edit-btn { opacity: 0.4; }
.server-card:hover .server-edit-btn { opacity: 0.7; }
.server-edit-btn:hover { opacity: 1 !important; }
.server-del-btn { opacity: 0.3; }
.server-card:hover .server-del-btn { opacity: 0.6; }
.server-del-btn:hover { opacity: 1 !important; }
.server-connected-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--success-light); box-shadow: 0 0 8px rgba(76, 175, 128, 0.6); flex-shrink: 0; margin-right: 4px; }
.server-edit-btn { width: 26px; height: 26px; border: none; background: transparent; color: var(--text-muted); cursor: pointer; border-radius: 5px; font-size: 16px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; padding: 0; transition: all 0.12s; }
.server-edit-btn:hover { color: var(--text-primary); background: var(--bg-hover); }
.server-del-btn { width: 26px; height: 26px; border: none; background: transparent; color: var(--text-muted); cursor: pointer; border-radius: 5px; font-size: 14px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; padding: 0; transition: all 0.12s; }
.server-del-btn:hover { color: var(--danger); background: var(--danger-light); }
.server-empty { padding: 24px 16px; font-size: 12px; color: var(--text-muted); text-align: center; line-height: 1.6; }
.sidebar-footer {
  display: flex; border-top: 1px solid var(--border-color); padding: 8px 10px; gap: 4px;
}
.footer-btn {
  flex: 1; padding: 7px 4px; border: none; border-radius: 6px;
  background: transparent; color: var(--sidebar-text); cursor: pointer;
  font-size: 12px; text-align: center; transition: all 0.12s;
}
.footer-btn:hover { background: var(--sidebar-hover); color: var(--text-primary); }
.font-slider-panel { display:flex; align-items:center; gap:6px; padding:6px 12px; border-top:1px solid var(--border-color); background:var(--sidebar-bg); }
.font-slider { flex:1; height:4px; cursor:pointer; accent-color:var(--accent); }
.font-size-label { font-size:11px; color:var(--text-muted); min-width:30px; text-align:right; }
@keyframes pulse { 0%,100% { opacity:1 } 50% { opacity:0.4 } }
</style>
