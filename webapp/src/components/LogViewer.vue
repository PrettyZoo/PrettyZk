<template>
  <div style="display:flex;flex-direction:column;height:100%">
    <div class="toolbar">
      <div class="breadcrumb">{{ t('log.title') }}</div>
      <div class="toolbar-actions">
        <button class="btn btn-small" @click="clearLogs">{{ t('log.clear') }}</button>
        <button class="btn btn-small" @click="emit('back')">{{ t('node.back') }}</button>
      </div>
    </div>
    <div class="log-scroll" ref="logContainer">
      <div v-for="(line, i) in logs" :key="i" class="log-line">{{ line }}</div>
      <div v-if="connected" class="log-line" style="color:var(--text-muted);font-size:11px">{{ t('log.streaming') }}</div>
      <div v-else class="log-line" style="color:var(--text-muted)">Connecting...</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { t } from '../i18n.ts'

const emit = defineEmits(['back'])
const logs = ref([])
const connected = ref(false)
const logContainer = ref(null)
let eventSource = null

onMounted(() => {
  try {
    eventSource = new EventSource('/api/logs/stream')
    eventSource.addEventListener('log', (e) => {
      logs.value.push(e.data)
      nextTick(() => {
        if (logContainer.value) {
          logContainer.value.scrollTop = logContainer.value.scrollHeight
        }
      })
    })
    eventSource.onopen = () => { connected.value = true }
    eventSource.onerror = () => { connected.value = false }
  } catch (e) {
    logs.value.push('Failed to connect: ' + e.message)
  }
})

onUnmounted(() => {
  if (eventSource) eventSource.close()
})

function clearLogs() {
  logs.value = []
}
</script>

<style scoped>
.toolbar {
  display: flex; align-items: center;
  padding: 8px 12px; background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color); gap: 8px; min-height: 40px;
}
.breadcrumb { font-size: 13px; color: var(--text-secondary); flex: 1; }
.toolbar-actions { display: flex; gap: 4px; }
.log-scroll {
  flex: 1; overflow-y: auto;
  font-family: 'SF Mono', Monaco, Menlo, Consolas, monospace;
  font-size: 12px; padding: 8px; background: var(--bg-primary);
}
.log-line { line-height: 1.5; white-space: pre-wrap; word-break: break-all; }
</style>
