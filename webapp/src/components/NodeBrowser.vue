<template>
  <div class="node-browser">
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
              :node="node" :depth="0" :selected-path="selectedNodePath"
              :server-id="serverId" :refresh-key="refreshKey"
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
            <div class="meta-item"><span class="meta-key">{{ t('node.dataVersion') }}</span><span class="meta-val">{{ selectedNode.dataVersion }}</span></div>
            <div class="meta-item"><span class="meta-key">{{ t('node.dataLength') }}</span><span class="meta-val">{{ selectedNode.dataLength }}</span></div>
            <div class="meta-item"><span class="meta-key">{{ t('node.children') }}</span><span class="meta-val">{{ selectedNode.numChildren }}</span></div>
            <div class="meta-item"><span class="meta-key">{{ t('node.ephemeral') }}</span><span class="meta-val">{{ selectedNode.ephemeral ? 'Yes' : 'No' }}</span></div>
            <div class="meta-item"><span class="meta-key">{{ t('node.createdTime') }}</span><span class="meta-val">{{ formatTime(selectedNode.creationTime) }}</span></div>
            <div class="meta-item"><span class="meta-key">{{ t('node.modifiedTime') }}</span><span class="meta-val">{{ formatTime(selectedNode.modifiedTime) }}</span></div>
          </div>
          <div class="editor-section">
            <div class="editor-toolbar">
              <span class="editor-label">{{ t('node.nodeData') }}</span>
              <div class="editor-spacer"></div>
              <button class="editor-fmt-btn" :class="{ active: editorFmt === 'raw' }" @click="editorFmt = 'raw'">RAW</button>
              <button class="editor-fmt-btn" :class="{ active: editorFmt === 'json' }" @click="toggleFmt('json')">JSON</button>
              <button class="editor-fmt-btn" :class="{ active: editorFmt === 'xml' }" @click="toggleFmt('xml')">XML</button>
            </div>
            <div class="editor-wrap">
              <CodeEditor
                :model-value="displayData"
                @update:model-value="onEditorChange"
                :language="editorLang" :dark="isDark" :readonly="editorFmt !== 'raw'"
              />
            </div>
          </div>
        </div>
        <div v-else class="detail-empty">
          <p>{{ t('node.selectNode') }}</p>
        </div>
      </div>
    </div>

    <div v-show="activeTab === 'terminal'" class="tab-panel terminal-tab">
      <TerminalApp :server-id="serverId" />
    </div>

    <div v-if="ctxMenu.show" class="ctx-menu" :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
         @click.stop @contextmenu.prevent="closeCtxMenu">
      <div class="ctx-item" @click="ctxMenuAddChild">+ {{ t('node.add') }} Child</div>
      <div class="ctx-sep"></div>
      <div class="ctx-item danger" @click="ctxMenuDelete">✕ {{ t('node.deleteNode') }}</div>
    </div>
    <div v-if="ctxMenu.show" class="ctx-backdrop" @click="closeCtxMenu"></div>

    <DialogForm
      v-if="showAddForm"
      title-key="node.addNodeTitle"
      confirm-key="common.confirm"
      :fields="addFormFields"
      @confirm="onAddNodeConfirm"
      @cancel="showAddForm = false"
    />

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
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { api } from '../api.js'
import { t } from '../i18n.js'
import { state } from '../main.js'
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
const isDark = computed(() => document.documentElement.getAttribute('data-theme') === 'dark')
const connecting = ref(true)
const refreshKey = ref(0)
const showAddForm = ref(false)
const editorFmt = ref('raw')

function toggleFmt(fmt) {
  if (editorFmt.value === fmt) { editorFmt.value = 'raw'; return }
  editorFmt.value = fmt
}

const editorLang = computed(() => editorFmt.value === 'json' ? 'json' : editorFmt.value === 'xml' ? 'xml' : 'text')

const displayData = computed(() => {
  const raw = nodeData.value || ''
  if (editorFmt.value === 'raw') return raw
  try {
    if (editorFmt.value === 'json') {
      return JSON.stringify(JSON.parse(raw), null, 2)
    }
    if (editorFmt.value === 'xml') {
      // Simple XML formatting - indent tags
      return raw.replace(/>\s*</g, '>\n<').split('\n').map((line, i) => {
        const indent = line.startsWith('</') ? -1 : 0
        return '  '.repeat(Math.max(0, i + indent)) + line
      }).join('\n')
    }
  } catch (e) { return raw }
  return raw
})

function onEditorChange(val) {
  if (editorFmt.value === 'raw') {
    nodeData.value = val
  }
}

const ctxMenu = ref({ show: false, x: 0, y: 0, node: null })
function onNodeContextMenu(event, node) { ctxMenu.value = { show: true, x: event.clientX, y: event.clientY, node } }
function closeCtxMenu() { ctxMenu.value.show = false }
function ctxMenuAddChild() {
  const n = ctxMenu.value.node; closeCtxMenu()
  if (!n) return; addFormParentOverride.value = n.path; showAddForm.value = true
}
function ctxMenuDelete() {
  const n = ctxMenu.value.node; closeCtxMenu()
  if (!n) return
  showDialog(t('node.deleteNode'), t('node.deleteConfirm', { path: n.path })).then(ok => {
    if (!ok) return
    selectedNode.value = null; selectedNodePath.value = null
    api.deleteNode(props.serverId, n.path).then(() => {
      window.__toast?.(t('node.deleted'), 'success'); refreshTree()
    }).catch(e => { window.__toast?.('Failed: ' + e.message, 'error') })
  })
}

function refreshTree() { refreshKey.value++ }

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

const pathParts = computed(() => {
  if (!selectedNodePath.value || selectedNodePath.value === '/') return []
  const parts = selectedNodePath.value.split('/').filter(Boolean)
  const result = []; let full = ''
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
function sync() { refreshTree(); window.__toast?.('Synced', 'success') }

async function onSelectNode(node) {
  selectedNodePath.value = node.path
  try {
    const data = await api.listNodes(props.serverId, node.path)
    selectedNode.value = data; nodeData.value = data.data || ''
  } catch (e) { window.__toast?.('Failed to load: ' + e.message, 'error') }
}

async function saveData() {
  try {
    await api.updateNode(props.serverId, { path: selectedNodePath.value, data: nodeData.value })
    window.__toast?.('Data saved', 'success')
  } catch (e) { window.__toast?.('Failed to save: ' + e.message, 'error') }
}

function deleteSelected() {
  if (!selectedNodePath.value) return
  showDialog(t('node.deleteNode'), t('node.deleteConfirm', { path: selectedNodePath.value })).then(ok => {
    if (!ok) return
    selectedNode.value = null; selectedNodePath.value = null
    api.deleteNode(props.serverId, selectedNodePath.value).then(() => {
      window.__toast?.(t('node.deleted'), 'success'); refreshTree()
    }).catch(e => { window.__toast?.('Failed: ' + e.message, 'error') })
  })
}

function showAddDialog() { addFormParentOverride.value = ''; showAddForm.value = true }

async function onAddNodeConfirm(form) {
  showAddForm.value = false
  try {
    await api.createNode(props.serverId, form)
    window.__toast?.(t('node.created'), 'success'); refreshTree()
  } catch (e) { window.__toast?.('Failed: ' + e.message, 'error') }
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

async function connectAndLoad() {
  connecting.value = true; treeData.value = []; selectedNode.value = null; selectedNodePath.value = null
  try {
    await api.connect(props.serverId)
    state.serverStatuses[props.serverId] = 'connected'
    connecting.value = false; loadTree('/')
  } catch (e) {
    window.__toast?.('Connection failed: ' + e.message, 'error')
    state.serverStatuses[props.serverId] = 'disconnected'
    connecting.value = false
  }
}

let nodeWs = null
let wsServerId = null

watch(() => props.serverId, (newId, oldId) => {
  if (nodeWs) { nodeWs.close(); nodeWs = null }
  connectAndLoad()
  // Reconnect node events WS
  wsServerId = newId
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  try {
    nodeWs = new WebSocket(`${protocol}//${window.location.host}/ws/nodes/${encodeURIComponent(newId)}`)
    nodeWs.onmessage = (e) => {
      try { const msg = JSON.parse(e.data); if (['added','updated','deleted'].includes(msg.type)) refreshTree() } catch (_) {}
    }
  } catch (e) { console.warn(e) }
})

onMounted(() => {
  wsServerId = props.serverId
  connectAndLoad()
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  try {
    nodeWs = new WebSocket(`${protocol}//${window.location.host}/ws/nodes/${encodeURIComponent(props.serverId)}`)
    nodeWs.onmessage = (e) => {
      try { const msg = JSON.parse(e.data); if (['added','updated','deleted'].includes(msg.type)) refreshTree() } catch (_) {}
    }
  } catch (e) { console.warn(e) }
})

onUnmounted(() => { if (nodeWs) { nodeWs.close(); nodeWs = null } })
</script>

<style scoped>
.tab-bar { display:flex; background:var(--header-bg); border-bottom:1px solid var(--border-color); padding:0 8px; flex-shrink:0; }
.tab-item { display:flex; align-items:center; gap:5px; padding:10px 16px; cursor:pointer; font-size:12px; font-weight:500; color:var(--text-secondary); border-bottom:2px solid transparent; transition:all 0.12s; margin-bottom:-1px; }
.tab-item:hover { color:var(--text-primary); background:var(--bg-hover); border-radius:6px 6px 0 0; }
.tab-item.active { color:var(--accent); border-bottom-color:var(--accent); background:transparent; }
.tab-icon { font-size:13px; }
.tab-panel { flex:1; display:flex; flex-direction:column; overflow:hidden; }
.terminal-tab { background:#1a1a1a; }
.toolbar { display:flex; align-items:center; padding:7px 16px; background:var(--toolbar-bg); border-bottom:1px solid var(--border-color); gap:8px; min-height:40px; flex-shrink:0; }
.breadcrumb { font-size:13px; color:var(--text-secondary); flex:1; display:flex; align-items:center; flex-wrap:wrap; }
.crumb { cursor:pointer; color:var(--accent); padding:2px 5px; border-radius:4px; font-size:12px; }
.crumb:hover { background:var(--accent-subtle); }
.crumb-sep { color:var(--text-muted); padding:0 2px; font-size:11px; }
.toolbar-actions { display:flex; gap:4px; }
.browse-split { flex:1; display:flex; overflow:hidden; }
.tree-panel { width:300px; min-width:240px; border-right:1px solid var(--border-color); display:flex; flex-direction:column; overflow:hidden; position:relative; }
.search-box { padding:10px 12px; position:relative; flex-shrink:0; }
.search-box input { width:100%; padding:7px 10px; border:1px solid var(--input-border); border-radius:6px; background:var(--input-bg); color:var(--text-primary); font-size:12px; outline:none; }
.search-box input:focus { border-color:var(--input-focus); }
.search-dropdown { position:absolute; top:100%; left:8px; right:8px; z-index:100; max-height:260px; overflow-y:auto; background:var(--bg-primary); border:1px solid var(--border-color); border-radius:6px; box-shadow:0 4px 12px rgba(0,0,0,0.12); }
.search-hit { padding:6px 12px; cursor:pointer; font-size:12px; color:var(--text-primary); }
.search-hit:hover { background:var(--bg-hover); }
.tree-scroll { flex:1; overflow-y:auto; padding:4px 0; }
.tree-empty { padding:20px; text-align:center; color:var(--text-muted); font-size:13px; }
.detail-panel { flex:1; display:flex; flex-direction:column; overflow:hidden; }
.detail-empty { flex:1; display:flex; align-items:center; justify-content:center; color:var(--text-muted); }
.detail-header { display:flex; align-items:center; justify-content:space-between; padding:10px 16px; background:var(--toolbar-bg); border-bottom:1px solid var(--border-color); gap:8px; flex-shrink:0; }
.detail-path { font-size:12px; color:var(--text-secondary); font-family:'SF Mono',Monaco,Consolas,monospace; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.detail-actions { display:flex; gap:4px; flex-shrink:0; }
.detail-meta { display:grid; grid-template-columns:repeat(auto-fill,minmax(150px,1fr)); gap:0; border-bottom:1px solid var(--border-color); flex-shrink:0; background:var(--bg-primary); }
.meta-item { padding:6px 12px; display:flex; flex-direction:column; gap:1px; border-bottom:1px solid var(--border-light); border-right:1px solid var(--border-light); }
.meta-item:nth-child(odd) { background:var(--bg-secondary); }
.meta-key { font-size:10px; color:var(--text-muted); font-weight:600; letter-spacing:0.3px; }
.meta-val { font-size:13px; font-family:'SF Mono',Monaco,Consolas,monospace; color:var(--text-primary); word-break:break-all; }
.editor-section { flex:1; display:flex; flex-direction:column; }
.editor-toolbar { display:flex; align-items:center; padding:4px 12px; background:var(--toolbar-bg); border-bottom:1px solid var(--border-color); gap:4px; }
.editor-label { font-size:10px; font-weight:700; color:var(--text-muted); text-transform:uppercase; letter-spacing:0.5px; }
.editor-spacer { flex:1; }
.editor-fmt-btn { padding:2px 8px; border:1px solid var(--border-color); border-radius:4px; background:transparent; color:var(--text-muted); font-size:11px; font-weight:600; cursor:pointer; transition:all 0.12s; }
.editor-fmt-btn:hover { background:var(--bg-hover); color:var(--text-primary); }
.editor-fmt-btn.active { background:var(--accent); color:#fff; border-color:var(--accent); }
.editor-wrap { flex:1; overflow:hidden; display:flex; flex-direction:column; min-height:0; }
.editor-section { flex:1; display:flex; flex-direction:column; min-height:0; }
.detail-panel { flex:1; display:flex; flex-direction:column; overflow:hidden; min-height:0; }
.4lc-output { margin-top:12px; padding:12px; background:var(--code-bg); border:1px solid var(--border-color); border-radius:6px; font-family:'SF Mono',Monaco,monospace; font-size:12px; white-space:pre-wrap; min-height:200px; overflow:auto; }
.ctx-menu { position:fixed; z-index:9998; background:var(--bg-primary); border:1px solid var(--border-color); border-radius:8px; box-shadow:0 4px 16px rgba(0,0,0,0.15); padding:4px; min-width:140px; }
.ctx-item { padding:8px 14px; cursor:pointer; font-size:13px; border-radius:4px; color:var(--text-primary); }
.ctx-item:hover { background:var(--bg-hover); }
.ctx-item.danger { color:var(--danger); }
.ctx-item.danger:hover { background:rgba(198,40,40,0.08); }
.ctx-sep { height:1px; background:var(--border-color); margin:4px 8px; }
.ctx-backdrop { position:fixed; top:0; left:0; right:0; bottom:0; z-index:9997; }
</style>
