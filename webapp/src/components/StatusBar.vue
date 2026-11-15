<template>
  <footer class="status-bar">
    <div class="status-left">
      <span class="status-dot" :class="dotClass"></span>
      <span class="status-name">
        {{ server ? (server.alias || server.host + ':' + server.port) : t('status.noServer') }}
      </span>
      <span class="status-separator">·</span>
      <span class="status-state">{{ stateLabel }}</span>
    </div>
    <div class="status-right">
      <span class="status-version">v{{ version || '3.0.0' }}</span>
      <span class="status-separator">·</span>
      <span>ZooKeeper GUI</span>
    </div>
  </footer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { t } from '../i18n.ts'

const props = defineProps({
  server: { type: Object, default: null },
  status: { type: String, default: '' },
  version: { type: String, default: '' },
})

const dotClass = computed(() => props.status || 'disconnected')
const stateLabel = computed(() => {
  if (!props.server) return t('status.ready')
  if (props.status === 'connected') return t('server.connected')
  if (props.status === 'connecting') return t('server.connecting')
  return t('server.disconnected')
})
</script>

<style scoped>
.status-bar {
  height: 26px;
  min-height: 26px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  border-top: 1px solid var(--border-color);
  background: var(--toolbar-bg);
  color: var(--text-muted);
  font-size: 11px;
  user-select: none;
}
.status-left,
.status-right {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.status-name {
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.status-state {
  color: var(--text-muted);
}
.status-separator {
  opacity: 0.5;
}
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-muted);
  flex-shrink: 0;
}
.status-dot.connected {
  background: var(--success);
}
.status-dot.connecting {
  background: var(--warning);
}
.status-dot.disconnected {
  background: var(--text-muted);
}
.status-version {
  color: var(--text-secondary);
}
</style>
