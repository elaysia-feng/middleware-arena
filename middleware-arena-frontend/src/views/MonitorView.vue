<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <div>
        <h1>性能监控</h1>
        <div class="muted">实时查看运行中任务的进度、QPS、JVM 堆与组件资源占用</div>
      </div>
      <el-select
        v-model="selectedTaskId"
        placeholder="选择任务"
        style="width: 260px"
        @change="onTaskChange"
      >
        <el-option
          v-for="t in recentTasks"
          :key="t.id"
          :label="`任务 #${t.id} · ${statusLabel(t.status)} · v${t.versionId}`"
          :value="t.id"
        />
      </el-select>
    </div>

    <div class="grid cols-4" style="margin-top:8px">
      <div class="card metric"><small>当前状态</small><strong class="blue">{{ statusLabel(selectedTask?.status) }}</strong></div>
      <div class="card metric"><small>执行进度</small><strong class="green">{{ Math.round(selectedTask?.progress ?? 0) }}%</strong></div>
      <div class="card metric"><small>当前阶段</small><strong class="orange">{{ selectedTask?.currentStage || '—' }}</strong></div>
      <div class="card metric"><small>最近任务</small><strong class="purple">{{ recentTasks.length }}</strong></div>
    </div>

    <div v-if="selectedTask" class="card panel" style="margin-top:16px">
      <h3>当前任务进度</h3>
      <p>
        任务 <strong>#{{ selectedTask.id }}</strong> ·
        状态
        <span class="badge" :class="badgeClass(selectedTask.status)">
          {{ statusLabel(selectedTask.status) }}
        </span>
        · 阶段 <strong>{{ selectedTask.currentStage || '—' }}</strong>
      </p>
      <div class="progress">
        <div :style="{ width: (selectedTask.progress ?? 0) + '%' }"></div>
      </div>
      <div class="muted" style="margin-top:8px">{{ progressText }}</div>
    </div>

    <div class="grid cols-2" style="margin-top:16px">
      <div class="card panel">
        <h3>任务状态</h3>
        <p class="muted">当前后端只对外提供任务状态、阶段和进度；QPS、P95、JVM 等时序指标尚无查询接口，因此页面不会展示虚构曲线。</p>
      </div>
      <div class="card panel">
        <h3>下一步</h3>
        <p class="muted">任务成功后可直接启动 AI 工作流分析，结合任务代码、日志和指标上下文给出诊断。</p>
        <button v-if="selectedTask?.status === 'SUCCESS'" class="btn soft" @click="$router.push(`/report?taskId=${selectedTask.id}`)">分析当前任务</button>
      </div>
    </div>

    <div v-if="!loading && recentTasks.length === 0" class="muted" style="margin-top:16px; text-align: center">
      还没有任务，去场景页选一个跑起来再来看。
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getTask,
  pageTasks,
  getTaskProgress,
  type TaskResponse,
  type TaskStatus,
} from '@/api/experiment'

const recentTasks = ref<TaskResponse[]>([])
const selectedTaskId = ref<number | null>(null)
const selectedTask = ref<TaskResponse | null>(null)
const progressText = ref('')
const loading = ref(false)
let pollHandle: ReturnType<typeof setInterval> | null = null

function statusLabel(s?: TaskStatus) {
  if (s === 'CREATED' || s === 'QUEUED') return '排队中'
  if (s === 'RUNNING') return '运行中'
  if (s === 'SUCCESS') return '已完成'
  if (s === 'FAILED') return '失败'
  return s || '未知'
}
function badgeClass(s?: TaskStatus) {
  if (s === 'CREATED' || s === 'QUEUED') return 'wait'
  if (s === 'RUNNING') return 'run'
  if (s === 'SUCCESS') return 'ok'
  if (s === 'FAILED') return 'fail'
  return 'wait'
}

async function loadRecent() {
  loading.value = true
  try {
    recentTasks.value = await pageTasks(1, 50)
    if (!selectedTaskId.value && recentTasks.value.length) {
      selectedTaskId.value = recentTasks.value[0].id
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadSelected() {
  if (!selectedTaskId.value) {
    selectedTask.value = null
    return
  }
  try {
    selectedTask.value = await getTask(selectedTaskId.value)
    await loadProgress()
    maybeStartPolling()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载失败')
  }
}

async function loadProgress() {
  if (!selectedTaskId.value) return
  try {
    progressText.value = await getTaskProgress(selectedTaskId.value)
  } catch {
    progressText.value = '（无法获取进度）'
  }
}

function isTerminal(s?: TaskStatus) {
  return s === 'SUCCESS' || s === 'FAILED' || s === 'CANCELLED'
}

function maybeStartPolling() {
  if (pollHandle) clearInterval(pollHandle)
  if (isTerminal(selectedTask.value?.status)) return
  pollHandle = setInterval(async () => {
    if (!selectedTask.value || isTerminal(selectedTask.value.status)) {
      if (pollHandle) clearInterval(pollHandle)
      pollHandle = null
      return
    }
    try {
      if (selectedTaskId.value !== null) {
        const fresh = await getTask(selectedTaskId.value)
        selectedTask.value = fresh
        await loadProgress()
      }
    } catch {
      // 静默轮询错误
    }
  }, 3000)
}

function onTaskChange() {
  if (pollHandle) clearInterval(pollHandle)
  pollHandle = null
  loadSelected()
}

onBeforeUnmount(() => {
  if (pollHandle) clearInterval(pollHandle)
})

onMounted(async () => {
  await loadRecent()
  await loadSelected()
})
</script>
