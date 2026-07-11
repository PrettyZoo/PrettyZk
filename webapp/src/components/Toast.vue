<template>
  <div class="toast-container">
    <TransitionGroup name="toast">
      <div v-for="t in toasts" :key="t.id" class="toast" :class="t.type">
        {{ t.message }}
      </div>
    </TransitionGroup>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

const toasts = ref([])
let id = 0

// Make toast globally accessible
window.__toast = (message, type = 'info', duration = 3000) => {
  const toastId = ++id
  toasts.value.push({ id: toastId, message, type })
  setTimeout(() => {
    const idx = toasts.value.findIndex(t => t.id === toastId)
    if (idx >= 0) toasts.value.splice(idx, 1)
  }, duration)
}
onUnmounted(() => {
  timers.forEach(t => clearTimeout(t))
  timers.length = 0
})
</script>

<style scoped>
.toast-container {
  position: fixed; bottom: 20px; left: 50%;
  transform: translateX(-50%); z-index: 10000;
  display: flex; flex-direction: column; gap: 8px;
  pointer-events: none;
}
.toast {
  padding: 10px 20px; border-radius: 6px;
  background: var(--toast-bg); color: var(--toast-text);
  font-size: 13px; box-shadow: 0 4px 12px rgba(0,0,0,0.2);
  pointer-events: auto; max-width: 400px;
}
.toast.error { background: var(--danger); }
.toast.success { background: var(--success); }
.toast.info { background: var(--sidebar-bg); }
.toast-enter-active { animation: toast-in 0.3s ease; }
.toast-leave-active { animation: toast-out 0.3s ease; }
@keyframes toast-in { from { opacity:0; transform:translateY(20px) } to { opacity:1; transform:translateY(0) } }
@keyframes toast-out { from { opacity:1 } to { opacity:0; transform:translateY(-10px) } }
</style>
