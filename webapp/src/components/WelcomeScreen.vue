<template>
  <div class="welcome">
    <div class="welcome-hero">
      <img class="welcome-logo" :src="iconUrl" alt="PrettyZk" />
      <h1>{{ t('welcome.title') }}</h1>
      <p class="welcome-sub">{{ t('welcome.subtitle') }}</p>
    </div>

    <div class="welcome-actions">
      <n-button type="primary" size="large" @click="$emit('add')">
        <template #icon><Plus :size="17" /></template>
        {{ t('welcome.newConn') }}
      </n-button>
    </div>

    <div v-if="servers.length" class="welcome-recent">
      <div class="recent-label">{{ t('welcome.recent') }}</div>
      <button
        v-for="s in servers.slice(0, 6)"
        :key="s.id"
        class="recent-item"
        @click="$emit('select', s.id)"
      >
        <Server :size="15" class="recent-icon" />
        <span class="recent-name">{{ s.alias || s.host + ':' + s.port }}</span>
        <span class="recent-host">{{ s.host }}:{{ s.port }}</span>
      </button>
    </div>

    <div class="welcome-footer">v{{ version || '3.0.0' }} · ZooKeeper GUI</div>
  </div>
</template>

<script setup lang="ts">
import { NButton } from 'naive-ui'
import { Plus, Server } from 'lucide-vue-next'
import { t } from '../i18n.ts'
import iconUrl from '../assets/icon.png'

defineProps({
  servers: { type: Array, default: () => [] },
  version: { type: String, default: '' },
})
defineEmits(['add', 'select'])
</script>

<style scoped>
.welcome {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 48px;
  text-align: center;
  user-select: none;
  background: var(--bg-primary);
}
.welcome-hero {
  margin-bottom: 26px;
}
.welcome-logo {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  margin-bottom: 14px;
}
.welcome h1 {
  margin: 0 0 6px;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.3px;
  color: var(--text-primary);
}
.welcome-sub {
  margin: 0;
  max-width: 380px;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.6;
}
.welcome-actions {
  margin-bottom: 28px;
}
.welcome-recent {
  width: 100%;
  max-width: 420px;
  text-align: left;
}
.recent-label {
  margin-bottom: 8px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1.1px;
  text-transform: uppercase;
  color: var(--text-muted);
}
.recent-item {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 9px 12px;
  margin-bottom: 4px;
  border: 1px solid var(--border-color);
  border-radius: 7px;
  background: var(--card-bg);
  color: var(--text-primary);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s, background 0.15s;
}
.recent-item:hover {
  border-color: var(--accent);
  background: var(--accent-subtle);
}
.recent-icon {
  color: var(--text-muted);
  flex-shrink: 0;
}
.recent-name {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recent-host {
  font-size: 11px;
  color: var(--text-muted);
  flex-shrink: 0;
}
.welcome-footer {
  margin-top: 32px;
  font-size: 11px;
  color: var(--text-muted);
}
</style>
