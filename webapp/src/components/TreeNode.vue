<template>
  <div>
    <div class="tree-node" :class="{ selected: node.path === selectedPath, ephemeral: node.ephemeral }"
         :style="{ paddingLeft: (8 + depth * 18) + 'px' }"
         @contextmenu.prevent="$emit('node-ctx', $event, node)">
      <span class="tree-arrow" :class="{ expanded: expanded }" @click.stop="toggleExpand">
        ▶
      </span>
      <span class="tree-icon">{{ node.ephemeral ? '⚡' : (expanded ? '📂' : '📁') }}</span>
      <span class="tree-label" @click.stop="$emit('select', node)">{{ node.name }}</span>
      <span class="tree-badge" v-if="node.numChildren > 0 && !childrenLoaded">{{ node.numChildren }}</span>
      <span v-if="loading" class="tree-loading"> ⟳</span>
    </div>
    <div v-if="expanded && children.length > 0" class="tree-children-area">
      <TreeNode
        v-for="child in children" :key="child.path"
        :node="child"
        :depth="depth + 1"
        :selected-path="selectedPath"
        :server-id="serverId"
        :refresh-key="refreshKey"
        @select="(n) => $emit('select', n)"
        @toggle="(n) => $emit('toggle', n)"
        @node-ctx="(e, n) => $emit('node-ctx', e, n)"
      />
    </div>
    <div v-if="expanded && !loading && children.length === 0" class="tree-empty-child">
      <span class="tree-label" :style="{ paddingLeft: (8 + (depth + 1) * 18) + 'px' }">({{ t('node.empty') }})</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { api } from '../api.ts'
import { t } from '../i18n.ts'

const props = defineProps({
  node: { type: Object, required: true },
  depth: { type: Number, default: 0 },
  selectedPath: { type: String, default: null },
  serverId: { type: String, default: null },
  refreshKey: { type: Number, default: 0 },
})

const emit = defineEmits(['select', 'toggle', 'node-ctx'])
const expanded = ref(false)
const children = ref([])
const childrenLoaded = ref(false)
const loading = ref(false)

// When refreshKey changes, reload children if this node is currently expanded
watch(() => props.refreshKey, () => {
  if (expanded.value) {
    reloadChildren()
  }
})

async function toggleExpand() {
  if (expanded.value) {
    // Collapse
    expanded.value = false
    return
  }
  // Expand
  await loadChildren()
  expanded.value = true
}

async function loadChildren() {
  if (childrenLoaded.value) {
    // Already loaded, just expand
    return
  }
  // Pre-populate from props for first-level children
  if (!childrenLoaded.value && props.node.children && props.node.children.length > 0 && children.value.length === 0) {
    children.value = props.node.children.map(c => ({ ...c }))
    childrenLoaded.value = true
    // Still refresh in background for fresh data
    reloadChildren().catch(() => {})
    return
  }
  await reloadChildren()
}

async function reloadChildren() {
  loading.value = true
  try {
    const sid = props.serverId
    if (!sid) return
    const data = await api.listNodes(sid, props.node.path)
    children.value = (data.children || []).map(c => ({
      ...c,
      name: c.path.substring(c.path.lastIndexOf('/') + 1) || c.name || c.path,
    }))
    childrenLoaded.value = true
  } catch (e) {
    console.warn('Failed to load children:', e)
    window.__toast?.('Failed to load children: ' + e.message, 'error')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.tree-node {
  display: flex; align-items: center; gap: 4px;
  padding: 4px 0; cursor: pointer;
  font-size: 13px; color: var(--text-primary);
  border-left: 3px solid transparent;
  white-space: nowrap; user-select: none;
  transition: background 0.08s;
}
.tree-node:hover { background: var(--bg-hover); }
.tree-node.selected { background: var(--bg-selected); border-left-color: var(--accent); }
.tree-node.selected:hover { background: #d4e8ef; }
.tree-node.ephemeral { color: var(--node-ephemeral); }
.tree-arrow { width: 16px; text-align: center; flex-shrink: 0; font-size: 8px; color: var(--text-muted); cursor: pointer; transition: transform 0.15s, color 0.15s; }
.tree-arrow:hover { color: var(--text-secondary); }
.tree-arrow.expanded { transform: rotate(90deg); }
.tree-icon { width: 18px; text-align: center; flex-shrink: 0; font-size: 13px; }
.tree-label { overflow: hidden; text-overflow: ellipsis; flex: 1; min-width: 0; font-size: 13px; }
.tree-badge { font-size: 10px; padding: 0 6px; border-radius: 8px; background: var(--bg-tertiary); color: var(--text-muted); flex-shrink: 0; margin-left: auto; font-weight: 500; }
.tree-loading { color: var(--accent); font-size: 12px; flex-shrink: 0; }
.tree-children-area { }
.tree-empty-child { padding: 3px 0 3px 0; font-size: 12px; color: var(--text-muted); font-style: italic; }
</style>
