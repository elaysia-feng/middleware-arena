<template>
  <div class="page">
    <el-page-header @back="onBack" />
    <div class="page-head">
      <div>
        <h1>{{ isCreate ? '代码实验室 — 新建模板' : `代码实验室 — 模板 #${templateId}` }}</h1>
        <div class="muted">编辑文件、设置运行参数，保存即发布新版本</div>
      </div>
      <el-space>
        <button class="btn" @click="handleSave" :disabled="submitting">
          保存版本
        </button>
        <button class="btn primary" @click="handleSubmit" :disabled="submitting">
          提交压测
        </button>
      </el-space>
    </div>

    <!-- 模板元数据（仅在创建模式显示） -->
    <el-card v-if="isCreate" class="create-form" style="margin-bottom:16px">
      <el-form :model="tplForm" :rules="tplRules" ref="tplFormRef" label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="实验名称" prop="name">
              <el-input v-model="tplForm.name" maxlength="50" show-word-limit placeholder="如：秒杀性能压测" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="中间件类型" prop="middlewareType">
              <el-select v-model="tplForm.middlewareType" placeholder="选择" style="width:100%">
                <el-option v-for="m in middlewareOptions" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="场景" prop="scenario">
              <el-input v-model="tplForm.scenario" maxlength="200" show-word-limit placeholder="如：分布式秒杀" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标签（逗号分隔）">
              <el-input v-model="tplForm.tags" placeholder="如：redis, 压测" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="描述">
              <el-input v-model="tplForm.description" type="textarea" :rows="2" maxlength="500" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- 文件树 + Monaco -->
    <div class="code-shell" :style="{ gridTemplateColumns: `${sidebarWidth}px 6px minmax(0, 1fr)` }">
      <aside class="file-tree">
        <h3>文件</h3>
        <div
          v-for="node in visibleFileTree"
          :key="node.key"
          class="tree-node"
          :class="{ active: node.fileIndex !== undefined && activeIdx === node.fileIndex }"
          :style="{ paddingLeft: `${8 + node.depth * 14}px` }"
          :title="node.path"
          @click="selectTreeNode(node)"
        >
          <span class="tree-caret">{{ node.kind === 'directory' ? (isDirectoryExpanded(node.path) ? '▾' : '▸') : '' }}</span>
          <span class="tree-icon">{{ node.kind === 'directory' ? '📁' : '📄' }}</span>
          <span class="tree-name">{{ node.name || '(未命名)' }}</span>
        </div>
        <div v-if="!files.length" class="muted" style="padding:8px;font-size:12px">
          还没有文件
        </div>
        <button class="btn soft" style="margin-top:12px;width:100%" @click="addFile">
          + 添加文件
        </button>
      </aside>
      <div class="resize-handle" title="拖动调整文件树宽度" @pointerdown="startResize" />
      <section class="editor">
        <div class="editor-top">
          <span>{{ activeFile?.path || '请选择文件' }}</span>
          <span style="color:#7b879d">{{ activeFile?.language || 'plaintext' }}</span>
        </div>
        <MonacoEditor
          v-if="activeFile"
          v-model="activeFile.content"
          :language="activeFile.language"
          height="500px"
        />
        <div v-else class="muted" style="padding:40px;text-align:center">
          添加或选择一个文件开始编辑
        </div>
      </section>
    </div>

    <!-- 运行参数 -->
    <div class="card panel" style="margin-top:16px">
      <h3>运行参数（k6 压测配置）</h3>
      <RunParamsForm v-model="runParams" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import MonacoEditor from '@/components/MonacoEditor.vue'
import RunParamsForm, { type RunParams } from '@/components/RunParamsForm.vue'
import {
  createTemplate,
  createVersion,
  getTemplate,
  getVersion,
  createTask,
  type TemplateFileRequest,
} from '@/api/experiment'

const route = useRoute()
const router = useRouter()

const routeTemplateId = computed(() => route.params.templateId as string | undefined)
const isCreate = computed(() => routeTemplateId.value === 'new' || !routeTemplateId.value)
const templateId = computed(() => (isCreate.value ? undefined : Number(routeTemplateId.value)))

const submitting = ref(false)

// 创建模式表单
const tplFormRef = ref<FormInstance>()
const middlewareOptions = ['REDIS', 'RABBITMQ', 'SEATA', 'ELASTICSEARCH', 'SENTINEL', 'MYSQL', 'OTHER']
const tplForm = reactive({
  name: '',
  middlewareType: 'REDIS',
  scenario: '',
  tags: '',
  description: '',
})
const tplRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  middlewareType: [{ required: true, message: '请选择', trigger: 'change' }],
  scenario: [{ required: true, message: '请描述场景', trigger: 'blur' }],
}

// 文件：path/language/content/editable 与后端 FilePatch 对齐
interface FileEntry {
  path: string
  language: string
  content: string
  editable: boolean
}

interface FileTreeNode {
  key: string
  name: string
  path: string
  kind: 'directory' | 'file'
  depth: number
  fileIndex?: number
  children: FileTreeNode[]
}

const files = ref<FileEntry[]>([])
const activeIdx = ref(0)
const runParams = ref<RunParams>({})
const sidebarWidth = ref(240)
const expandedDirectories = ref(new Set<string>())

const activeFile = computed(() => files.value[activeIdx.value])

const fileTree = computed<FileTreeNode[]>(() => {
  const root: FileTreeNode[] = []

  files.value.forEach((file, fileIndex) => {
    const normalizedPath = file.path.replace(/\\/g, '/').replace(/^\/+|\/+$/g, '')
    const parts = normalizedPath ? normalizedPath.split('/').filter(Boolean) : ['(未命名)']
    let current = root
    let parentPath = ''

    parts.forEach((part, partIndex) => {
      const nodePath = parentPath ? `${parentPath}/${part}` : part
      const isFile = partIndex === parts.length - 1
      let node = current.find((item) => item.name === part && item.kind === (isFile ? 'file' : 'directory'))
      if (!node) {
        node = {
          key: `${isFile ? 'file' : 'directory'}:${nodePath}`,
          name: part,
          path: nodePath,
          kind: isFile ? 'file' : 'directory',
          depth: partIndex,
          fileIndex: isFile ? fileIndex : undefined,
          children: [],
        }
        current.push(node)
      }
      current = node.children
      parentPath = nodePath
    })
  })

  const sortNodes = (nodes: FileTreeNode[]) => {
    nodes.sort((a, b) => {
      if (a.kind !== b.kind) return a.kind === 'directory' ? -1 : 1
      return a.name.localeCompare(b.name)
    })
    nodes.forEach((node) => sortNodes(node.children))
  }
  sortNodes(root)
  return root
})

const visibleFileTree = computed<FileTreeNode[]>(() => {
  const result: FileTreeNode[] = []
  const flatten = (nodes: FileTreeNode[], depth: number) => {
    nodes.forEach((node) => {
      node.depth = depth
      result.push(node)
      if (node.kind === 'directory' && expandedDirectories.value.has(node.path)) {
        flatten(node.children, depth + 1)
      }
    })
  }
  flatten(fileTree.value, 0)
  return result
})

function isDirectoryExpanded(path: string) {
  return expandedDirectories.value.has(path)
}

function selectTreeNode(node: FileTreeNode) {
  if (node.kind === 'directory') {
    const next = new Set(expandedDirectories.value)
    if (next.has(node.path)) next.delete(node.path)
    else next.add(node.path)
    expandedDirectories.value = next
    return
  }
  if (node.fileIndex !== undefined) activeIdx.value = node.fileIndex
}

function expandPathToFile(path: string) {
  const parts = path.replace(/\\/g, '/').split('/').filter(Boolean)
  const next = new Set(expandedDirectories.value)
  parts.slice(0, -1).forEach((_, index) => {
    next.add(parts.slice(0, index + 1).join('/'))
  })
  expandedDirectories.value = next
}

function startResize(event: PointerEvent) {
  const startX = event.clientX
  const startWidth = sidebarWidth.value
  const onMove = (moveEvent: PointerEvent) => {
    sidebarWidth.value = Math.min(480, Math.max(180, startWidth + moveEvent.clientX - startX))
  }
  const stopResize = () => {
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', stopResize)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  }
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', stopResize)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

function addFile() {
  files.value.push({ path: '', language: 'java', content: '', editable: true })
  activeIdx.value = files.value.length - 1
}

function onBack() {
  router.push('/scenes')
}

async function loadExistingTemplate() {
  if (isCreate.value || templateId.value === undefined) return
  try {
    const template = await getTemplate(templateId.value)
    if (!template.latestVersionId) {
      ElMessage.warning('该模板还没有可编辑的版本')
      return
    }

    const version = await getVersion(template.latestVersionId)
    files.value = parseFiles(version.filesJson)
    runParams.value = parseRunParams(version.runParamsJson)
    activeIdx.value = 0
    expandedDirectories.value = new Set()
    expandPathToFile(files.value[0]?.path || '')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载模板失败')
  }
}

function parseFiles(filesJson?: string): FileEntry[] {
  if (!filesJson) return []
  try {
    const parsed: unknown = JSON.parse(filesJson)
    if (!Array.isArray(parsed)) throw new Error('文件内容格式不正确')
    return parsed
      .filter((file): file is Record<string, unknown> => Boolean(file) && typeof file === 'object')
      .map((file) => ({
        path: typeof file.path === 'string' ? file.path : '',
        language: typeof file.language === 'string' ? file.language : 'plaintext',
        content: typeof file.content === 'string' ? file.content : '',
        editable: typeof file.editable === 'boolean' ? file.editable : true,
      }))
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '读取模板文件失败')
    return []
  }
}

function parseRunParams(runParamsJson?: string): RunParams {
  if (!runParamsJson) return {}
  try {
    const parsed: unknown = JSON.parse(runParamsJson)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed as RunParams : {}
  } catch {
    ElMessage.warning('运行参数格式不正确，已使用空配置')
    return {}
  }
}

function buildFiles(): TemplateFileRequest[] {
  return files.value.map((f) => ({
    path: f.path.trim(),
    language: f.language || 'plaintext',
    content: f.content || '',
    editable: f.editable,
  }))
}

function validateFiles(): string | null {
  if (!files.value.length) return '请至少添加一个文件'
  for (let i = 0; i < files.value.length; i++) {
    const f = files.value[i]
    if (!f.path.trim()) return `第 ${i + 1} 个文件缺少路径`
  }
  return null
}

async function ensureTemplate(): Promise<number> {
  if (!isCreate.value) {
    if (templateId.value === undefined) throw new Error('模板 ID 缺失')
    return templateId.value
  }
  const valid = await tplFormRef.value?.validate().catch(() => false)
  if (!valid) throw new Error('表单校验失败')
  const tplFormData = {
    name: tplForm.name.trim(),
    middlewareType: tplForm.middlewareType,
    scenario: tplForm.scenario.trim(),
    tags: tplForm.tags.trim() || undefined,
    description: tplForm.description.trim() || undefined,
    files: buildFiles(),
    runParams: runParams.value,
  }
  const created = await createTemplate(tplFormData)
  return created.id
}

async function pushVersion(tid: number, summary: string) {
  await createVersion({
    templateId: tid,
    filesJson: JSON.stringify(buildFiles()),
    runParamsJson: JSON.stringify(runParams.value || {}),
    changeSummary: summary,
  })
}

async function handleSave() {
  const err = validateFiles()
  if (err) return ElMessage.warning(err)
  submitting.value = true
  try {
    const tid = await ensureTemplate()
    if (isCreate.value) {
      // createTemplate 已经自动创建 V1，跳转到详情
      ElMessage.success('模板已创建（含 V1）')
      router.replace(`/scenes/${tid}`)
      return
    }
    await pushVersion(tid, '编辑保存')
    ElMessage.success('版本已保存')
    router.replace(`/scenes/${tid}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    submitting.value = false
  }
}

async function handleSubmit() {
  const err = validateFiles()
  if (err) return ElMessage.warning(err)
  submitting.value = true
  try {
    const tid = await ensureTemplate()
    let versionId = templateId.value !== undefined ? templateId.value : undefined
    if (isCreate.value) {
      // createTemplate 自动创 V1 — 走完拿
      const tpl = await getTemplate(tid)
      versionId = tpl.latestVersionId
    } else {
      const v = await pushVersion_inner(tid)
      versionId = v
    }
    if (!versionId) throw new Error('没有可运行版本')
    const task = await createTask(versionId)
    ElMessage.success('已创建任务')
    router.push(`/tasks/${task.id}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交失败')
  } finally {
    submitting.value = false
  }
}

// 单开一个 helper 不与 pushVersion 同名（async 顺序原因）
async function pushVersion_inner(tid: number): Promise<number> {
  const v = await createVersion({
    templateId: tid,
    filesJson: JSON.stringify(buildFiles()),
    runParamsJson: JSON.stringify(runParams.value || {}),
    changeSummary: '提交压测',
  })
  return v.id
}

onMounted(async () => {
  await loadExistingTemplate()
  if (!isCreate.value && files.value.length === 0) {
    files.value.push({
      path: 'src/main/java/App.java',
      language: 'java',
      content: '',
      editable: true,
    })
    activeIdx.value = 0
    expandPathToFile(files.value[0].path)
  }
})

onUnmounted(() => {
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
})
</script>

<style scoped>
.create-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  height: 34px;
  padding-right: 8px;
  color: #c7d2e8;
  font-size: 12px;
  line-height: 34px;
  cursor: pointer;
  user-select: none;
}

.tree-node:hover,
.tree-node.active {
  background: #263753;
  color: #ffffff;
}

.tree-caret {
  width: 10px;
  flex: 0 0 10px;
  color: #8da2c4;
  text-align: center;
}

.tree-icon {
  flex: 0 0 16px;
  font-size: 13px;
}

.tree-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
