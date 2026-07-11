<template>
  <div class="dialog-overlay" @click.self="onCancel">
    <div class="dialog-box">
      <div class="dialog-header">
        <h3>{{ titleKey ? t(titleKey) : 'Dialog' }}</h3>
        <button class="dialog-close" @click="onCancel">&times;</button>
      </div>
      <div class="dialog-body">
        <div class="form-group" v-for="(field, idx) in fields" :key="idx">
          <label>{{ field.label }}</label>
          <input v-if="field.type === 'text'" v-model="formData[field.key]" type="text" :placeholder="field.placeholder" />
          <select v-else-if="field.type === 'select'" v-model="formData[field.key]">
            <option v-for="opt in field.options" :key="opt.value ?? opt" :value="opt.value ?? opt">{{ opt.label ?? opt }}</option>
          </select>
          <textarea v-else-if="field.type === 'textarea'" v-model="formData[field.key]" :placeholder="field.placeholder" rows="4"></textarea>
        </div>
      </div>
      <div class="dialog-footer">
        <button class="btn" @click="onCancel">{{ t('common.cancel') }}</button>
        <button class="btn btn-primary" @click="onConfirm" :disabled="!valid">{{ t(confirmKey || 'common.confirm') }}</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, computed } from 'vue'
import { t } from '../i18n.ts'

const props = defineProps({
  titleKey: { type: String, default: '' },
  confirmKey: { type: String, default: 'common.confirm' },
  fields: { type: Array, required: true },
})

const emit = defineEmits(['confirm', 'cancel'])

const formData = reactive({})

// Initialize form data from fields
function initFormData() {
  for (const f of props.fields) {
    if (!(f.key in formData)) {
      formData[f.key] = f.default ?? ''
    }
  }
}
initFormData()

const valid = computed(() => {
  for (const f of props.fields) {
    if (f.required) {
      const val = formData[f.key]
      if (val === undefined || val === null || (typeof val === 'string' && val.trim() === '')) {
        return false
      }
    }
  }
  return true
})

function onConfirm() {
  emit('confirm', { ...formData })
}

function onCancel() {
  emit('cancel')
}
</script>

<style scoped>
.dialog-overlay {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: var(--dialog-overlay); z-index: 9999;
  display: flex; align-items: center; justify-content: center;
}
.dialog-box {
  background: var(--bg-primary); border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.25);
  min-width: 420px; max-width: 520px; width: 100%;
}
.dialog-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 18px; border-bottom: 1px solid var(--border-color);
}
.dialog-header h3 { font-size: 15px; font-weight: 600; }
.dialog-close { border: none; background: transparent; font-size: 20px; cursor: pointer; color: var(--text-muted); padding:0 4px; }
.dialog-close:hover { color: var(--text-primary); }
.dialog-body { padding: 18px; max-height: 400px; overflow-y: auto; }
.dialog-footer {
  display: flex; justify-content: flex-end; gap: 8px;
  padding: 12px 18px; border-top: 1px solid var(--border-color);
}
.form-group { margin-bottom: 14px; }
.form-group:last-child { margin-bottom: 0; }
.form-group label { display: block; margin-bottom: 4px; font-size: 12px; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.3px; }
.form-group input, .form-group select, .form-group textarea {
  width: 100%; padding: 8px 10px;
  border: 1px solid var(--input-border); border-radius: 6px;
  background: var(--input-bg); color: var(--text-primary);
  font-size: 13px; outline: none; transition: border-color 0.2s;
}
.form-group input:focus, .form-group select:focus, .form-group textarea:focus {
  border-color: var(--input-focus); box-shadow: 0 0 0 2px var(--accent-light);
}
.form-group textarea { resize: vertical; font-family: 'SF Mono', Monaco, Consolas, monospace; font-size: 12px; }
</style>
