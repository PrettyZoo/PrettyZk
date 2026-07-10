<template>
  <div ref="editorContainer" class="cm-editor-wrapper"></div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { EditorView, basicSetup } from 'codemirror'
import { EditorState } from '@codemirror/state'
import { json } from '@codemirror/lang-json'
import { xml } from '@codemirror/lang-xml'
import { oneDark } from '@codemirror/theme-one-dark'

const props = defineProps({
  modelValue: { type: String, default: '' },
  language: { type: String, default: 'text' }, // json, xml, text
  readonly: { type: Boolean, default: false },
  dark: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue'])

const editorContainer = ref(null)
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

onMounted(() => {
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
})

onUnmounted(() => {
  if (view) view.destroy()
})

watch(() => props.modelValue, (newVal) => {
  if (view && newVal !== view.state.doc.toString()) {
    view.dispatch({
      changes: { from: 0, to: view.state.doc.length, insert: newVal || '' }
    })
  }
})
</script>

<style scoped>
.cm-editor-wrapper {
  height: 100%;
  overflow: hidden;
}
.cm-editor-wrapper :deep(.cm-editor) {
  height: 100%;
}
.cm-editor-wrapper :deep(.cm-scroller) {
  overflow: auto;
}
</style>
