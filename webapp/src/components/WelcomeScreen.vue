<template>
  <div class="welcome">
    <div class="welcome-hero">
      <div class="welcome-icon">🦓</div>
      <h1>{{ t('welcome.title') }}</h1>
      <p class="welcome-sub">{{ t('welcome.subtitle') }}</p>
    </div>
    <div class="welcome-cards">
      <div class="welcome-card primary" @click="$emit('add')">
        <div class="card-icon-wrap">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="10"/><path d="M12 8v8M8 12h8"/></svg>
        </div>
        <div class="card-body">
          <div class="card-title">{{ t('welcome.newConn') }}</div>
          <div class="card-desc">{{ t('welcome.newConnDesc') }}</div>
        </div>
        <div class="card-arrow">→</div>
      </div>
      <div class="welcome-card" v-if="servers.length > 0">
        <div class="card-icon-wrap">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M22 19a2 2 0 01-2 2H4a2 2 0 01-2-2V5a2 2 0 012-2h5l2 3h9a2 2 0 012 2z"/></svg>
        </div>
        <div class="card-body">
          <div class="card-title">{{ t('welcome.recent') }}</div>
          <div class="card-desc">{{ t('welcome.recentDesc') }}</div>
        </div>
      </div>
    </div>
    <div class="welcome-footer">
      <span class="version-badge">v{{ version }}</span>
      <span class="footer-dot">·</span>
      <span>ZooKeeper GUI</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api.ts'
import { t } from '../i18n.ts'

defineProps({ servers: { type: Array, default: () => [] } })
defineEmits(['add'])

const version = ref('')
onMounted(async () => {
  try { const v = await api.version(); version.value = v.version } catch (e) { version.value = '3.0.0' }
})
</script>

<style scoped>
.welcome {
  display: flex; flex-direction: column; align-items: center;
  justify-content: center; height: 100%; padding: 48px 60px;
  text-align: center; user-select: none;
}
.welcome-hero { margin-bottom: 36px; }
.welcome-icon { font-size: 56px; margin-bottom: 12px; line-height: 1; }
.welcome h1 { font-size: 26px; font-weight: 700; color: var(--text-primary); margin-bottom: 6px; letter-spacing: -0.3px; }
.welcome-sub { font-size: 14px; color: var(--text-muted); line-height: 1.5; max-width: 360px; }

.welcome-cards { display: flex; gap: 14px; max-width: 560px; width: 100%; flex-direction: column; }
.welcome-card {
  display: flex; align-items: center; gap: 16px;
  padding: 18px 20px; border-radius: 12px;
  background: var(--card-bg); border: 1px solid var(--border-color);
  cursor: pointer; transition: all 0.2s ease; text-align: left;
}
.welcome-card:hover {
  border-color: var(--accent); box-shadow: 0 2px 16px rgba(0,0,0,0.06);
  transform: translateY(-1px);
}
.welcome-card.primary { border-color: var(--accent); background: var(--accent-subtle); }
.welcome-card.primary:hover { border-color: var(--accent-hover); box-shadow: 0 2px 20px rgba(70,159,149,0.12); }

.card-icon-wrap {
  width: 44px; height: 44px; border-radius: 10px;
  background: var(--accent-light); color: var(--accent);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.welcome-card.primary .card-icon-wrap { background: var(--accent); color: #fff; }
.card-body { flex: 1; min-width: 0; }
.card-title { font-size: 15px; font-weight: 600; color: var(--text-primary); margin-bottom: 2px; }
.card-desc { font-size: 12px; color: var(--text-muted); line-height: 1.5; }
.card-arrow { font-size: 18px; color: var(--accent); flex-shrink: 0; opacity: 0; transition: opacity 0.2s; }
.welcome-card:hover .card-arrow { opacity: 1; }

.welcome-footer {
  margin-top: 40px; display: flex; align-items: center; gap: 8px;
  font-size: 12px; color: var(--text-muted);
}
.version-badge {
  background: var(--accent-subtle); color: var(--accent);
  padding: 2px 8px; border-radius: 10px; font-weight: 600;
  font-size: 11px; letter-spacing: 0.2px;
}
</style>
