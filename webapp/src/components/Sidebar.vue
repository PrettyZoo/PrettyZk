<template>
  <nav class="sidebar">
    <div class="sidebar-header">
      <span class="logo">PZ</span>
      <span class="brand">{{ t('app.name') }}</span>
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
        @contextmenu.prevent="showContextMenu($event, s)"
      >
        <div class="server-status" :class="statuses[s.id] || 'disconnected'"></div>
        <div class="server-info">
          <div class="server-name">{{ s.alias || s.host + ':' + s.port }}</div>
          <div class="server-host">{{ s.host }}:{{ s.port }}</div>
        </div>
        <div class="server-acl-badge" v-if="s.acl" title="ACL configured">🔒</div>
        <button class="server-action-btn" @click.stop="$emit('edit', s.id)" title="Edit">⚙</button>
        <button class="server-action-btn danger" @click.stop="$emit('delete', s.id)" title="Delete">✕</button>
      </div>
      <div v-if="!servers || servers.length === 0" class="server-empty">
        No servers yet. Click "+" to add one.
      </div>
    </div>

    <div class="sidebar-footer">
      <button class="footer-btn" @click="$emit('logs')" title="Logs">📋 {{ t('sidebar.logs') }}</button>
      <button class="footer-btn" @click="$emit('toggle-lang')" title="Switch Language">{{ locale === 'zh' ? 'EN' : '中' }}</button>
      <button class="footer-btn" @click="$emit('toggle-theme')" title="Toggle Dark/Light">{{ isDark ? '☀️' : '🌙' }}</button>
    </div>
  </nav>
</template>

<script setup>
import { computed, inject } from 'vue'
import { t, getLocale } from '../i18n.js'

const props = defineProps({
  servers: { type: Array, default: () => [] },
  statuses: { type: Object, default: () => ({}) },
  selectedId: { type: String, default: null },
  locale: { type: String, default: 'en' },
})
defineEmits(['select', 'add', 'edit', 'delete', 'logs', 'toggle-theme', 'toggle-lang'])
const isDark = computed(() => document.documentElement.getAttribute('data-theme') === 'dark')
function showContextMenu(e, s) {}
</script>

<style scoped>
.sidebar {
  width: var(--sidebar-width, 260px); min-width: var(--sidebar-width, 260px);
  background: var(--sidebar-bg); display: flex; flex-direction: column;
  border-right: 1px solid rgba(255,255,255,0.06);
  overflow: hidden; user-select: none;
}
.sidebar-header {
  display: flex; align-items: center; gap: 10px;
  padding: 16px 16px 12px; border-bottom: 1px solid rgba(255,255,255,0.06);
}
.logo {
  width: 32px; height: 32px; border-radius: 8px;
  background: var(--accent); color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 800; flex-shrink: 0;
}
.brand { font-size: 15px; font-weight: 700; color: #e0e0e0; letter-spacing: 0.5px; }
.sidebar-toolbar { padding: 10px 12px; }
.toolbar-btn {
  width: 100%; padding: 8px 12px; border: 1px dashed rgba(255,255,255,0.15);
  border-radius: 8px; background: transparent; color: var(--sidebar-text);
  cursor: pointer; display: flex; align-items: center; gap: 8px;
  font-size: 13px; transition: all 0.15s;
}
.toolbar-btn:hover { background: var(--sidebar-hover); border-color: var(--accent); color: #fff; }
.server-list-label {
  padding: 8px 16px 4px; font-size: 11px; font-weight: 600;
  color: rgba(255,255,255,0.3); text-transform: uppercase; letter-spacing: 1px;
}
.server-list { flex: 1; overflow-y: auto; padding: 4px 8px; }
.server-card {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px; border-radius: 6px;
  cursor: pointer; transition: all 0.12s;
  position: relative; margin-bottom: 2px;
}
.server-card:hover { background: var(--sidebar-hover); }
.server-card.selected { background: var(--sidebar-hover); }
.server-card.selected .server-name { color: #fff; }
.server-status {
  width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0;
  background: var(--text-muted); transition: all 0.3s;
}
.server-status.connected { background: var(--success-light); box-shadow: 0 0 6px var(--success-light); }
.server-status.disconnected { background: #555; }
.server-status.connecting { background: var(--warning-light); animation: pulse 1s infinite; }
.server-info { flex: 1; min-width: 0; }
.server-name { font-size: 13px; font-weight: 500; color: #d0d0d0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.server-host { font-size: 11px; color: rgba(255,255,255,0.3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.server-acl-badge { font-size: 10px; flex-shrink: 0; opacity: 0.5; }
.server-action-btn {
  width: 20px; height: 20px; border: none; background: transparent;
  color: rgba(255,255,255,0.2); cursor: pointer; border-radius: 3px;
  font-size: 11px; display: none; align-items: center; justify-content: center;
  flex-shrink: 0; padding: 0;
}
.server-card:hover .server-action-btn { display: flex; }
.server-action-btn:hover { color: #fff; background: rgba(255,255,255,0.1); }
.server-action-btn.danger:hover { color: var(--danger); background: rgba(198,40,40,0.15); }
.server-empty { padding: 20px 16px; font-size: 12px; color: rgba(255,255,255,0.25); text-align: center; }
.sidebar-footer {
  display: flex; border-top: 1px solid rgba(255,255,255,0.06); padding: 8px 12px; gap: 4px;
}
.footer-btn {
  flex: 1; padding: 6px; border: none; border-radius: 6px;
  background: transparent; color: var(--sidebar-text); cursor: pointer;
  font-size: 12px; text-align: center;
}
.footer-btn:hover { background: var(--sidebar-hover); color: #fff; }
@keyframes pulse { 0%,100% { opacity:1 } 50% { opacity:0.4 } }
</style>
