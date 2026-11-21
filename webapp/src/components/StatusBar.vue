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
      <button
        class="status-version"
        :disabled="checking"
        :title="t('status.checkUpdate')"
        @click="checkUpdate"
      >
        <span v-if="checking" class="status-spinner"></span>
        v{{ version || '3.0.0' }}
      </button>
      <span class="status-separator">·</span>
      <span>ZooKeeper GUI</span>
    </div>
  </footer>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { t } from '../i18n.ts'
import { checkForUpdates } from '../updates.ts'

const props = defineProps({
  server: { type: Object, default: null },
  status: { type: String, default: '' },
  version: { type: String, default: '' },
})

const checking = ref(false)

const dotClass = computed(() => props.status || 'disconnected')
const stateLabel = computed(() => {
  if (!props.server) return t('status.ready')
  if (props.status === 'connected') return t('server.connected')
  if (props.status === 'connecting') return t('server.connecting')
  return t('server.disconnected')
})

async function checkUpdate() {
  if (checking.value) return
  checking.value = true
  try {
    await checkForUpdates({ silent: false })
  } finally {
    checking.value = false
  }
}
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
  border: none;
  background: transparent;
  padding: 0 2px;
  border-radius: 3px;
  color: var(--text-secondary);
  font: inherit;
  cursor: pointer;
}
.status-version:hover {
  color: var(--accent);
}
.status-version:disabled {
  opacity: 0.6;
  cursor: default;
}
.status-spinner {
  display: inline-block;
  width: 9px;
  height: 9px;
  margin-right: 4px;
  border: 1.5px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: status-spin 0.7s linear infinite;
  vertical-align: -1px;
}
@keyframes status-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
