<template>
  <div class="page" v-loading="loading">
    <el-page-header @back="$router.back()" />

    <div class="page-head">
      <div>
        <h1>任务 #{{ task?.id ?? '加载中…' }}</h1>
        <div class="muted">{{ statusLabel(task?.status) }} · {{ task?.currentStage || '—' }}</div>
      </div>
      <el-space>
        <button v-if="canCancel" class="btn" :disabled="cancelling" @click="handleCancel">
          取消任务
        </button>
        <button v-if="canRetry" class="btn primary" :disabled="retrying" @click="handleRetry">
          重跑一次
        </button>
        <button v-if="task?.status === 'SUCCESS'" class="btn primary" @click="router.push(`/report?taskId=${task.id}`)">
          AI 分析
        </button>
      </el-space>
    </div>

    <div v-if="task" class="card hero">
      <div class="resource-row">
        <div class="resource"><strong>状态</strong><span><span class="badge" :class="badgeClass(task.status)">{{ statusLabel(task.status) }}</span></span></div>
        <div class="resource"><strong>当前阶段</strong><span>{{ task.currentStage || '—' }}</span></div>
        <div class="resource"><strong>进度</strong><span>{{ Math.round(task.progress ?? 0) }}%</span></div>
        <div class="resource"><strong>用户 ID</strong><span>#{{ task.userId }}</span></div>
        <div class="resource"><strong>版本</strong><span>v{{ task.versionId }}</span></div>
        <div class="resource"><strong>tierSnapshot</strong><span>{{ task.tierSnapshot || '—' }}</span></div>
      </div>

      <div class="progress" style="margin-top:20px"><div :style="{ width: (task.progress ?? 0) + '%' }"></div></div>
      <div class="muted" style="margin-top:8px">实时进度：{{ progressText }}</div>
    </div>

    <div v-if="task?.errorMessage" class="card panel" style="margin-top:16px">
      <h3>错误信息</h3>
      <pre style="background:#fef0f0;padding:12px;border-radius:8px;color:#c00;white-space:pre-wrap;margin:8px 0 0">{{ task.errorMessage }}</pre>
      <p v-if="task.errorCode" class="muted" style="margin-top:8px">错误码：{{ task.errorCode }}</p>
    </div>

    <div v-if="task" class="resource-row" style="margin-top:16px">
      <div class="resource"><strong>创建时间</strong><span>{{ formatTime(task.createdAt) }}</span></div>
      <div class="resource"><strong>开始时间</strong><span>{{ formatTime(task.startedAt) }}</span></div>
      <div class="resource"><strong>结束时间</strong><span>{{ formatTime(task.finishedAt) }}</span></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getTask,
  getTaskProgress,
  cancelTask,
  retryTask,
  type TaskResponse,
  type TaskStatus,
} from '@/api/experiment'

const route = useRoute()
const router = useRouter()
const taskId = computed(() => Number(route.params.taskId))
const task = ref<TaskResponse | null>(null)
const progressText = ref('')
const loading = ref(false)
const retrying = ref(false)
const cancelling = ref(false)
let pollHandle: ReturnType<typeof setInterval> | null = null

function formatTime(t?: string) {
  if (!t) return '—'
  return new Date(t).toLocaleString('zh-CN')
}

function statusLabel(s?: TaskStatus): string {
  if (s === 'CREATED' || s === 'QUEUED') return '排队中'
  if (s === 'RUNNING') return '运行中'
  if (s === 'SUCCESS') return '已完成'
  if (s === 'FAILED') return '失败'
  if (s === 'CANCELLED') return '已取消'
  return s || '未知'
}
function badgeClass(s?: TaskStatus): string {
  if (s === 'CREATED' || s === 'QUEUED') return 'wait'
  if (s === 'RUNNING') return 'run'
  if (s === 'SUCCESS') return 'ok'
  if (s === 'FAILED') return 'fail'
  return 'wait'
}
function isTerminal(s?: TaskStatus): boolean {
  return s === 'SUCCESS' || s === 'FAILED' || s === 'CANCELLED'
}
const canCancel = computed(
  () => task.value && !isTerminal(task.value.status),
)
const canRetry = computed(
  () => task.value?.status === 'FAILED',
)

async function load() {
  loading.value = true
  try {
    task.value = await getTask(taskId.value)
    await loadProgress()
    maybeStartPolling()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadProgress() {
  try {
    progressText.value = await getTaskProgress(taskId.value)
  } catch {
    progressText.value = '（无法获取进度）'
  }
}

function maybeStartPolling() {
  if (pollHandle) clearInterval(pollHandle)
  if (isTerminal(task.value?.status)) return
  pollHandle = setInterval(async () => {
    if (!task.value || isTerminal(task.value.status)) {
      if (pollHandle) clearInterval(pollHandle)
      pollHandle = null
      return
    }
    try {
      const fresh = await getTask(taskId.value)
      task.value = fresh
      await loadProgress()
    } catch {
      // 静默
    }
  }, 3000)
}

async function handleCancel() {
  try {
    await ElMessageBox.confirm('确认取消该任务？', '提示', { type: 'warning' })
  } catch {
    return
  }
  cancelling.value = true
  try {
    await cancelTask(taskId.value)
    ElMessage.success('已取消')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '取消失败')
  } finally {
    cancelling.value = false
  }
}

async function handleRetry() {
  // 后端 retryTask 仅允许 status=FAILED；UI 已 gating，但仍做兜底
  retrying.value = true
  try {
    const fresh = await retryTask(taskId.value)
    ElMessage.success('已发起重跑')
    router.replace(`/tasks/${fresh.id}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重跑失败')
  } finally {
    retrying.value = false
  }
}

onBeforeUnmount(() => {
  if (pollHandle) clearInterval(pollHandle)
})

onMounted(load)
</script>
