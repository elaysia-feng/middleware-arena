<template>
  <div ref="container" class="monaco-editor" :style="{ height }" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as monaco from 'monaco-editor'

const props = withDefaults(
  defineProps<{
    modelValue: string
    language?: string
    height?: string
    readOnly?: boolean
    theme?: 'vs' | 'vs-dark' | 'hc-black'
  }>(),
  {
    language: 'plaintext',
    height: '300px',
    readOnly: false,
    theme: 'vs',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  change: [value: string]
}>()

const container = ref<HTMLElement | null>(null)
let editor: monaco.editor.IStandaloneCodeEditor | null = null
let suppressChange = false

onMounted(() => {
  if (!container.value) return
  // 配置 monaco 环境：如果 vite 没把 worker 包好，禁用 web worker 让其跑在主线程
  if (!(self as any).MonacoEnvironment) {
    ;(self as any).MonacoEnvironment = { getWorker: () => ({ postMessage: () => {}, terminate: () => {} }) }
  }
  editor = monaco.editor.create(container.value, {
    value: props.modelValue,
    language: props.language,
    theme: props.theme,
    readOnly: props.readOnly,
    automaticLayout: true,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    fontSize: 13,
  })
  editor.onDidChangeModelContent(() => {
    if (suppressChange || !editor) return
    const v = editor.getValue()
    emit('update:modelValue', v)
    emit('change', v)
  })
})

watch(
  () => props.modelValue,
  (v) => {
    if (!editor) return
    if (editor.getValue() !== v) {
      suppressChange = true
      editor.setValue(v)
      suppressChange = false
    }
  },
)

watch(
  () => props.language,
  (lang) => {
    const model = editor?.getModel()
    if (model) monaco.editor.setModelLanguage(model, lang)
  },
)

onBeforeUnmount(() => {
  editor?.dispose()
  editor = null
})
</script>

<style scoped>
.monaco-editor {
  width: 100%;
  min-height: 100px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}
</style>
