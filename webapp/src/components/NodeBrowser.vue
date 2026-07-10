<template>
  <div class="node-browser">
    <!-- Tabs -->
    <div class="tab-bar">
      <div class="tab-item" :class="{ active: activeTab === 'browse' }" @click="activeTab = 'browse'">
        <span class="tab-icon">📁</span> {{ t('node.browse') }}
      </div>
      <div class="tab-item" :class="{ active: activeTab === 'terminal' }" @click="activeTab = 'terminal'">
        <span class="tab-icon">💻</span> {{ t('node.terminal') }}
      </div>
      <div class="tab-item" :class="{ active: activeTab === '4lc' }" @click="activeTab = '4lc'">
        <span class="tab-icon">🔧</span> {{ t('node.fourLetterCmd') }}
      </div>
    </div>

    <!-- Browse -->
    <div v-show="activeTab === 'browse'" class="tab-panel">
      <div class="toolbar">
        <div class="breadcrumb">
          <span class="crumb" @click="loadRoot">/</span>
          <template v-for="(part, i) in pathParts" :key="i">
            <span class="crumb-sep">/</span>
            <span class="crumb" @click="navigateToPath(part.fullPath)">{{ part.name }}</span>
          </template>
        </div>
        <div class="toolbar-actions">
          <button class="btn btn-small" @click="sync" :disabled="loading">⟳ {{ t('node.sync') }}</button>
          <button class="btn btn-small btn-primary" @click="showAddDialog">+ {{ t('node.add') }}</button>
          <button class="btn btn-small btn-ghost" @click="emit('back')">← {{ t('node.back') }}</button>
        </div>
      </div>
      <div class="browse-split">
        <div class="tree-panel">
          <div class="search-box">
            <input v-model="searchQuery" type="text" :placeholder="t('node.search')" @input="onSearch" />
            <div v-if="searchResults.length > 0" class="search-dropdown">
              <div v-for="r in searchResults" :key="r.path" class="search-hit" @click="selectSearchResult(r.path)">{{ r.path }}</div>
            </div>
          </div>
          <div class="tree-scroll">
            <TreeNode
              v-for="node in treeData" :key="node.path"
              :node="node"
              :depth="0"
              :selected-path="selectedNodePath"
              :server-id="serverId"
              @select="onSelectNode"
              @node-ctx="onNodeContextMenu"
            />
            <div v-if="connecting" class="tree-empty">Connecting...</div>
            <div v-else-if="treeData.length === 0" class="tree-empty">{{ t('node.selectNode') }}</div>
          </div>
        </div>
        <div v-if="selectedNode" class="detail-panel">
          <div class="detail-header">
            <div class="detail-path">{{ selectedNode.path }}</div>
            <div class="detail-actions">
              <button class="btn btn-small btn-primary" @click="saveData">{{ t('node.save') }}</button>
              <button class="btn btn-small btn-danger" @click="deleteSelected">{{ t('node.deleteNode') }}</button>
            </div>
          </div>
          <div class="detail-meta">
            <div class="meta-item"><span class="meta-key">Data Version</span><span class="meta-val">{{ selectedNode.dataVersion }}</span></div>
            <div class="meta-item"><span class="meta-key">Data Length</span><span class="meta-val">{{ selectedNode.dataLength }}</span></div>
            <div class="meta-item"><span class="meta-key">Children</span><span class="meta-val">{{ selectedNode.numChildren }}</span></div>
            <div class="meta-item"><span class="meta-key">Ephemeral</span><span class="meta-val">{{ selectedNode.ephemeral ? 'Yes' : 'No' }}</span></div>
            <div class="meta-item"><span class="meta-key">Created</span><span class="meta-val">{{ formatTime(selectedNode.creationTime) }}</span></div>
            <div class="meta-item"><span class="meta-key">Modified</span><span class="meta-val">{{ formatTime(selectedNode.modifiedTime) }}</span></div>
          </div>
          <div class="editor-section">
            <div class="editor-label">{{ t('node.nodeData') }}</div>
            <div class="editor-wrap">
              <CodeEditor
                :model-value="nodeData"
                @update:model-value="nodeData = $event"
                language="json"
                :dark="isDark"
              />
            </div>
          </div>
        </div>
        <div v-else class="detail-empty">
          <p>Select a node to view details</p>
        </div>
      </div>
    </div>

    <!-- Terminal -->
    <div v-show="activeTab === 'terminal'" class="tab-panel terminal-tab">
      <TerminalApp :server-id="serverId" />
    </div>

    <!-- Context Menu -->
    <div v-if="ctxMenu.show" class="ctx-menu" :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
         @click.stop @contextmenu.prevent="closeCtxMenu">
      <div class="ctx-item" @click="ctxMenuAddChild">+ {{ t('node.add') }} Child</div>
      <div class="ctx-sep"></div>
      <div class="ctx-item danger" @click="ctxMenuDelete">✕ {{ t('node.deleteNode') }}</div>
    </div>
    <div v-if="ctxMenu.show" class="ctx-backdrop" @click="closeCtxMenu"></div>

    <!-- Add Node Dialog -->
    <DialogForm
      v-if="showAddForm"
      title-key="node.addNodeTitle"
      confirm-key="common.confirm"
      :fields="addFormFields"
      @confirm="onAddNodeConfirm"
      @cancel="showAddForm = false"
    />

    <!-- 4-Letter Cmd -->
    <div v-show="activeTab === '4lc'" class="tab-panel">
      <div class="toolbar"><div class="breadcrumb">4-Letter Command</div></div>
      <div style="padding:20px">
        <div class="form-group" style="max-width:200px">
          <label>{{ t('node.cmd') }}</label>
          <input v-model="fourLetterCmd" type="text" maxlength="4" placeholder="stat" style="text-transform:lowercase" />
        </div>
        <button class="btn btn-primary" @click="runFourLetterCmd">{{ t('node.execute') }}</button>
        <pre class="4lc-output">{{ fourLetterOutput }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { api } from '../api.js'
import { t } from '../i18n.js'
import CodeEditor from './CodeEditor.vue'
import TerminalApp from './TerminalApp.vue'
import DialogForm from './DialogForm.vue'
import TreeNode from './TreeNode.vue'

const props = defineProps({ serverId: { type: String, required: true } })
const emit = defineEmits(['back'])

const activeTab = ref('browse')
const loading = ref(false)
const treeData = ref([])
const selectedNodePath = ref(null)
const selectedNode = ref(null)
const nodeData = ref('')
const searchQuery = ref('')
const searchResults = ref([])
const fourLetterCmd = ref('')
const fourLetterOutput = ref('')
const showAddForm = ref(false)
// Context menu
const ctxMenu = ref({ show: false, x: 0, y: 0, node: null })
function onNodeContextMenu(event, node) {
  ctxMenu.value = { show: true, x: event.clientX, y: event.clientY, node }
}
function closeCtxMenu() { ctxMenu.value.show = false }
async function ctxMenuAddChild() {
  const n = ctxMenu.value.node
  closeCtxMenu()
  if (!n) return
  addFormParentOverride.value = n.path
  showAddForm.value = true
}
async function ctxMenuDelete() {
  const n = ctxMenu.value.node
  closeCtxMenu()
  if (!n) return
  if (!await showDialog(t('node.deleteNode'), t('node.deleteConfirm', { path: n.path }))) return
  try {
    await api.deleteNode(props.serverId, n.path)
    window.__toast?.(t('node.deleted'), 'success')
    selectedNode.value = null; selectedNodePath.value = null; loadTree('/')
  } catch (e) { window.__toast?.('Failed: ' + e.message, 'error') }
}

const addFormParentOverride = ref('')
const addFormFields = computed(() => [
  { key: 'path', label: t('node.parentPath'), type: 'text', placeholder: '/', default: addFormParentOverride.value || '/', required: true },
  { key: 'name', label: t('node.nodeName'), type: 'text', placeholder: 'my-node', required: true },
  { key: 'data', label: t('node.data'), type: 'textarea', placeholder: 'Optional data', default: '' },
  { key: 'mode', label: t('node.nodeMode'), type: 'select', default: 'PERSISTENT', options: [
    { value: 'PERSISTENT', label: t('node.modePersistent') + ' - ' + t('node.modePersistentDesc') },
    { value: 'EPHEMERAL', label: t('node.modeEphemeral') + ' - ' + t('node.modeEphemeralDesc') },
    { value: 'PERSISTENT_SEQUENTIAL', label: t('node.modePersistentSeq') + ' - ' + t('node.modePersistentSeqDesc') },
    { value: 'EPHEMERAL_SEQUENTIAL', label: t('node.modeEphemeralSeq') + ' - ' + t('node.modeEphemeralSeqDesc') },
  ] },
])
const isDark = computed(() => document.documentElement.getAttribute('data-theme') === 'dark')

const pathParts = computed(() => {
  if (!selectedNodePath.value || selectedNodePath.value === '/') return []
  const parts = selectedNodePath.value.split('/').filter(Boolean)
  const result = []
  let full = ''
  for (const p of parts) { full += '/' + p; result.push({ name: p, fullPath: full }) }
  return result
})

async function loadTree(path = '/') {
  loading.value = true
  try {
    const data = await api.listNodes(props.serverId, path)
    if (path === '/') {
      treeData.value = [{
        path: '/', name: '/ (root)', numChildren: data.numChildren || 0,
        children: (data.children || []).map(c => ({
          ...c, name: c.path.substring(c.path.lastIndexOf('/') + 1) || c.path,
        })),
      }]
    }
    return data
  } catch (e) {
    window.__toast?.('Failed to load: ' + e.message, 'error')
    return null
  } finally { loading.value = false }
}

function loadRoot() { loadTree('/'); selectedNodePath.value = null; selectedNode.value = null }
async function sync() { await loadTree('/'); window.__toast?.('Synced', 'success') }
function navigateToPath(path) { selectedNodePath.value = path; loadTree(path) }

async function onSelectNode(node) {
  selectedNodePath.value = node.path
  try {
    const data = await api.listNodes(props.serverId, node.path)
    selectedNode.value = data
    nodeData.value = data.data || ''
  } catch (e) { window.__toast?.('Failed to load: ' + e.message, 'error') }
}

async function saveData() {
  try {
    await api.updateNode(props.serverId, { path: selectedNodePath.value, data: nodeData.value })
    window.__toast?.('Data saved', 'success')
  } catch (e) { window.__toast?.('Failed to save: ' + e.message, 'error') }
}

async function deleteSelected() {
  if (!selectedNodePath.value) return
  if (!await showDialog('Delete Node', `Delete ${selectedNodePath.value}?`)) return
  try {
    await api.deleteNode(props.serverId, selectedNodePath.value)
    window.__toast?.('Deleted', 'success')
    selectedNode.value = null; selectedNodePath.value = null; loadTree('/')
  } catch (e) { window.__toast?.('Failed: ' + e.message, 'error') }
}

function showAddDialog() {
  addFormParentOverride.value = ''
  showAddForm.value = true
}

async function onAddNodeConfirm(form) {
  showAddForm.value = false
  try {
    await api.createNode(props.serverId, form)
    window.__toast?.('Node created', 'success')
    loadTree('/')
  } catch (e) {
    window.__toast?.('Failed: ' + e.message, 'error')
  }
}

async function runFourLetterCmd() {
  const cmd = fourLetterCmd.value.trim().toLowerCase()
  if (!cmd || cmd.length !== 4) { window.__toast?.('Enter 4-letter command', 'error'); return }
  fourLetterOutput.value = `Executing "${cmd}"...\n(API coming soon)`
}

let searchTimer = null
function onSearch() {
  clearTimeout(searchTimer)
  const q = searchQuery.value.trim()
  if (!q) { searchResults.value = []; return }
  searchTimer = setTimeout(async () => {
    try { searchResults.value = await api.searchNodes(props.serverId, q) || [] } catch (e) { searchResults.value = [] }
  }, 300)
}
function selectSearchResult(path) { searchResults.value = []; searchQuery.value = ''; onSelectNode({ path }) }
function formatTime(ms) { return ms ? new Date(ms).toLocaleString() : '-' }
function showDialog(title, msg) {
  const d = window.__dialog
  if (!d) return Promise.resolve(false)
  return new Promise(r => { d.title = title; d.message = msg; d.show = true; d.resolve = r })
}

const connecting = ref(true)
let nodeWs = null
onMounted(async () => {
  // Connect to ZK first
  try {
    await api.connect(props.serverId)
    connecting.value = false
    loadTree('/')
  } catch (e) {
    window.__toast?.('Connection failed: ' + e.message, 'error')
    connecting.value = false
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  try {
    nodeWs = new WebSocket(`${protocol}//${window.location.host}/ws/nodes/${encodeURIComponent(props.serverId)}`)
    nodeWs.onmessage = (e) => {
      try { const msg = JSON.parse(e.data); if (['added','updated','deleted'].includes(msg.type)) loadTree('/') } catch (_) {}
    }
  } catch (e) { console.warn(e) }
})
onUnmounted(() => { if (nodeWs) { nodeWs.close(); nodeWs = null } })
</script>

<style scoped>
.node-browser { display:flex; flex-direction:column; height:100%; }
.tab-bar { display:flex; background:var(--bg-secondary); border-bottom:1px solid var(--border-color); padding:0 12px; flex-shrink:0; }
.tab-item {
  display:flex; align-items:center; gap:4px;
  padding:10px 16px; cursor:pointer; font-size:12px; font-weight:500;
  color:var(--text-secondary); border-bottom:2px solid transparent;
  transition:all 0.15s;
}
.tab-item:hover { color:var(--text-primary); background:var(--bg-hover); }
.tab-item.active { color:var(--accent); border-bottom-color:var(--accent); }
.tab-icon { font-size:14px; }
.tab-panel { flex:1; display:flex; flex-direction:column; overflow:hidden; }
.terminal-tab { background:#1a1a1a; }
.toolbar {
  display:flex; align-items:center; padding:8px 16px;
  background:var(--bg-secondary); border-bottom:1px solid var(--border-color);
  gap:8px; min-height:40px; flex-shrink:0;
}
.breadcrumb { font-size:13px; color:var(--text-secondary); flex:1; display:flex; align-items:center; flex-wrap:wrap; }
.crumb { cursor:pointer; color:var(--accent); padding:1px 2px; border-radius:3px; }
.crumb:hover { background:var(--accent-light); }
.crumb-sep { color:var(--text-muted); padding:0 2px; }
.toolbar-actions { display:flex; gap:4px; }
.browse-split { flex:1; display:flex; overflow:hidden; }
.tree-panel { width:300px; min-width:260px; border-right:1px solid var(--border-color); display:flex; flex-direction:column; overflow:hidden; position:relative; }
.search-box { padding:8px 12px; position:relative; flex-shrink:0; }
.search-box input {
  width:100%; padding:7px 10px; border:1px solid var(--input-border); border-radius:6px;
  background:var(--input-bg); color:var(--text-primary); font-size:12px; outline:none;
}
.search-box input:focus { border-color:var(--input-focus); }
.search-dropdown {
  position:absolute; top:100%; left:8px; right:8px; z-index:100;
  max-height:260px; overflow-y:auto;
  background:var(--bg-primary); border:1px solid var(--border-color);
  border-radius:6px; box-shadow:0 4px 12px rgba(0,0,0,0.12);
}
.search-hit { padding:6px 12px; cursor:pointer; font-size:12px; color:var(--text-primary); }
.search-hit:hover { background:var(--bg-hover); }
.tree-scroll { flex:1; overflow-y:auto; padding:4px 0; }
.tree-root-node { }
.tree-node {
  display:flex; align-items:center; gap:4px;
  padding:4px 12px; cursor:pointer; font-size:13px; color:var(--text-primary);
  transition:background 0.1s; border-left:3px solid transparent;
}
.tree-node:hover { background:var(--bg-hover); }
.tree-node.selected { background:var(--bg-selected); border-left-color:var(--accent); }
.tree-node.ephemeral { color:var(--node-ephemeral); }
.tree-arrow { width:14px; text-align:center; flex-shrink:0; font-size:10px; color:var(--text-muted); }
.tree-arrow-placeholder { visibility:hidden; }
.tree-icon { width:18px; text-align:center; flex-shrink:0; font-size:13px; }
.tree-label { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.tree-badge {
  margin-left:auto; padding:0 5px; font-size:10px; border-radius:8px;
  background:var(--bg-tertiary); color:var(--text-muted); flex-shrink:0;
}
.tree-children { }
.tree-empty { padding:20px; text-align:center; color:var(--text-muted); font-size:13px; }
.detail-panel { flex:1; display:flex; flex-direction:column; overflow:hidden; }
.detail-empty { flex:1; display:flex; align-items:center; justify-content:center; color:var(--text-muted); }
.detail-header {
  display:flex; align-items:center; justify-content:space-between;
  padding:10px 16px; background:var(--bg-secondary);
  border-bottom:1px solid var(--border-color); gap:8px; flex-shrink:0;
}
.detail-path { font-size:12px; color:var(--text-secondary); font-family:'SF Mono',Monaco,monospace; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.detail-actions { display:flex; gap:4px; flex-shrink:0; }
.detail-meta {
  display:grid; grid-template-columns:1fr 1fr 1fr; gap:0;
  border-bottom:1px solid var(--border-color); flex-shrink:0;
}
.meta-item {
  padding:6px 12px; display:flex; flex-direction:column; gap:1px;
  border-right:1px solid var(--border-color); border-bottom:1px solid var(--bg-tertiary);
}
.meta-item:nth-child(3n) { border-right:none; }
.meta-key { font-size:10px; color:var(--text-muted); text-transform:uppercase; letter-spacing:0.3px; }
.meta-val { font-size:13px; font-family:'SF Mono',Monaco,monospace; color:var(--text-primary); word-break:break-all; }
.editor-section { flex:1; display:flex; flex-direction:column; }
.editor-label { padding:6px 12px; font-size:11px; font-weight:600; color:var(--text-muted); text-transform:uppercase; background:var(--bg-secondary); border-bottom:1px solid var(--border-color); }
.editor-wrap { flex:1; overflow:hidden; }
.4lc-output { margin-top:12px; padding:12px; background:var(--code-bg); border:1px solid var(--border-color); border-radius:6px; font-family:'SF Mono',Monaco,monospace; font-size:12px; white-space:pre-wrap; min-height:200px; overflow:auto; }
.ctx-menu { position:fixed; z-index:9998; background:var(--bg-primary); border:1px solid var(--border-color); border-radius:8px; box-shadow:0 4px 16px rgba(0,0,0,0.15); padding:4px; min-width:140px; }
.ctx-item { padding:8px 14px; cursor:pointer; font-size:13px; border-radius:4px; color:var(--text-primary); }
.ctx-item:hover { background:var(--bg-hover); }
.ctx-item.danger { color:var(--danger); }
.ctx-item.danger:hover { background:rgba(198,40,40,0.08); }
.ctx-sep { height:1px; background:var(--border-color); margin:4px 8px; }
.ctx-backdrop { position:fixed; top:0; left:0; right:0; bottom:0; z-index:9997; }
</style>
