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
  width: var(--sidebar-width, 270px); min-width: var(--sidebar-width, 270px);
  background: var(--sidebar-bg); display: flex; flex-direction: column;
  border-right: 1px solid rgba(255,255,255,0.04);
  overflow: hidden; user-select: none;
}
.sidebar-header {
  display: flex; align-items: center; gap: 10px;
  padding: 18px 16px 14px; border-bottom: 1px solid rgba(255,255,255,0.05);
}
.logo {
  width: 32px; height: 32px; border-radius: 8px;
  background: linear-gradient(135deg, var(--accent), var(--accent-hover));
  color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 800; flex-shrink: 0;
  letter-spacing: 0;
}
.brand { font-size: 15px; font-weight: 700; color: #e8e8ee; letter-spacing: 0.3px; }
.sidebar-toolbar { padding: 12px 12px 8px; }
.toolbar-btn {
  width: 100%; padding: 9px 12px; border: 1px dashed rgba(255,255,255,0.1);
  border-radius: 8px; background: transparent; color: var(--sidebar-text);
  cursor: pointer; display: flex; align-items: center; gap: 8px;
  font-size: 13px; font-weight: 500; transition: all 0.15s;
}
.toolbar-btn:hover { background: var(--sidebar-hover); border-color: var(--accent); color: #e8e8ee; }
.sidebar-toolbar + .server-list-label { padding-top: 4px; }
.server-list-label {
  padding: 12px 16px 6px; font-size: 10px; font-weight: 700;
  color: rgba(255,255,255,0.2); text-transform: uppercase; letter-spacing: 1.2px;
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
  background: #3a3b4a; transition: all 0.3s;
}
.server-status.connected { background: var(--success-light); box-shadow: 0 0 6px rgba(76,175,128,0.4); }
.server-status.disconnected { background: #3a3b4a; }
.server-status.connecting { background: var(--warning-light); animation: pulse 1s infinite; }
.server-info { flex: 1; min-width: 0; }
.server-name { font-size: 13px; font-weight: 500; color: #c8cad0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.server-host { font-size: 11px; color: rgba(255,255,255,0.2); margin-top: 1px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.server-action-btn {
  width: 22px; height: 22px; border: none; background: transparent;
  color: rgba(255,255,255,0.15); cursor: pointer; border-radius: 4px;
  font-size: 11px; display: none; align-items: center; justify-content: center;
  flex-shrink: 0; padding: 0; transition: all 0.12s;
}
.server-card:hover .server-action-btn { display: flex; }
.server-action-btn:hover { color: #c8cad0; background: rgba(255,255,255,0.06); }
.server-action-btn.danger:hover { color: var(--danger); background: rgba(211,47,47,0.12); }
.server-empty { padding: 24px 16px; font-size: 12px; color: rgba(255,255,255,0.15); text-align: center; line-height: 1.6; }
.sidebar-footer {
  display: flex; border-top: 1px solid rgba(255,255,255,0.04); padding: 8px 10px; gap: 4px;
}
.footer-btn {
  flex: 1; padding: 7px 4px; border: none; border-radius: 6px;
  background: transparent; color: var(--sidebar-text); cursor: pointer;
  font-size: 12px; text-align: center; transition: all 0.12s;
}
.footer-btn:hover { background: var(--sidebar-hover); color: #c8cad0; }
@keyframes pulse { 0%,100% { opacity:1 } 50% { opacity:0.4 } }
</style>
