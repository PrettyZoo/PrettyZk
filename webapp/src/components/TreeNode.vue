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
      <span class="tree-badge" v-if="node.numChildren > 0 && !node.childrenLoaded">{{ node.numChildren }}</span>
      <span v-if="loading" class="tree-loading"> ⟳</span>
    </div>
    <div v-if="expanded && children.length > 0" class="tree-children-area">
      <TreeNode
        v-for="child in children" :key="child.path"
        :node="child"
        :depth="depth + 1"
        :selected-path="selectedPath"
        :server-id="serverId"
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

<script setup>
import { ref } from 'vue'
import { api } from '../api.js'
import { t } from '../i18n.js'

const props = defineProps({
  node: { type: Object, required: true },
  depth: { type: Number, default: 0 },
  selectedPath: { type: String, default: null },
  serverId: { type: String, default: null },
})

const emit = defineEmits(['select', 'toggle'])
const expanded = ref(false)
const children = ref([])
const loading = ref(false)

async function toggleExpand() {
  if (expanded.value) {
    expanded.value = false
    children.value = []
    return
  }
  if (props.node.childrenLoaded) {
    expanded.value = true
    return
  }
  loading.value = true
  try {
    const sid = props.serverId
    if (!sid) return
    const data = await api.listNodes(sid, props.node.path)
    children.value = (data.children || []).map(c => ({
      ...c,
      name: c.path.substring(c.path.lastIndexOf('/') + 1) || c.name || c.path,
      childrenLoaded: false,
    }))
    props.node.childrenLoaded = true
    expanded.value = true
  } catch (e) {
    console.warn('Failed to load children:', e)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.tree-node {
  display: flex; align-items: center; gap: 3px;
  padding: 3px 0; cursor: pointer;
  font-size: 13px; color: var(--text-primary);
  border-left: 3px solid transparent;
  white-space: nowrap;
  user-select: none;
}
.tree-node:hover { background: var(--bg-hover); }
.tree-node.selected { background: var(--bg-selected); border-left-color: var(--accent); }
.tree-node.ephemeral { color: var(--node-ephemeral); }
.tree-arrow { width: 16px; text-align: center; flex-shrink: 0; font-size: 9px; color: var(--text-muted); cursor: pointer; transition: transform 0.15s; }
.tree-arrow.expanded { transform: rotate(90deg); }
.tree-arrow.no-children { visibility: hidden; }
.tree-icon { width: 16px; text-align: center; flex-shrink: 0; font-size: 12px; }
.tree-label { overflow: hidden; text-overflow: ellipsis; flex: 1; min-width: 0; }
.tree-badge { font-size: 10px; padding: 0 5px; border-radius: 8px; background: var(--bg-tertiary); color: var(--text-muted); flex-shrink: 0; margin-left: auto; }
.tree-loading { color: var(--accent); font-size: 12px; flex-shrink: 0; }
.tree-children-area { }
.tree-empty-child { padding: 2px 0; font-size: 12px; color: var(--text-muted); }
</style>
