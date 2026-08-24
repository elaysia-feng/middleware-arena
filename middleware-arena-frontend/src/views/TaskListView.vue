<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <div>
        <h1>压测任务</h1>
        <div class="muted">任务通过 RabbitMQ 排队执行，避免本地资源被打满</div>
      </div>
      <button class="btn primary" @click="$router.push('/scenes')">启动当前实验</button>
    </div>

    <div class="grid cols-4">
      <div class="card metric"><small>运行中</small><strong class="blue">{{ stats.running }}</strong></div>
      <div class="card metric"><small>排队中</small><strong class="orange">{{ stats.queued }}</strong></div>
      <div class="card metric"><small>今日完成</small><strong class="green">{{ stats.done }}</strong></div>
      <div class="card metric"><small>失败任务</small><strong class="red">{{ stats.failed }}</strong></div>
    </div>

    <div class="card panel" style="margin-top:16px">
      <h3>最近任务</h3>
      <el-empty v-if="!loading && tasks.length === 0" description="还没有任务" />
      <table v-else class="table">
        <thead>
          <tr>
            <th>任务</th>
            <th>版本</th>
            <th>用户</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in tasks" :key="t.id">
            <td>#{{ t.id }}</td>
            <td>v{{ t.versionId }}</td>
            <td>#{{ t.userId }}</td>
            <td>
              <span class="badge" :class="badgeClass(t.status)">{{ statusLabel(t.status) }}</span>
            </td>
            <td>{{ formatTime(t.createdAt) }}</td>
            <td>
              <button class="btn" @click="$router.push(`/tasks/${t.id}`)">详情</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pageTasks, type TaskResponse, type TaskStatus } from '@/api/experiment'

const tasks = ref<TaskResponse[]>([])
const loading = ref(false)

const stats = computed(() => {
  const todayStr = new Date().toISOString().slice(0, 10)
  return {
    running: tasks.value.filter((t) => t.status === 'RUNNING').length,
    queued: tasks.value.filter((t) => t.status === 'QUEUED' || t.status === 'CREATED').length,
    done: tasks.value.filter(
      (t) => t.status === 'SUCCESS' && (t.finishedAt || '').slice(0, 10) === todayStr,
    ).length,
    failed: tasks.value.filter((t) => t.status === 'FAILED').length,
  }
})

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
  if (s === 'CANCELLED') return 'wait'
  return 'wait'
}

async function load() {
  loading.value = true
  try {
    tasks.value = await pageTasks(1, 50)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
