<template>
  <div class="page" v-loading="loading">
    <el-page-header @back="$router.push('/scenes')" />
    <div class="page-head">
      <div>
        <h1>{{ template?.name || '加载中…' }}</h1>
        <div class="muted">{{ template?.description || ' ' }}</div>
      </div>
      <el-space>
        <button class="btn soft" @click="$router.push(`/scenes/${templateId}/edit`)">编辑代码</button>
        <button class="btn primary" :disabled="!template?.latestVersionId" @click="runLatest">运行</button>
      </el-space>
    </div>

    <div v-if="template" class="card hero" style="margin-bottom:16px">
      <div class="resource-row">
        <div class="resource"><strong>中间件</strong><span>{{ template.middlewareType || '—' }}</span></div>
        <div class="resource"><strong>最近版本</strong><span>v{{ template.latestVersionNo ?? '—' }}</span></div>
        <div class="resource"><strong>作者</strong><span>用户 #{{ template.userId }}</span></div>
        <div class="resource"><strong>创建时间</strong><span>{{ formatTime(template.createdAt) }}</span></div>
        <div class="resource" style="grid-column: span 2">
          <strong>描述</strong>
          <span>{{ template.description || '—' }}</span>
        </div>
      </div>
    </div>

    <el-tabs v-if="template" v-model="activeTab">
      <el-tab-pane label="版本历史" name="versions">
        <el-empty v-if="!versionsLoading && versions.length === 0" description="还没有版本" />
        <table v-else class="table">
          <thead>
            <tr>
              <th>版本</th>
              <th>变更说明</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="v in versions" :key="v.id">
              <td>v{{ v.versionNo }}</td>
              <td>{{ v.changeSummary || '—' }}</td>
              <td>{{ formatTime(v.createdAt) }}</td>
              <td>
                <button class="btn" @click="runTask(v.id)">运行</button>
                <button class="btn soft" @click="diffWithPrev(v.versionNo)">对比</button>
              </td>
            </tr>
          </tbody>
        </table>
      </el-tab-pane>

      <el-tab-pane label="运行参数" name="params">
        <div v-if="latestRunParams" class="card panel">
          <pre style="background:#f5f7fa;padding:12px;border-radius:8px;margin:0">{{ latestRunParams }}</pre>
        </div>
        <el-empty v-else description="最近版本没有运行参数" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="diffOpen" title="版本对比" width="900">
      <div v-loading="diffLoading">
        <el-form inline>
          <el-form-item label="从版本">
            <el-input v-model="diffFromId" type="number" />
          </el-form-item>
          <el-form-item label="到版本">
            <el-input v-model="diffToId" type="number" />
          </el-form-item>
          <el-form-item>
            <button class="btn primary" @click="loadDiff">对比</button>
          </el-form-item>
        </el-form>
        <pre v-if="diffData" style="background:#f5f7fa;padding:12px;border-radius:8px;white-space:pre-wrap;max-height:55vh;overflow:auto;font-family:Consolas,monospace;font-size:12px">{{ JSON.stringify(diffData, null, 2) }}</pre>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getTemplate,
  listVersions,
  createTask,
  diffVersions,
  getVersion,
  type TemplateResponse,
  type VersionResponse,
  type VersionDiffResponse,
} from '@/api/experiment'

const route = useRoute()
const router = useRouter()

const templateId = computed(() => Number(route.params.templateId))
const template = ref<TemplateResponse | null>(null)
const versions = ref<VersionResponse[]>([])
const loading = ref(false)
const versionsLoading = ref(false)
const activeTab = ref('versions')

const diffOpen = ref(false)
const diffFromId = ref<number>()
const diffToId = ref<number>()
const diffData = ref<VersionDiffResponse | null>(null)
const diffLoading = ref(false)

const latestRunParams = ref<string>('')

function formatTime(t?: string) {
  if (!t) return '—'
  return new Date(t).toLocaleString('zh-CN')
}

async function loadTemplate() {
  loading.value = true
  try {
    template.value = await getTemplate(templateId.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadVersions() {
  versionsLoading.value = true
  try {
    versions.value = await listVersions(templateId.value)
    // 加载最近版本 runParamsJson 预览
    const latest = versions.value[versions.value.length - 1]
    if (latest) {
      try {
        const v = await getVersion(latest.id)
        if (v.runParamsJson) {
          const p = JSON.parse(v.runParamsJson)
          latestRunParams.value = JSON.stringify(p, null, 2)
        }
      } catch {}
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载版本失败')
  } finally {
    versionsLoading.value = false
  }
}

async function runLatest() {
  if (!template.value?.latestVersionId) {
    ElMessage.warning('没有可运行版本')
    return
  }
  await runTask(template.value.latestVersionId)
}

async function runTask(versionId: number) {
  try {
    const task = await createTask(versionId)
    ElMessage.success('已创建任务')
    router.push(`/tasks/${task.id}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建任务失败')
  }
}

async function diffWithPrev(_versionNo: number) {
  diffOpen.value = true
  diffData.value = null
  diffFromId.value = undefined
  diffToId.value = undefined
}

async function loadDiff() {
  if (!diffFromId.value || !diffToId.value) {
    ElMessage.warning('请输入两个版本 ID')
    return
  }
  diffLoading.value = true
  try {
    diffData.value = await diffVersions(diffFromId.value, diffToId.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '对比失败')
  } finally {
    diffLoading.value = false
  }
}

onMounted(async () => {
  await loadTemplate()
  await loadVersions()
})
</script>
