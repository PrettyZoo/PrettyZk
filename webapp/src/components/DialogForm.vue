<template>
  <n-modal
    :show="true"
    preset="card"
    :title="titleKey ? t(titleKey) : 'Dialog'"
    :bordered="false"
    style="width: 520px; max-width: 92vw"
    @update:show="onCancel"
  >
    <n-form label-placement="top" :show-require-mark="false">
      <n-form-item v-for="field in fields" :key="field.key" :label="field.label">
        <n-input
          v-if="field.type === 'text'"
          v-model:value="formData[field.key]"
          :placeholder="field.placeholder"
        />
        <n-select
          v-else-if="field.type === 'select'"
          v-model:value="formData[field.key]"
          :options="normalizeOptions(field.options)"
        />
        <n-input
          v-else-if="field.type === 'textarea'"
          v-model:value="formData[field.key]"
          type="textarea"
          :autosize="{ minRows: 4, maxRows: 10 }"
          :placeholder="field.placeholder"
        />
      </n-form-item>
    </n-form>

    <template #footer>
      <div class="dialog-footer">
        <n-button @click="onCancel">{{ t('common.cancel') }}</n-button>
        <n-button type="primary" :disabled="!valid" @click="onConfirm">
          {{ t(confirmKey || 'common.confirm') }}
        </n-button>
      </div>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue'
import { NButton, NForm, NFormItem, NInput, NModal, NSelect } from 'naive-ui'
import { t } from '../i18n.ts'

const props = defineProps({
  titleKey: { type: String, default: '' },
  confirmKey: { type: String, default: 'common.confirm' },
  fields: { type: Array, required: true },
})

const emit = defineEmits(['confirm', 'cancel'])

const formData = reactive<Record<string, any>>({})
for (const field of props.fields as any[]) {
  formData[field.key] = field.default ?? ''
}

const valid = computed(() => {
  for (const field of props.fields as any[]) {
    if (!field.required) continue
    const value = formData[field.key]
    if (
      value === undefined ||
      value === null ||
      (typeof value === 'string' && value.trim() === '')
    ) {
      return false
    }
  }
  return true
})

function normalizeOptions(options: any[] = []) {
  return options.map((option) =>
    typeof option === 'string' ? { label: option, value: option } : option,
  )
}

function onConfirm() {
  if (!valid.value) return
  emit('confirm', { ...formData })
}

function onCancel() {
  emit('cancel')
}
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
