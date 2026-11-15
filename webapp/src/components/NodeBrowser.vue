<template>
  <div class="node-browser">
    <div class="tab-bar">
      <div class="tab-item" :class="{ active: activeTab === 'browse' }" @click="activeTab = 'browse'">
        <span class="tab-icon"><FolderTree :size="14" /></span> {{ t('node.browse') }}
      </div>
      <div class="tab-item" :class="{ active: activeTab === 'terminal' }" @click="activeTab = 'terminal'">
        <span class="tab-icon"><TerminalSquare :size="14" /></span> {{ t('node.terminal') }}
      </div>
      <div class="tab-item" :class="{ active: activeTab === '4lc' }" @click="activeTab = '4lc'">
        <span class="tab-icon"><Wrench :size="14" /></span> {{ t('node.fourLetterCmd') }}
      </div>
    </div>

    <div v-show="activeTab === 'browse'" class="tab-panel">
      <div class="toolbar">
        <div class="breadcrumb">
          <span class="crumb" @click="loadRoot">/</span>
          <template v-for="(part, i) in pathParts" :key="i">
            <span class="crumb-sep"><ChevronRight :size="12" /></span>
            <span class="crumb" @click="navigateToPath(part.fullPath)">{{ part.name }}</span>
          </template>
        </div>
        <div class="toolbar-actions">
          <button class="btn btn-small" @click="sync" :disabled="loading">
            <RefreshCw :size="13" /> {{ t('node.sync') }}
          </button>
          <button class="btn btn-small btn-primary" @click="showAddDialog">
            <Plus :size="13" /> {{ t('node.add') }}
          </button>
        </div>
      </div>

      <div class="browse-split">
        <div class="tree-panel">
          <div class="search-box">
            <input
              v-model="searchQuery"
              type="text"
              :placeholder="t('node.search')"
              @input="onSearch"
            />
            <div v-if="searchResults.length > 0" class="search-dropdown">
              <div
                v-for="r in searchResults"
                :key="r.path"
                class="search-hit"
                @click="selectSearchResult(r.path)"
              >
                {{ r.path }}
              </div>
            </div>
          </div>

          <div class="tree-scroll">
            <n-virtual-list
              v-if="visibleRows.length > 0"
              :items="visibleRows"
              :item-size="26"
              key-field="path"
              class="tree-virtual"
            >
              <template #default="{ item }">
                <div
                  class="tree-row"
                  :class="{ selected: item.path === selectedNodePath }"
                  :style="{ paddingLeft: 8 + item.depth * 18 + 'px' }"
                  @click="selectRow(item)"
                  @contextmenu.prevent="onNodeContextMenu($event, item)"
                >
                  <span class="tree-arrow" @click.stop="toggleNode(item)">
                    <ChevronRight
                      v-if="hasChildren(item)"
                      :size="12"
                      :class="{ rotated: expanded.has(item.path) }"
                    />
                  </span>
                  <span class="tree-icon">
                    <Loader2 v-if="loadingPaths.has(item.path)" :size="13" class="tree-spinner" />
                    <FolderOpen v-else-if="expanded.has(item.path)" :size="14" />
                    <Folder v-else :size="14" />
                  </span>
                  <span class="tree-label">{{ item.name }}</span>
                  <span v-if="item.numChildren > 0" class="tree-badge">{{ item.numChildren }}</span>
                </div>
              </template>
            </n-virtual-list>
            <div v-else-if="connecting" class="tree-empty">{{ t('node.connecting') }}</div>
            <div v-else class="tree-empty">{{ t('node.empty') }}</div>
          </div>
        </div>

        <DetailPanel
          v-if="selectedNode"
          :node="selectedNode"
          :node-data="nodeData"
          :saving="saving"
          @save="saveData"
          @delete="deleteSelected"
          @update:node-data="nodeData = $event"
        />
        <div v-else class="detail-empty">
          <p>{{ t('node.selectNode') }}</p>
        </div>
      </div>
    </div>

    <div v-if="activeTab === 'terminal'" class="tab-panel terminal-tab">
      <TerminalApp :server-id="serverId" />
    </div>

    <div
      v-if="ctxMenu.show"
      class="ctx-menu"
      :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
      @click.stop
      @contextmenu.prevent="closeCtxMenu"
    >
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
      <div class="toolbar"><div class="breadcrumb">{{ t('node.fourLetterCmd') }}</div></div>
      <div style="padding: 20px">
        <div class="form-group" style="max-width: 200px">
          <label>{{ t('node.cmd') }}</label>
          <input
            v-model="fourLetterCmd"
            type="text"
            maxlength="4"
            placeholder="stat"
            style="text-transform: lowercase"
          />
        </div>
        <button class="btn btn-primary" @click="runFourLetterCmd">{{ t('node.execute') }}</button>
        <pre class="cmd-output">{{ fourLetterOutput }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { NVirtualList } from 'naive-ui'
import {
  ChevronRight,
  Folder,
  FolderOpen,
  FolderTree,
  Loader2,
  Plus,
  RefreshCw,
  TerminalSquare,
  Wrench,
} from 'lucide-vue-next'
import { api } from '../api.ts'
import { t } from '../i18n.ts'
import { state } from '../state.ts'
import { toast, confirm } from '../composables/feedback.ts'

const TerminalApp = defineAsyncComponent(() => import('./TerminalApp.vue'))
const DetailPanel = defineAsyncComponent(() => import('./DetailPanel.vue'))
const DialogForm = defineAsyncComponent(() => import('./DialogForm.vue'))

interface TreeRow {
  path: string
  name: string
  depth: number
  numChildren: number
}

const props = defineProps({ serverId: { type: String, required: true } })
defineEmits(['back'])

const activeTab = ref('browse')
const loading = ref(false)
const selectedNodePath = ref<string | null>(null)
const selectedNode = ref<any>(null)
const nodeData = ref('')
const searchQuery = ref('')
const searchResults = ref<any[]>([])
const fourLetterCmd = ref('')
const fourLetterOutput = ref('')
const connecting = ref(true)
const showAddForm = ref(false)
const saving = ref(false)

const expanded = reactive(new Set<string>())
const loadingPaths = reactive(new Set<string>())
const childrenMap = reactive(new Map<string, TreeRow[]>())
const rootNumChildren = ref(0)

const visibleRows = computed<TreeRow[]>(() => {
  const rows: TreeRow[] = []
  const walk = (row: TreeRow) => {
    rows.push(row)
    if (!expanded.has(row.path)) return
    const children = childrenMap.get(row.path)
    if (children) {
      for (const child of children) walk(child)
    }
  }
  walk({ path: '/', name: '/', depth: 0, numChildren: rootNumChildren.value })
  return rows
})

const ctxMenu = ref<{ show: boolean; x: number; y: number; node: TreeRow | null }>({
  show: false,
  x: 0,
  y: 0,
  node: null,
})

function onNodeContextMenu(event: MouseEvent, node: TreeRow) {
  ctxMenu.value = { show: true, x: event.clientX, y: event.clientY, node }
}

function closeCtxMenu() {
  ctxMenu.value.show = false
}

function ctxMenuAddChild() {
  const node = ctxMenu.value.node
  closeCtxMenu()
  if (!node) return
  addFormParentOverride.value = node.path
  showAddForm.value = true
}

function ctxMenuDelete() {
  const node = ctxMenu.value.node
  closeCtxMenu()
  if (!node) return
  confirm(t('node.deleteNode'), t('node.deleteConfirm', { path: node.path })).then((ok) => {
    if (!ok) return
    const parent = parentPath(node.path)
    if (selectedNodePath.value === node.path) {
      selectedNode.value = null
      selectedNodePath.value = null
    }
    api
      .deleteNode(props.serverId, node.path)
      .then(() => {
        toast(t('node.deleted'), 'success')
        refreshPath(parent, true)
      })
      .catch((e) => toast(t('node.deleteFailed', { msg: e.message }), 'error'))
  })
}

const addFormParentOverride = ref('')
const addFormFields = computed(() => [
  {
    key: 'path',
    label: t('node.parentPath'),
    type: 'text',
    placeholder: '/',
    default: addFormParentOverride.value || '/',
    required: true,
  },
  { key: 'name', label: t('node.nodeName'), type: 'text', placeholder: 'my-node', required: true },
  { key: 'data', label: t('node.data'), type: 'textarea', placeholder: 'Optional data', default: '' },
  {
    key: 'mode',
    label: t('node.nodeMode'),
    type: 'select',
    default: 'PERSISTENT',
    options: [
      { value: 'PERSISTENT', label: t('node.modePersistent') + ' - ' + t('node.modePersistentDesc') },
      { value: 'EPHEMERAL', label: t('node.modeEphemeral') + ' - ' + t('node.modeEphemeralDesc') },
      {
        value: 'PERSISTENT_SEQUENTIAL',
        label: t('node.modePersistentSeq') + ' - ' + t('node.modePersistentSeqDesc'),
      },
      {
        value: 'EPHEMERAL_SEQUENTIAL',
        label: t('node.modeEphemeralSeq') + ' - ' + t('node.modeEphemeralSeqDesc'),
      },
    ],
  },
])

const pathParts = computed(() => {
  if (!selectedNodePath.value || selectedNodePath.value === '/') return []
  const parts = selectedNodePath.value.split('/').filter(Boolean)
  const result: { name: string; fullPath: string }[] = []
  let full = ''
  for (const part of parts) {
    full += '/' + part
    result.push({ name: part, fullPath: full })
  }
  return result
})

function parentPath(path: string) {
  if (!path || path === '/') return '/'
  const index = path.lastIndexOf('/')
  return index <= 0 ? '/' : path.substring(0, index)
}

function depthOf(path: string) {
  return path === '/' ? 0 : path.split('/').filter(Boolean).length
}

function hasChildren(row: TreeRow) {
  if (row.path === '/') return true
  if (!childrenMap.has(row.path)) return true
  return (childrenMap.get(row.path)?.length ?? 0) > 0
}

async function loadChildren(path: string, force = false) {
  if (loadingPaths.has(path)) return
  if (!force && childrenMap.has(path)) return
  loadingPaths.add(path)
  try {
    const data = await api.listChildren(props.serverId, path)
    const depth = depthOf(path) + 1
    const children: TreeRow[] = (data.children || []).map((name: string) => ({
      path: path === '/' ? '/' + name : path + '/' + name,
      name,
      depth,
      numChildren: 0,
    }))
    childrenMap.set(path, children)
    if (path === '/') {
      rootNumChildren.value = children.length
    }
  } catch (e: any) {
    toast(t('node.loadFailed', { msg: e.message }), 'error')
  } finally {
    loadingPaths.delete(path)
  }
}

async function toggleNode(row: TreeRow) {
  if (expanded.has(row.path)) {
    expanded.delete(row.path)
    return
  }
  expanded.add(row.path)
  await loadChildren(row.path)
}

async function ensureExpanded(path: string) {
  await loadChildren('/')
  expanded.add('/')
  const parts = path.split('/').filter(Boolean)
  let current = '/'
  for (const part of parts) {
    current = current === '/' ? '/' + part : current + '/' + part
    await loadChildren(current)
    expanded.add(current)
  }
}

async function selectRow(row: TreeRow) {
  await onSelectNode({ path: row.path })
}

async function onSelectNode(row: { path: string }) {
  selectedNodePath.value = row.path
  try {
    const data = await api.getNodeDetail(props.serverId, row.path)
    selectedNode.value = data
    nodeData.value = data.data || ''
  } catch (e: any) {
    toast(t('node.loadFailed', { msg: e.message }), 'error')
  }
}

async function navigateToPath(path: string) {
  await ensureExpanded(path)
  await onSelectNode({ path })
}

async function loadRoot() {
  selectedNodePath.value = null
  selectedNode.value = null
  expanded.clear()
  expanded.add('/')
  childrenMap.clear()
  await loadChildren('/')
}

let refreshTimer: ReturnType<typeof setTimeout> | null = null
const pendingRefresh = new Set<string>()

function scheduleRefresh(path: string) {
  pendingRefresh.add(path)
  if (refreshTimer) return
  refreshTimer = setTimeout(async () => {
    refreshTimer = null
    const paths = Array.from(pendingRefresh)
    pendingRefresh.clear()
    for (const path of paths) {
      await refreshPath(path)
    }
  }, 250)
}

async function refreshPath(path: string, expand = false) {
  childrenMap.delete(path)
  if (expand) expanded.add(path)
  if (path === '/' || expanded.has(path)) {
    await loadChildren(path, true)
  }
}

async function sync() {
  loading.value = true
  try {
    const paths = new Set<string>(['/', ...expanded])
    childrenMap.clear()
    expanded.clear()
    expanded.add('/')
    for (const path of paths) {
      if (path !== '/') expanded.add(path)
      await loadChildren(path, true)
    }
    if (selectedNodePath.value) {
      await onSelectNode({ path: selectedNodePath.value })
    }
    toast(t('node.synced'), 'success')
  } finally {
    loading.value = false
  }
}

async function saveData(data: string) {
  saving.value = true
  try {
    await api.updateNode(props.serverId, {
      path: selectedNodePath.value,
      data: data || nodeData.value,
    })
    toast(t('node.dataSaved'), 'success')
  } catch (e: any) {
    toast(t('node.saveFailed', { msg: e.message }), 'error')
  } finally {
    saving.value = false
  }
}

function deleteSelected() {
  if (!selectedNodePath.value) return
  const path = selectedNodePath.value
  confirm(t('node.deleteNode'), t('node.deleteConfirm', { path })).then((ok) => {
    if (!ok) return
    selectedNode.value = null
    selectedNodePath.value = null
    api
      .deleteNode(props.serverId, path)
      .then(() => {
        toast(t('node.deleted'), 'success')
        refreshPath(parentPath(path))
      })
      .catch((e) => toast(t('node.deleteFailed', { msg: e.message }), 'error'))
  })
}

function showAddDialog() {
  addFormParentOverride.value = ''
  showAddForm.value = true
}

async function onAddNodeConfirm(form: any) {
  showAddForm.value = false
  try {
    await api.createNode(props.serverId, form)
    toast(t('node.created'), 'success')
    await refreshPath(form.path || '/', true)
  } catch (e: any) {
    toast(t('node.createFailed', { msg: e.message }), 'error')
  }
}

async function runFourLetterCmd() {
  const cmd = fourLetterCmd.value.trim().toLowerCase()
  if (!cmd || cmd.length !== 4) {
    toast(t('node.enterCmd'), 'error')
    return
  }
  fourLetterOutput.value = `Executing "${cmd}"...`
  try {
    const res = await api.execute4LC(props.serverId, cmd)
    fourLetterOutput.value = res.result || '(no output)'
  } catch (e: any) {
    fourLetterOutput.value = 'Error: ' + e.message
  }
}

let searchTimer: ReturnType<typeof setTimeout> | null = null

function onSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  const query = searchQuery.value.trim()
  if (!query) {
    searchResults.value = []
    return
  }
  searchTimer = setTimeout(async () => {
    try {
      searchResults.value = (await api.searchNodes(props.serverId, query)) || []
    } catch {
      searchResults.value = []
    }
  }, 300)
}

async function selectSearchResult(path: string) {
  searchResults.value = []
  searchQuery.value = ''
  await ensureExpanded(path)
  await onSelectNode({ path })
}

async function connectAndLoad() {
  connecting.value = true
  selectedNode.value = null
  selectedNodePath.value = null
  childrenMap.clear()
  expanded.clear()
  expanded.add('/')
  try {
    await api.connect(props.serverId)
    state.serverStatuses[props.serverId] = 'connected'
    connecting.value = false
    await loadChildren('/', true)
  } catch (e: any) {
    toast('Connection failed: ' + e.message, 'error')
    state.serverStatuses[props.serverId] = 'disconnected'
    connecting.value = false
  }
}

let nodeWs: WebSocket | null = null

function connectWs(serverId: string) {
  if (nodeWs) {
    nodeWs.close()
    nodeWs = null
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  try {
    nodeWs = new WebSocket(
      `${protocol}//${window.location.host}/ws/nodes/${encodeURIComponent(serverId)}`,
    )
    nodeWs.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data)
        if (!['added', 'updated', 'deleted'].includes(message.type)) return
        const path = message.path || '/'
        const parent = parentPath(path)

        if (message.type === 'updated') {
          // Metadata/data changed; the tree structure does not.
          if (path === selectedNodePath.value) {
            onSelectNode({ path })
          }
          return
        }

        const siblings = childrenMap.get(parent)
        if (!siblings) {
          // The parent is not loaded (or not expanded): let the debounced
          // loader decide whether it needs a refresh.
          scheduleRefresh(parent)
          return
        }

        if (message.type === 'added') {
          if (!siblings.some((row) => row.path === path)) {
            siblings.push({
              path,
              name: path.substring(path.lastIndexOf('/') + 1),
              depth: depthOf(path),
              numChildren: 0,
            })
          }
        } else if (message.type === 'deleted') {
          const index = siblings.findIndex((row) => row.path === path)
          if (index >= 0) {
            siblings.splice(index, 1)
          }
          if (
            selectedNodePath.value === path ||
            selectedNodePath.value?.startsWith(path + '/')
          ) {
            selectedNode.value = null
            selectedNodePath.value = null
          }
        }

        if (parent === '/') {
          rootNumChildren.value = siblings.length
        }
        if (selectedNodePath.value === parent) {
          onSelectNode({ path: parent })
        }
      } catch {
        // ignore malformed frames
      }
    }
  } catch (e) {
    console.warn(e)
  }
}

watch(
  () => props.serverId,
  (newId) => {
    connectAndLoad()
    connectWs(newId)
  },
)

onMounted(() => {
  connectAndLoad()
  connectWs(props.serverId)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearTimeout(refreshTimer)
    refreshTimer = null
  }
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
  if (nodeWs) {
    nodeWs.close()
    nodeWs = null
  }
})
</script>

<style scoped>
.node-browser {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.tab-bar {
  display: flex;
  background: var(--header-bg);
  border-bottom: 1px solid var(--border-color);
  padding: 0 8px;
  flex-shrink: 0;
}
.tab-item {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 10px 16px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary);
  border-bottom: 2px solid transparent;
  transition: all 0.12s;
  margin-bottom: -1px;
}
.tab-item:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
  border-radius: 6px 6px 0 0;
}
.tab-item.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
  background: transparent;
}
.tab-icon {
  display: inline-flex;
  align-items: center;
}
.tab-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.terminal-tab {
  background: #1a1a1a;
}
.toolbar {
  display: flex;
  align-items: center;
  padding: 7px 16px;
  background: var(--toolbar-bg);
  border-bottom: 1px solid var(--border-color);
  gap: 8px;
  min-height: 40px;
  flex-shrink: 0;
}
.breadcrumb {
  font-size: 13px;
  color: var(--text-secondary);
  flex: 1;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}
.crumb {
  cursor: pointer;
  color: var(--accent);
  padding: 2px 5px;
  border-radius: 4px;
  font-size: 12px;
  display: inline-flex;
  align-items: center;
}
.crumb:hover {
  background: var(--accent-subtle);
}
.crumb-sep {
  color: var(--text-muted);
  padding: 0 2px;
  display: inline-flex;
  align-items: center;
}
.toolbar-actions {
  display: flex;
  gap: 4px;
}
.browse-split {
  flex: 1;
  display: flex;
  overflow: hidden;
}
.detail-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
}
.tree-panel {
  width: 300px;
  min-width: 240px;
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}
.search-box {
  padding: 10px 12px;
  position: relative;
  flex-shrink: 0;
}
.search-box input {
  width: 100%;
  padding: 7px 10px;
  border: 1px solid var(--input-border);
  border-radius: 6px;
  background: var(--input-bg);
  color: var(--text-primary);
  font-size: 12px;
  outline: none;
}
.search-box input:focus {
  border-color: var(--input-focus);
}
.search-dropdown {
  position: absolute;
  top: 100%;
  left: 8px;
  right: 8px;
  z-index: 100;
  max-height: 260px;
  overflow-y: auto;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}
.search-hit {
  padding: 6px 12px;
  cursor: pointer;
  font-size: 12px;
  color: var(--text-primary);
}
.search-hit:hover {
  background: var(--bg-hover);
}
.tree-scroll {
  flex: 1;
  min-height: 0;
  padding: 4px 0;
  overflow: hidden;
}
.tree-virtual {
  height: 100%;
}
.tree-row {
  height: 26px;
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-primary);
  border-left: 3px solid transparent;
  white-space: nowrap;
  user-select: none;
}
.tree-row:hover {
  background: var(--bg-hover);
}
.tree-row.selected {
  background: var(--accent-subtle);
  border-left-color: var(--accent);
}
.tree-arrow {
  width: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--text-muted);
}
.tree-arrow svg {
  transition: transform 0.15s;
}
.tree-arrow svg.rotated {
  transform: rotate(90deg);
}
.tree-icon {
  width: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--text-muted);
}
.tree-label {
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
}
.tree-badge {
  font-size: 10px;
  padding: 0 6px;
  border-radius: 8px;
  background: var(--bg-tertiary);
  color: var(--text-muted);
  flex-shrink: 0;
  margin-right: 6px;
  font-weight: 500;
}
.tree-spinner {
  animation: spin 0.9s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.tree-empty {
  padding: 20px;
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
}
.cmd-output {
  margin-top: 12px;
  padding: 12px;
  background: var(--code-bg);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  font-family: 'SF Mono', Monaco, monospace;
  font-size: 12px;
  white-space: pre-wrap;
  min-height: 200px;
  overflow: auto;
}
.ctx-menu {
  position: fixed;
  z-index: 9998;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  padding: 4px;
  min-width: 140px;
}
.ctx-item {
  padding: 8px 14px;
  cursor: pointer;
  font-size: 13px;
  border-radius: 4px;
  color: var(--text-primary);
}
.ctx-item:hover {
  background: var(--bg-hover);
}
.ctx-item.danger {
  color: var(--danger);
}
.ctx-item.danger:hover {
  background: rgba(198, 40, 40, 0.08);
}
.ctx-sep {
  height: 1px;
  background: var(--border-color);
  margin: 4px 8px;
}
.ctx-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9997;
}
</style>
