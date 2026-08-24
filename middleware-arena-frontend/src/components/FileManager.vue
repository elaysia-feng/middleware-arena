<template>
  <div class="file-manager">
    <div class="fm-toolbar">
      <el-button type="primary" :icon="Plus" @click="addFile">添加文件</el-button>
      <el-tag>{{ files.length }} 个文件</el-tag>
      <span class="fm-tip">
        每条文件会被 Runner 写入磁盘、交给编译/打包步骤；runner 端 SutBuilder 会跳过 <code>editable=false</code> 的文件
      </span>
    </div>

    <el-empty v-if="files.length === 0" description="还没有文件，点上面添加" />
    <div v-else class="fm-list">
      <div v-for="(f, idx) in files" :key="idx" class="fm-item">
        <div class="fm-meta">
          <el-input
            v-model="f.path"
            placeholder="如 src/main/java/App.java"
            class="fm-path"
            clearable
          />
          <el-select v-model="f.language" placeholder="语言" class="fm-lang">
            <el-option label="java" value="java" />
            <el-option label="go" value="go" />
            <el-option label="python" value="python" />
            <el-option label="javascript" value="javascript" />
            <el-option label="typescript" value="typescript" />
            <el-option label="yaml" value="yaml" />
            <el-option label="json" value="json" />
            <el-option label="xml" value="xml" />
            <el-option label="shell" value="shell" />
            <el-option label="plaintext" value="plaintext" />
          </el-select>
          <el-checkbox v-model="f.editable">可编辑</el-checkbox>
          <el-button size="small" @click="toggle(idx)">
            {{ isOpen(idx) ? '收起' : '展开内容' }}
          </el-button>
          <el-button size="small" type="danger" @click="remove(idx)">删除</el-button>
        </div>
        <MonacoEditor
          v-if="isOpen(idx)"
          v-model="f.content"
          :language="f.language || 'plaintext'"
          height="280px"
        />
      </div>
    </div>

    <div class="fm-raw">
      <el-button text @click="rawOpen = !rawOpen">
        {{ rawOpen ? '隐藏' : '查看' }} filesJson 序列化预览（写入 OSS 的内容）
      </el-button>
      <MonacoEditor v-if="rawOpen" :model-value="rawJson" language="json" height="180px" read-only />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import MonacoEditor from '@/components/MonacoEditor.vue'

export interface FilePatch {
  path: string
  language: string
  content: string
  editable: boolean
}

const props = defineProps<{ modelValue: FilePatch[] }>()
const emit = defineEmits<{ 'update:modelValue': [files: FilePatch[]] }>()

const files = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const openSet = ref<Set<number>>(new Set())
const rawOpen = ref(false)

function isOpen(idx: number) {
  return openSet.value.has(idx)
}
function toggle(idx: number) {
  const next = new Set(openSet.value)
  if (next.has(idx)) next.delete(idx)
  else next.add(idx)
  openSet.value = next
}

function newFile(): FilePatch {
  return { path: '', language: 'java', content: '', editable: true }
}
function addFile() {
  const updated = [...files.value, newFile()]
  // 默认展开刚加进来的那条
  openSet.value = new Set([...openSet.value, updated.length - 1])
  emit('update:modelValue', updated)
}
function remove(idx: number) {
  const updated = files.value.slice()
  updated.splice(idx, 1)
  // openSet 的索引需要重新映射
  const next = new Set<number>()
  for (const i of openSet.value) {
    if (i < idx) next.add(i)
    else if (i > idx) next.add(i - 1)
  }
  openSet.value = next
  emit('update:modelValue', updated)
}

const rawJson = computed(() =>
  JSON.stringify(
    files.value.map((f) => ({
      path: f.path,
      language: f.language,
      content: f.content,
      editable: f.editable,
    })),
    null,
    2,
  ),
)

watch(
  () => props.modelValue.length,
  (n) => {
    // 文件被外部清空时，也清掉展开状态
    if (n === 0) openSet.value = new Set()
  },
)
</script>

<style scoped>
.file-manager {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.fm-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.fm-tip {
  color: #909399;
  font-size: 12px;
  flex: 1;
  min-width: 200px;
}
.fm-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.fm-item {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 12px;
  background: #fafafa;
}
.fm-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.fm-path {
  flex: 2;
  min-width: 240px;
}
.fm-lang {
  width: 130px;
}
.fm-raw {
  margin-top: 8px;
}
</style>
