<template>
  <div class="dialog-overlay" @click.self="$emit('cancel')">
    <div
      class="dialog-box"
      role="dialog"
      aria-modal="true"
      :aria-labelledby="'dialog-title-' + _uid"
      :aria-describedby="'dialog-body-' + _uid"
    >
      <div class="dialog-header">
        <h3 :id="'dialog-title-' + _uid">{{ title }}</h3>
        <button class="dialog-close" @click="$emit('cancel')" aria-label="Close">&times;</button>
      </div>
      <div class="dialog-body" :id="'dialog-body-' + _uid">{{ message }}</div>
      <div class="dialog-footer">
        <button class="btn btn-secondary" @click="$emit('cancel')">{{ t('common.cancel') }}</button>
        <button class="btn btn-primary" ref="confirmBtn" @click="$emit('confirm')">{{ t('common.confirm') }}</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { t } from '../i18n.ts'

defineProps({ title: String, message: String })
defineEmits(['confirm', 'cancel'])

const confirmBtn = ref<HTMLElement | null>(null)

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    // emit cancel directly via the first close button's click
    const closeBtn = document.querySelector('.dialog-close') as HTMLElement
    closeBtn?.click()
  }
}

onMounted(() => {
  confirmBtn.value?.focus()
  document.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.dialog-overlay {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: var(--dialog-overlay); z-index: 9999;
  display: flex; align-items: center; justify-content: center;
}
.dialog-box {
  background: var(--bg-primary); border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.3);
  min-width: 400px; max-width: 600px;
}
.dialog-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px; border-bottom: 1px solid var(--border-color);
}
.dialog-header h3 { font-size: 15px; font-weight: 600; }
.dialog-close { border: none; background: transparent; font-size: 20px; cursor: pointer; color: var(--text-muted); }
.dialog-body { padding: 16px; font-size: 13px; line-height: 1.5; }
.dialog-footer { display: flex; justify-content: flex-end; gap: 8px; padding: 12px 16px; border-top: 1px solid var(--border-color); }
</style>
