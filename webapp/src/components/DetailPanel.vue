<template>
  <div class="detail-panel">
    <div class="detail-header">
      <div class="detail-path">{{ node.path }}</div>
      <div class="detail-actions">
        <button class="btn btn-small btn-primary" @click="$emit('save', nodeData)" :disabled="saving">{{ t('node.save') }}</button>
        <button class="btn btn-small btn-danger" @click="$emit('delete', node)">{{ t('node.deleteNode') }}</button>
      </div>
    </div>
    <div class="detail-meta">
      <div class="meta-item"><span class="meta-key">{{ t('node.dataVersion') }}</span><span class="meta-val">{{ node.dataVersion }}</span></div>
      <div class="meta-item"><span class="meta-key">{{ t('node.dataLength') }}</span><span class="meta-val">{{ node.dataLength }}</span></div>
      <div class="meta-item"><span class="meta-key">{{ t('node.children') }}</span><span class="meta-val">{{ node.numChildren }}</span></div>
      <div class="meta-item"><span class="meta-key">{{ t('node.ephemeral') }}</span><span class="meta-val">{{ node.ephemeral ? 'Yes' : 'No' }}</span></div>
      <div class="meta-item"><span class="meta-key">{{ t('node.createdTime') }}</span><span class="meta-val">{{ formatTime(node.creationTime) }}</span></div>
      <div class="meta-item"><span class="meta-key">{{ t('node.modifiedTime') }}</span><span class="meta-val">{{ formatTime(node.modifiedTime) }}</span></div>
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
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { t } from '../i18n.ts'
import { state } from '../main.ts'
import CodeEditor from './CodeEditor.vue'

const props = defineProps({
  node: { type: Object, required: true },
  nodeData: { type: String, default: '' },
  saving: { type: Boolean, default: false },
})
const emit = defineEmits(['save', 'delete', 'update:nodeData'])

const isDark = computed(() => state.config.theme === 'dark')
const editorFmt = ref('raw')

function toggleFmt(fmt) {
  editorFmt.value = editorFmt.value === fmt ? 'raw' : fmt
}

const editorLang = computed(() => editorFmt.value === 'json' ? 'json' : editorFmt.value === 'xml' ? 'xml' : 'text')

const displayData = computed(() => {
  const raw = props.nodeData || ''
  if (editorFmt.value === 'raw') return raw
  try {
    if (editorFmt.value === 'json') return JSON.stringify(JSON.parse(raw), null, 2)
    if (editorFmt.value === 'xml') return raw.replace(/>\s*</g, '>\n<').split('\n').map((l, i) => '  '.repeat(Math.max(0, i + (l.startsWith('</') ? -1 : 0))) + l).join('\n')
  } catch (e) { return raw }
  return raw
})

function onEditorChange(val) {
  if (editorFmt.value === 'raw') emit('update:nodeData', val)
}

function formatTime(ms) { return ms ? new Date(ms).toLocaleString() : '-' }
</script>

<style scoped>
.detail-panel { flex:1; display:flex; flex-direction:column; overflow:hidden; min-height:0; }
.detail-header { display:flex; align-items:center; justify-content:space-between; padding:10px 16px; background:var(--toolbar-bg); border-bottom:1px solid var(--border-color); gap:8px; flex-shrink:0; }
.detail-path { font-size:12px; color:var(--text-secondary); font-family:'SF Mono',Monaco,Consolas,monospace; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.detail-actions { display:flex; gap:4px; flex-shrink:0; }
.detail-meta { display:grid; grid-template-columns:repeat(auto-fill,minmax(150px,1fr)); gap:0; border-bottom:1px solid var(--border-color); flex-shrink:0; background:var(--bg-primary); }
.meta-item { padding:6px 12px; display:flex; flex-direction:column; gap:1px; border-bottom:1px solid var(--border-light); border-right:1px solid var(--border-light); }
.meta-item:nth-child(odd) { background:var(--bg-secondary); }
.meta-key { font-size:10px; color:var(--text-muted); font-weight:600; letter-spacing:0.3px; text-transform:uppercase; }
.meta-val { font-size:13px; font-family:'SF Mono',Monaco,Consolas,monospace; color:var(--text-primary); word-break:break-all; }
.editor-section { flex:1; display:flex; flex-direction:column; min-height:0; }
.editor-toolbar { display:flex; align-items:center; padding:4px 12px; background:var(--toolbar-bg); border-bottom:1px solid var(--border-color); gap:4px; }
.editor-label { font-size:10px; font-weight:700; color:var(--text-muted); text-transform:uppercase; letter-spacing:0.5px; }
.editor-spacer { flex:1; }
.editor-fmt-btn { padding:2px 8px; border:1px solid var(--border-color); border-radius:4px; background:transparent; color:var(--text-muted); font-size:11px; font-weight:600; cursor:pointer; transition:all 0.12s; }
.editor-fmt-btn:hover { background:var(--bg-hover); color:var(--text-primary); }
.editor-fmt-btn.active { background:var(--accent); color:#fff; border-color:var(--accent); }
.editor-wrap { flex:1; overflow:hidden; display:flex; flex-direction:column; min-height:0; }
</style>
