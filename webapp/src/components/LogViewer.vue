<template>
  <div class="log-viewer">
    <div class="toolbar">
      <div class="breadcrumb">{{ t('log.title') }}</div>
      <div class="toolbar-actions">
        <button class="btn btn-small" @click="clearLogs">{{ t('log.clear') }}</button>
        <button class="btn btn-small" @click="emit('back')">{{ t('node.back') }}</button>
      </div>
    </div>
    <div class="log-scroll">
      <n-virtual-list
        v-if="logs.length"
        ref="listRef"
        :items="logs"
        :item-size="20"
        :item-resizable="true"
        key-field="id"
        class="log-virtual"
      >
        <template #default="{ item }">
          <div class="log-line">{{ item.text }}</div>
        </template>
      </n-virtual-list>
      <div v-else class="log-empty">{{ t('log.connecting') }}</div>
      <div v-if="connected" class="log-status">{{ t('log.streaming') }}</div>
      <div v-else class="log-status">{{ t('log.connecting') }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { NVirtualList } from 'naive-ui'
import { t } from '../i18n.ts'

const emit = defineEmits(['back'])
const MAX_LINES = 10000
const logs = ref<{ id: number; text: string }[]>([])
const connected = ref(false)
const listRef = ref<any>(null)
let eventSource: EventSource | null = null
let nextId = 0

onMounted(() => {
  try {
    eventSource = new EventSource('/api/logs/stream')
    eventSource.addEventListener('log', (event) => {
      logs.value.push({ id: ++nextId, text: (event as MessageEvent).data })
      if (logs.value.length > MAX_LINES) {
        logs.value.splice(0, logs.value.length - MAX_LINES)
      }
      nextTick(() => {
        listRef.value?.scrollTo({ position: 'bottom' })
      })
    })
    eventSource.onopen = () => {
      connected.value = true
    }
    eventSource.onerror = () => {
      connected.value = false
    }
  } catch (e: any) {
    logs.value.push({ id: ++nextId, text: t('log.connectFailed', { msg: e.message }) })
  }
})

onUnmounted(() => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
})

function clearLogs() {
  logs.value = []
}
</script>

<style scoped>
.log-viewer {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.toolbar {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
  gap: 8px;
  min-height: 40px;
  flex-shrink: 0;
}
.breadcrumb {
  font-size: 13px;
  color: var(--text-secondary);
  flex: 1;
}
.toolbar-actions {
  display: flex;
  gap: 4px;
}
.log-scroll {
  flex: 1;
  min-height: 0;
  position: relative;
  background: var(--bg-primary);
}
.log-virtual {
  height: 100%;
}
.log-line {
  padding: 0 8px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 20px;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--text-primary);
}
.log-empty {
  padding: 24px;
  text-align: center;
  color: var(--text-muted);
  font-size: 12px;
}
.log-status {
  position: absolute;
  left: 8px;
  bottom: 4px;
  padding: 2px 7px;
  border-radius: 4px;
  background: var(--bg-secondary);
  color: var(--text-muted);
  font-size: 10px;
  pointer-events: none;
}
</style>
