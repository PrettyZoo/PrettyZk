<template>
  <div class="form-panel">
    <h2 style="margin-bottom:20px">{{ isEdit ? t('server.editServer') : t('server.newServer') }}</h2>

    <div class="form-row">
      <div class="form-group">
        <label>{{ t('server.host') }}</label>
        <input v-model="form.host" type="text" placeholder="localhost" />
      </div>
      <div class="form-group" style="max-width:120px">
        <label>{{ t('server.port') }}</label>
        <input v-model.number="form.port" type="number" placeholder="2181" />
      </div>
    </div>

    <div class="form-group">
      <label>{{ t('server.alias') }}</label>
      <input v-model="form.alias" type="text" placeholder="My Server" />
    </div>

    <div class="form-group">
      <label>{{ t('server.acl') }}</label>
      <textarea v-model="form.acl" placeholder="digest:user:password:cdrwa"></textarea>
    </div>

    <div class="form-group inline">
      <label class="toggle-switch">
        <input v-model="form.sshEnabled" type="checkbox" />
        <span class="toggle-slider"></span>
      </label>
      <span>{{ t('server.sshTunnel') }}</span>
    </div>

    <div v-if="form.sshEnabled" id="ssh-config">
      <div class="form-row">
        <div class="form-group">
          <label>{{ t('server.sshHost') }}</label>
          <input v-model="form.sshHost" type="text" />
        </div>
        <div class="form-group" style="max-width:100px">
          <label>{{ t('server.sshPort') }}</label>
          <input v-model.number="form.sshPort" type="number" value="22" />
        </div>
      </div>
      <div class="form-group">
        <label>{{ t('server.sshUser') }}</label>
        <input v-model="form.sshUsername" type="text" />
      </div>
      <div class="form-group">
        <label>{{ t('server.sshPass') }}</label>
        <input v-model="form.sshPassword" type="password" />
      </div>
      <div class="form-row">
        <div class="form-group">
          <label>{{ t('server.remoteHost') }}</label>
          <input v-model="form.remoteHost" type="text" placeholder="127.0.0.1" />
        </div>
        <div class="form-group" style="max-width:100px">
          <label>{{ t('server.remotePort') }}</label>
          <input v-model.number="form.remotePort" type="number" value="2181" />
        </div>
      </div>
    </div>

    <div class="form-group" style="max-width:200px">
      <label>{{ t('server.zkVersion') }}</label>
      <select v-model="form.zkVersion">
        <option value="auto">{{ t('server.zkVersionAuto') }}</option>
        <option value="3.4">{{ t('server.zkVersion34') }}</option>
        <option value="3.5">{{ t('server.zkVersion35') }}</option>
        <option value="3.6">{{ t('server.zkVersion36') }}</option>
      </select>
    </div>

    <h3 style="margin:16px 0 8px;font-size:13px">{{ t('server.advConfig') }}</h3>
    <div class="form-row">
      <div class="form-group"><label>{{ t('server.connTimeout') }}</label><input v-model.number="form.connTimeout" type="number" /></div>
      <div class="form-group"><label>{{ t('server.sessionTimeout') }}</label><input v-model.number="form.sessionTimeout" type="number" /></div>
    </div>
    <div class="form-row">
      <div class="form-group"><label>{{ t('server.maxRetries') }}</label><input v-model.number="form.maxRetries" type="number" /></div>
      <div class="form-group"><label>{{ t('server.retryInterval') }}</label><input v-model.number="form.retryInterval" type="number" /></div>
    </div>

    <div class="form-actions">
      <button class="btn btn-primary" @click="save" :disabled="saving">{{ saving ? '...' : t('server.save') }}</button>
      <button class="btn" @click="$emit('cancel')">{{ t('server.cancel') }}</button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { api } from '../api.js'
import { t } from '../i18n.js'

const props = defineProps({ serverId: { type: String, default: null } })
const emit = defineEmits(['saved', 'cancel'])

const isEdit = ref(!!props.serverId)
const saving = ref(false)

const form = reactive({
  host: 'localhost', port: 2181, alias: '', acl: '',
  sshEnabled: false, sshHost: '', sshPort: 22, sshUsername: '', sshPassword: '',
  remoteHost: '', remotePort: 2181,
  connTimeout: 60000, sessionTimeout: 60000, maxRetries: 3, retryInterval: 1000, zkVersion: 'auto',
})

onMounted(async () => {
  if (props.serverId) {
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
      if (s.maxRetries) form.maxRetries = s.maxRetries
      if (s.retryIntervalTime) form.retryInterval = s.retryIntervalTime
    } catch (e) {
      window.__toast?.('Failed to load server: ' + e.message, 'error')
    }
  }
})

async function save() {
  saving.value = true
  try {
    const data = {
      id: props.serverId || '',
      zkHost: form.host, zkPort: form.port, zkAlias: form.alias, acl: form.acl,
      sshEnabled: form.sshEnabled,
      sshHost: form.sshHost, sshPort: form.sshPort,
      sshUsername: form.sshUsername, sshPassword: form.sshPassword,
      remoteHost: form.remoteHost, remotePort: form.remotePort,
      connectionTimeout: form.connTimeout, sessionTimeout: form.sessionTimeout,
      maxRetries: form.maxRetries, retryIntervalTime: form.retryInterval,
      zkVersion: form.zkVersion,
    }
    const result = await api.saveServer(data)
    window.__toast?.('Server saved', 'success')
    emit('saved', result.id)
  } catch (e) {
    window.__toast?.('Failed to save: ' + e.message, 'error')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.form-panel { padding: 24px; overflow-y: auto; height: 100%; }
.form-group { margin-bottom: 14px; }
.form-group label { display: block; margin-bottom: 4px; font-size: 12px; font-weight: 500; color: var(--text-secondary); }
.form-group input, .form-group textarea, .form-group select {
  width: 100%; padding: 8px 10px;
  border: 1px solid var(--input-border); border-radius: 4px;
  background: var(--input-bg); color: var(--text-primary);
  font-size: 13px; outline: none; transition: border-color 0.15s;
}
.form-group input:focus, .form-group textarea:focus { border-color: var(--input-focus); }
.form-group textarea { min-height: 60px; resize: vertical; font-family: monospace; font-size: 12px; }
.form-group.inline { display: flex; gap: 8px; align-items: center; }
.form-row { display: flex; gap: 12px; }
.form-row .form-group { flex: 1; }
.form-actions { display: flex; gap: 8px; padding-top: 12px; border-top: 1px solid var(--border-color); }
.toggle-switch { position: relative; display: inline-block; width: 36px; height: 20px; }
.toggle-switch input { opacity: 0; width: 0; height: 0; }
.toggle-slider {
  position: absolute; cursor: pointer; top:0; left:0; right:0; bottom:0;
  background: var(--text-muted); border-radius: 20px; transition: 0.3s;
}
.toggle-slider:before {
  content: ""; position: absolute; height: 16px; width: 16px;
  left: 2px; bottom: 2px; background: white; border-radius: 50%; transition: 0.3s;
}
.toggle-switch input:checked + .toggle-slider { background: var(--accent); }
.toggle-switch input:checked + .toggle-slider:before { transform: translateX(16px); }
</style>
