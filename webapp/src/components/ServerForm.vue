<template>
  <div class="form-panel">
    <div class="form-header">
      <div>
        <h2>{{ isEdit ? t('server.editServer') : t('server.newServer') }}</h2>
        <p class="form-subtitle">{{ isEdit ? form.host + ':' + form.port : t('welcome.newConnDesc') }}</p>
      </div>
      <n-button quaternary size="small" @click="$emit('cancel')">
        <template #icon><ArrowLeft :size="15" /></template>
        {{ t('server.cancel') }}
      </n-button>
    </div>

    <n-form :model="form" label-placement="top" :show-require-mark="false" class="server-form">
      <div class="form-row">
        <n-form-item :label="t('server.host')" class="grow">
          <n-input v-model:value="form.host" placeholder="localhost" />
        </n-form-item>
        <n-form-item :label="t('server.port')" class="port">
          <n-input-number v-model:value="form.port" :min="1" :max="65535" style="width: 100%" />
        </n-form-item>
      </div>

      <n-form-item :label="t('server.alias')">
        <n-input v-model:value="form.alias" placeholder="My Server" />
      </n-form-item>

      <n-form-item :label="t('server.acl')">
        <n-input
          v-model:value="form.acl"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 4 }"
          placeholder="digest:user:password:cdrwa"
        />
      </n-form-item>

      <n-form-item :label="t('server.sshTunnel')">
        <n-switch v-model:value="form.sshEnabled" />
      </n-form-item>

      <div v-if="form.sshEnabled" class="ssh-section">
        <div class="form-row">
          <n-form-item :label="t('server.sshHost')" class="grow">
            <n-input v-model:value="form.sshHost" />
          </n-form-item>
          <n-form-item :label="t('server.sshPort')" class="port">
            <n-input-number v-model:value="form.sshPort" :min="1" :max="65535" style="width: 100%" />
          </n-form-item>
        </div>
        <n-form-item :label="t('server.sshUser')">
          <n-input v-model:value="form.sshUsername" />
        </n-form-item>
        <n-form-item :label="t('server.sshPass')">
          <n-input v-model:value="form.sshPassword" type="password" show-password-on="click" />
        </n-form-item>
        <div class="form-row">
          <n-form-item :label="t('server.remoteHost')" class="grow">
            <n-input v-model:value="form.remoteHost" placeholder="127.0.0.1" />
          </n-form-item>
          <n-form-item :label="t('server.remotePort')" class="port">
            <n-input-number v-model:value="form.remotePort" :min="1" :max="65535" style="width: 100%" />
          </n-form-item>
        </div>
      </div>

      <n-form-item :label="t('server.zkVersion')" class="version">
        <n-select v-model:value="form.zkVersion" :options="zkOptions" />
      </n-form-item>

      <div class="section-title">{{ t('server.advConfig') }}</div>
      <div class="form-row">
        <n-form-item :label="t('server.connTimeout')" class="grow">
          <n-input-number v-model:value="form.connTimeout" :min="1000" style="width: 100%" />
        </n-form-item>
        <n-form-item :label="t('server.sessionTimeout')" class="grow">
          <n-input-number v-model:value="form.sessionTimeout" :min="1000" style="width: 100%" />
        </n-form-item>
      </div>
      <div class="form-row">
        <n-form-item :label="t('server.maxRetries')" class="grow">
          <n-input-number v-model:value="form.maxRetries" :min="0" style="width: 100%" />
        </n-form-item>
        <n-form-item :label="t('server.retryInterval')" class="grow">
          <n-input-number v-model:value="form.retryInterval" :min="0" style="width: 100%" />
        </n-form-item>
      </div>

      <div class="form-actions">
        <n-button type="primary" :loading="saving" @click="save">
          <template #icon><Save :size="15" /></template>
          {{ t('server.save') }}
        </n-button>
        <n-button @click="$emit('cancel')">{{ t('server.cancel') }}</n-button>
      </div>
    </n-form>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { NButton, NForm, NFormItem, NInput, NInputNumber, NSelect, NSwitch } from 'naive-ui'
import { ArrowLeft, Save } from 'lucide-vue-next'
import { api } from '../api.ts'
import { t } from '../i18n.ts'
import { toast } from '../composables/feedback.ts'

const props = defineProps({ serverId: { type: String, default: null } })
const emit = defineEmits(['saved', 'cancel'])

const isEdit = ref(!!props.serverId)
const saving = ref(false)

const form = reactive({
  host: 'localhost',
  port: 2181,
  alias: '',
  acl: '',
  sshEnabled: false,
  sshHost: '',
  sshPort: 22,
  sshUsername: '',
  sshPassword: '',
  remoteHost: '',
  remotePort: 2181,
  connTimeout: 60000,
  sessionTimeout: 60000,
  maxRetries: 3,
  retryInterval: 1000,
  zkVersion: 'auto',
})

const zkOptions = computed(() => [
  { label: t('server.zkVersionAuto'), value: 'auto' },
  { label: t('server.zkVersion34'), value: '3.4' },
  { label: t('server.zkVersion35'), value: '3.5' },
  { label: t('server.zkVersion36'), value: '3.6' },
])

onMounted(async () => {
  if (!props.serverId) return
  try {
    const s = await api.getServer(props.serverId)
    form.host = s.host || 'localhost'
    form.port = s.port || 2181
    form.alias = s.alias || ''
    form.acl = s.acl || ''
    form.sshEnabled = s.sshEnabled || false
    if (s.sshEnabled) {
      form.sshHost = s.sshHost || ''
      form.sshPort = s.sshPort || 22
      form.sshUsername = s.sshUsername || ''
      form.remoteHost = s.remoteHost || ''
      form.remotePort = s.remotePort || 2181
    }
    if (s.connectionTimeout) form.connTimeout = s.connectionTimeout
    if (s.sessionTimeout) form.sessionTimeout = s.sessionTimeout
    if (s.maxRetries != null) form.maxRetries = s.maxRetries
    if (s.retryIntervalTime != null) form.retryInterval = s.retryIntervalTime
    if (s.zkVersion) form.zkVersion = s.zkVersion
  } catch (e: any) {
    toast(t('server.loadFailed', { msg: e.message }), 'error')
  }
})

async function save() {
  saving.value = true
  try {
    const data = {
      id: props.serverId || '',
      zkHost: form.host,
      zkPort: form.port,
      zkAlias: form.alias,
      acl: form.acl,
      sshEnabled: form.sshEnabled,
      sshHost: form.sshHost,
      sshPort: form.sshPort,
      sshUsername: form.sshUsername,
      sshPassword: form.sshPassword,
      remoteHost: form.remoteHost,
      remotePort: form.remotePort,
      connectionTimeout: form.connTimeout,
      sessionTimeout: form.sessionTimeout,
      maxRetries: form.maxRetries,
      retryIntervalTime: form.retryInterval,
      zkVersion: form.zkVersion,
    }
    const result = await api.saveServer(data)
    toast(t('server.saved'), 'success')
    emit('saved', result.id)
  } catch (e: any) {
    toast(t('server.saveFailed', { msg: e.message }), 'error')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.form-panel {
  padding: 22px 28px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}
.form-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}
.form-header h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 650;
  color: var(--text-primary);
}
.form-subtitle {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.server-form {
  max-width: 620px;
}
.form-row {
  display: flex;
  gap: 12px;
}
.grow {
  flex: 1;
}
.port {
  width: 120px;
  flex-shrink: 0;
}
.version {
  width: 220px;
}
.ssh-section {
  padding: 10px 12px 2px;
  margin-bottom: 14px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-secondary);
}
.section-title {
  margin: 6px 0 12px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1px;
  text-transform: uppercase;
  color: var(--text-muted);
}
.form-actions {
  display: flex;
  gap: 8px;
  padding-top: 14px;
  margin-top: 6px;
  border-top: 1px solid var(--border-color);
}
</style>
