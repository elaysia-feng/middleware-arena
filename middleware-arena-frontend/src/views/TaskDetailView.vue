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

    <div v-if="task" class="card panel task-log-panel">
      <div class="task-log-head">
        <div>
          <h3>执行日志</h3>
          <div class="muted">当前任务的 Runner 关键步骤：构建、启动、健康检查、压测、采集和清理。</div>
        </div>
        <span class="muted">{{ logs.length }} 条</span>
      </div>
      <div v-if="logs.length === 0" class="muted task-log-empty">等待 Runner 回传日志…</div>
      <div v-else class="task-log-list">
        <div v-for="(entry, index) in logs" :key="`${entry.occurredAtEpochMs}-${index}`" class="task-log-entry">
          <span class="task-log-time">{{ formatLogTime(entry.occurredAtEpochMs) }}</span>
          <span class="task-log-stage">{{ entry.stage || 'SYSTEM' }}</span>
          <span class="task-log-level" :class="entry.level === 'ERROR' ? 'error' : ''">{{ entry.level }}</span>
          <code>{{ entry.message }}</code>
        </div>
      </div>
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
  getTaskLogs,
  cancelTask,
  retryTask,
  type TaskResponse,
  type TaskLogResponse,
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
const logs = ref<TaskLogResponse[]>([])
let pollHandle: ReturnType<typeof setInterval> | null = null
let logPollHandle: ReturnType<typeof setInterval> | null = null

function formatTime(t?: string) {
  if (!t) return '—'
  return new Date(t).toLocaleString('zh-CN')
}

function formatLogTime(t?: number) {
  if (!t) return '—'
  return new Date(t).toLocaleTimeString('zh-CN', { hour12: false })
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
    await loadLogs()
    maybeStartPolling()
    maybeStartLogPolling()
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

async function loadLogs() {
  try {
    logs.value = await getTaskLogs(taskId.value)
  } catch {
    // 日志只用于观察，不影响任务状态页的正常展示。
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
      await loadLogs()
    } catch {
      // 静默
    }
  }, 3000)
}

function maybeStartLogPolling() {
  if (logPollHandle) clearInterval(logPollHandle)
  if (isTerminal(task.value?.status)) return
  logPollHandle = setInterval(async () => {
    await loadLogs()
    if (isTerminal(task.value?.status)) {
      if (logPollHandle) clearInterval(logPollHandle)
      logPollHandle = null
    }
  }, 1000)
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
    // retryTask 复用同一个 taskId；不能只 replace 相同路由，否则旧的错误信息会留在页面上。
    task.value = fresh
    logs.value = []
    progressText.value = '任务已重新入队，等待 Runner 接收…'
    maybeStartPolling()
    maybeStartLogPolling()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重跑失败')
  } finally {
    retrying.value = false
  }
}

onBeforeUnmount(() => {
  if (pollHandle) clearInterval(pollHandle)
  if (logPollHandle) clearInterval(logPollHandle)
})

onMounted(load)
</script>

<style scoped>
.task-log-panel {
  margin-top: 16px;
}

.task-log-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.task-log-head h3 {
  margin: 0 0 6px;
}

.task-log-list {
  max-height: 420px;
  overflow: auto;
  padding: 10px;
  border-radius: 10px;
  background: #0f172a;
  color: #dbeafe;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
}

.task-log-entry {
  display: grid;
  grid-template-columns: 90px 150px 55px minmax(0, 1fr);
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
  font-size: 12px;
  line-height: 1.5;
}

.task-log-entry:last-child {
  border-bottom: 0;
}

.task-log-time,
.task-log-stage,
.task-log-level {
  color: #93c5fd;
}

.task-log-level.error {
  color: #fca5a5;
}

.task-log-entry code {
  color: #f8fafc;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.task-log-empty {
  padding: 16px;
  border-radius: 10px;
  background: #f8fafc;
}

@media (max-width: 760px) {
  .task-log-entry {
    grid-template-columns: 1fr;
    gap: 2px;
  }
}
</style>
