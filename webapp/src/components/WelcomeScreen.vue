<template>
  <div class="welcome">
    <div class="welcome-icon">🦓</div>
    <h1>{{ t('welcome.title') }}</h1>
    <p class="welcome-sub">{{ t('welcome.subtitle') }}</p>
    <div class="welcome-cards">
      <div class="welcome-card" @click="$emit('add')">
        <div class="card-icon">+</div>
        <div class="card-title">{{ t('welcome.newConn') }}</div>
        <div class="card-desc">{{ t('welcome.newConnDesc') }}</div>
      </div>
      <div class="welcome-card" v-if="servers.length > 0">
        <div class="card-icon">📂</div>
        <div class="card-title">{{ t('welcome.recent') }}</div>
        <div class="card-desc">{{ t('welcome.recentDesc') }}</div>
      </div>
    </div>
    <p class="welcome-footer">PrettyZk v{{ version }}</p>
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
  try { const v = await api.version(); version.value = v.version } catch (e) { version.value = '2.1.1' }
})
</script>

<style scoped>
.welcome { display:flex; flex-direction:column; align-items:center; justify-content:center; height:100%; padding:40px; text-align:center; }
.welcome-icon { font-size:48px; margin-bottom:16px; }
.welcome h1 { font-size:28px; font-weight:700; color:var(--text-primary); margin-bottom:4px; }
.welcome-sub { font-size:14px; color:var(--text-muted); margin-bottom:40px; }
.welcome-cards { display:flex; gap:16px; max-width:600px; width:100%; }
.welcome-card { flex:1; padding:24px; border-radius:12px; background:var(--card-bg); border:1px solid var(--border-color); cursor:pointer; transition:all 0.2s; text-align:left; }
.welcome-card:hover { border-color:var(--accent); box-shadow:0 4px 12px rgba(0,0,0,0.1); transform:translateY(-2px); }
.card-icon { font-size:24px; margin-bottom:8px; }
.card-title { font-size:15px; font-weight:600; color:var(--text-primary); margin-bottom:4px; }
.card-desc { font-size:12px; color:var(--text-muted); line-height:1.5; }
.welcome-footer { margin-top:32px; font-size:12px; color:var(--text-muted); }
</style>
