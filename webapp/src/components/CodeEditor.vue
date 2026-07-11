<template>
  <div ref="editorContainer" class="cm-editor-wrapper">
    <textarea v-if="cmFailed" class="cm-fallback" v-model="localValue" :readonly="readonly" @input="onInput"></textarea>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { EditorView, basicSetup } from 'codemirror'
import { EditorState } from '@codemirror/state'
import { json } from '@codemirror/lang-json'
import { xml } from '@codemirror/lang-xml'
import { oneDark } from '@codemirror/theme-one-dark'

const props = defineProps({
  modelValue: { type: String, default: '' },
  language: { type: String, default: 'text' },
  readonly: { type: Boolean, default: false },
  dark: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue'])
const editorContainer = ref(null)
const cmFailed = ref(false)
const localValue = ref(props.modelValue || '')

let view = null

function detectLanguage(data) {
  if (!data) return null
  const trimmed = data.trim()
  if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
    try { JSON.parse(trimmed); return json() } catch (e) { /* not json */ }
  }
  if (trimmed.startsWith('<')) return xml()
  return null
}

onMounted(async () => {
  await nextTick()
  try {
    if (!editorContainer.value) return
    const langExt = props.language === 'json' ? json() :
                    props.language === 'xml' ? xml() :
                    detectLanguage(props.modelValue)

    const extensions = [basicSetup]
    if (langExt) extensions.push(langExt)
    if (props.dark) extensions.push(oneDark)

    if (!props.readonly) {
      extensions.push(EditorView.updateListener.of(update => {
        if (update.docChanged) {
          emit('update:modelValue', update.state.doc.toString())
        }
      }))
    }

    const state = EditorState.create({
      doc: props.modelValue || '',
      extensions: [...extensions, EditorView.editable.of(!props.readonly)],
    })

    view = new EditorView({ state, parent: editorContainer.value })
  } catch (e) {
    console.warn('CodeMirror init failed, using textarea fallback:', e)
    cmFailed.value = true
    localValue.value = props.modelValue || ''
  }
})

onUnmounted(() => {
  if (view) view.destroy()
})

watch(() => props.modelValue, (newVal) => {
  if (cmFailed.value) {
    localValue.value = newVal || ''
    return
  }
  if (view && newVal !== view.state.doc.toString()) {
    view.dispatch({
      changes: { from: 0, to: view.state.doc.length, insert: newVal || '' }
    })
  }
})

function onInput(e) {
  emit('update:modelValue', e.target.value)
}
</script>

<style scoped>
.cm-editor-wrapper { height:100%; min-height:150px; overflow:hidden; flex:1; display:flex; }
.cm-editor-wrapper :deep(.cm-editor) { height:100%; }
.cm-editor-wrapper :deep(.cm-scroller) { overflow:auto; }
.cm-editor-wrapper :deep(.cm-content) { min-height:150px; }
.cm-fallback { width:100%; height:100%; min-height:150px; padding:12px; border:none; resize:none; outline:none; background:var(--bg-primary); color:var(--text-primary); font-family:'SF Mono',Monaco,Consolas,monospace; font-size:13px; }
</style>
