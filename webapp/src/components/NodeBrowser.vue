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
          <n-button size="small" :loading="loading" @click="sync">
            <template #icon><RefreshCw :size="13" /></template>
            {{ t('node.sync') }}
          </n-button>
          <n-button size="small" type="primary" @click="showAddDialog">
            <template #icon><Plus :size="13" /></template>
            {{ t('node.add') }}
          </n-button>
        </div>
      </div>

      <div class="browse-split">
        <div class="tree-panel" :style="{ width: treeWidth + 'px' }">
          <div class="search-box">
            <n-input
              v-model:value="searchQuery"
              size="small"
              clearable
              :placeholder="t('node.search')"
              @input="onSearch"
            >
              <template #prefix><Search :size="13" /></template>
            </n-input>
            <div v-if="searchResults.length > 0" class="search-dropdown">
              <div
                v-for="r in searchResults"
                :key="r.path"
                class="search-hit"
                @click="selectSearchResult(r.path)"
              >
                {{ r.path }}
              </div>
              <div v-if="searchTruncated" class="search-truncated">
                {{ t('node.searchTruncated') }}
              </div>
            </div>
          </div>

          <div class="tree-scroll" tabindex="0" role="tree" aria-label="ZooKeeper nodes" @keydown="onTreeKeydown">
            <n-virtual-list
              v-if="visibleRows.length > 0"
              ref="treeListRef"
              :items="visibleRows"
              :item-size="26"
              key-field="path"
              class="tree-virtual"
            >
              <template #default="{ item }">
                <div
                  v-if="item.type === 'more'"
                  class="tree-row tree-more"
                  :style="{ paddingLeft: 8 + item.depth * 18 + 'px' }"
                  @click="selectRow(item)"
                >
                  <span class="tree-arrow"></span>
                  <span class="tree-icon">
                    <Loader2
                      v-if="loadingPaths.has(item.parentPath)"
                      :size="13"
                      class="tree-spinner"
                    />
                    <Plus v-else :size="13" />
                  </span>
                  <span class="tree-label tree-more-label">{{ t('node.loadMore') }}</span>
                </div>
                <div
                  v-else
                  class="tree-row"
                  role="treeitem"
                  :aria-selected="item.path === selectedNodePath"
                  :aria-expanded="expanded.has(item.path)"
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

        <div class="split-handle" @mousedown.prevent="startResize"></div>

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
        <div style="max-width: 200px; margin-bottom: 12px">
          <div class="field-label">{{ t('node.cmd') }}</div>
          <n-input
            v-model:value="fourLetterCmd"
            maxlength="4"
            placeholder="stat"
            style="text-transform: lowercase"
          />
        </div>
        <n-button type="primary" @click="runFourLetterCmd">{{ t('node.execute') }}</n-button>
        <pre class="cmd-output">{{ fourLetterOutput }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { NButton, NInput, NVirtualList } from 'naive-ui'
import {
  ChevronRight,
  Folder,
  FolderOpen,
  FolderTree,
  Loader2,
  Plus,
  Search,
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
  type?: 'node' | 'more'
  parentPath?: string
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
const searchTruncated = ref(false)
const fourLetterCmd = ref('')
const fourLetterOutput = ref('')
const connecting = ref(true)
const showAddForm = ref(false)
const saving = ref(false)
const treeWidth = ref(Number(localStorage.getItem('prettyzk.treeWidth')) || 300)
const treeListRef = ref<any>(null)

const expanded = reactive(new Set<string>())
const loadingPaths = reactive(new Set<string>())
const childrenMap = reactive(new Map<string, TreeRow[]>())
const hasMoreMap = reactive(new Map<string, boolean>())
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
    if (hasMoreMap.get(row.path)) {
      rows.push({
        type: 'more',
        path: `__more__:${row.path}`,
        name: '',
        depth: row.depth + 1,
        numChildren: 0,
        parentPath: row.path,
      })
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
  if (node.type === 'more') return
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
  if (row.type === 'more') return false
  if (row.path === '/') return true
  if (!childrenMap.has(row.path)) return true
  return (childrenMap.get(row.path)?.length ?? 0) > 0
}

async function loadChildren(path: string, force = false, offset = 0, append = false) {
  if (loadingPaths.has(path)) return
  if (!force && offset === 0 && childrenMap.has(path)) return
  loadingPaths.add(path)
  try {
    const data = await api.listChildren(props.serverId, path, offset)
    const depth = depthOf(path) + 1
    const page: TreeRow[] = (data.children || []).map((child: any) => ({
      path: path === '/' ? '/' + child.name : path + '/' + child.name,
      name: child.name,
      depth,
      numChildren: child.numChildren || 0,
    }))
    if (append) {
      const existing = childrenMap.get(path)
      if (existing) {
        existing.push(...page)
      } else {
        childrenMap.set(path, page)
      }
    } else {
      childrenMap.set(path, page)
    }
    hasMoreMap.set(path, !!data.hasMore)
    if (path === '/') {
      rootNumChildren.value = data.total || 0
    }
  } catch (e: any) {
    toast(t('node.loadFailed', { msg: e.message }), 'error')
  } finally {
    loadingPaths.delete(path)
  }
}

async function loadMoreChildren(parent: string) {
  const existing = childrenMap.get(parent) || []
  await loadChildren(parent, true, existing.length, true)
}

async function toggleNode(row: TreeRow) {
  if (row.type === 'more') {
    if (row.parentPath) await loadMoreChildren(row.parentPath)
    return
  }
  if (expanded.has(row.path)) {
    expanded.delete(row.path)
    return
  }
  expanded.add(row.path)
  await loadChildren(row.path)
}

async function ensureChildLoaded(parent: string, childPath: string) {
  while (!(childrenMap.get(parent) || []).some((row) => row.path === childPath)) {
    if (!childrenMap.has(parent)) {
      await loadChildren(parent)
    } else if (hasMoreMap.get(parent)) {
      await loadMoreChildren(parent)
    } else {
      break
    }
  }
}

async function ensureExpanded(path: string) {
  await loadChildren('/')
  expanded.add('/')
  const parts = path.split('/').filter(Boolean)
  let current = '/'
  for (const part of parts) {
    const next = current === '/' ? '/' + part : current + '/' + part
    await ensureChildLoaded(current, next)
    expanded.add(current)
    current = next
  }
}

async function selectRow(row: TreeRow) {
  if (row.type === 'more') {
    if (row.parentPath) await loadMoreChildren(row.parentPath)
    return
  }
  await onSelectNode({ path: row.path })
}

async function selectKeyboardRow(row: TreeRow) {
  const index = visibleRows.value.findIndex((item) => item.path === row.path)
  if (index >= 0) {
    treeListRef.value?.scrollToIndex?.(index)
  }
  await onSelectNode({ path: row.path })
}

async function onTreeKeydown(event: KeyboardEvent) {
  const rows = visibleRows.value.filter((row) => row.type !== 'more')
  if (rows.length === 0) return
  let index = rows.findIndex((row) => row.path === selectedNodePath.value)
  const current = index >= 0 ? rows[index] : null

  if (event.key === 'ArrowDown') {
    event.preventDefault()
    index = Math.min(rows.length - 1, index + 1)
    await selectKeyboardRow(rows[index])
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    index = Math.max(0, index < 0 ? 0 : index - 1)
    await selectKeyboardRow(rows[index])
  } else if (event.key === 'ArrowRight') {
    event.preventDefault()
    if (current && !expanded.has(current.path) && hasChildren(current)) {
      await toggleNode(current)
    }
  } else if (event.key === 'ArrowLeft') {
    event.preventDefault()
    if (current && expanded.has(current.path)) {
      expanded.delete(current.path)
    } else if (current) {
      const parent = parentPath(current.path)
      const parentRow = rows.find((row) => row.path === parent)
      if (parentRow) await selectKeyboardRow(parentRow)
    }
  } else if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    if (current) await onSelectNode({ path: current.path })
  }
}

function startResize(event: MouseEvent) {
  const startX = event.clientX
  const startWidth = treeWidth.value
  const onMove = (moveEvent: MouseEvent) => {
    const next = startWidth + moveEvent.clientX - startX
    treeWidth.value = Math.min(640, Math.max(220, next))
  }
  const onUp = () => {
    localStorage.setItem('prettyzk.treeWidth', String(treeWidth.value))
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}

let selectSequence = 0

async function onSelectNode(row: { path: string }) {
  const sequence = ++selectSequence
  selectedNodePath.value = row.path
  try {
    const data = await api.getNodeDetail(props.serverId, row.path)
    if (sequence !== selectSequence) return
    selectedNode.value = data
    nodeData.value = data.data || ''
  } catch (e: any) {
    if (sequence === selectSequence) {
      toast(t('node.loadFailed', { msg: e.message }), 'error')
    }
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
  hasMoreMap.clear()
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
  hasMoreMap.delete(path)
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
    hasMoreMap.clear()
    expanded.clear()
    expanded.add('/')
    for (const path of paths) {
      if (path !== '/') expanded.add(path)
    }
    await Promise.all(Array.from(paths).map((path) => loadChildren(path, true)))
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
    searchTruncated.value = false
    return
  }
  searchTimer = setTimeout(async () => {
    try {
      const result = await api.searchNodes(props.serverId, query)
      searchResults.value = result?.nodes || []
      searchTruncated.value = !!result?.truncated
    } catch {
      searchResults.value = []
      searchTruncated.value = false
    }
  }, 300)
}

async function selectSearchResult(path: string) {
  searchResults.value = []
  searchTruncated.value = false
  searchQuery.value = ''
  await ensureExpanded(path)
  await onSelectNode({ path })
}

async function connectAndLoad() {
  connecting.value = true
  selectedNode.value = null
  selectedNodePath.value = null
  childrenMap.clear()
  hasMoreMap.clear()
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
  background: var(--bg-primary);
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
.split-handle {
  width: 4px;
  flex-shrink: 0;
  cursor: col-resize;
  background: transparent;
  transition: background 0.12s;
}
.split-handle:hover {
  background: var(--accent-light);
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
  min-width: 220px;
  max-width: 640px;
  flex-shrink: 0;
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
.field-label {
  margin-bottom: 5px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
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
.search-truncated {
  padding: 6px 12px;
  border-top: 1px solid var(--border-color);
  color: var(--text-muted);
  font-size: 11px;
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
.tree-scroll:focus {
  outline: none;
}
.tree-scroll:focus-visible {
  outline: 1px solid var(--accent-light);
  outline-offset: -1px;
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
.tree-more-label { color: var(--accent); font-size: 12px; }
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
