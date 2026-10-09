<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <div>
        <h1>实验总览</h1>
        <div class="muted">查看中间件实验状态、压测任务与性能分析</div>
      </div>
      <button class="btn primary" :disabled="running" @click="runExperiment">
        {{ running ? '运行中…' : '再次运行' }}
      </button>
    </div>

    <!-- Hero：当前首个模板作为 hero -->
    <div v-if="heroTemplate" class="card hero">
      <div class="hero-top">
        <div class="hero-title">
          <div class="scene-icon">🛒</div>
          <div>
            <h2 style="margin:3px 0 6px">{{ heroTemplate.name }}</h2>
            <div class="muted">{{ heroTemplate.description || '分布式中间件实验场景' }}</div>
            <div style="margin-top:10px">
              <span class="tag">{{ heroTemplate.middlewareType || 'redis' }}</span>
              <span class="tag">v{{ heroTemplate.latestVersionNo ?? 1 }}</span>
              <span class="tag" v-for="t in parseTags(heroTemplate.tags)" :key="t">{{ t }}</span>
            </div>
          </div>
        </div>
        <button class="btn soft" @click="$router.push(`/scenes/${heroTemplate.id}/edit`)">修改代码</button>
      </div>
      <div class="resource-row">
        <div class="resource"><strong>lab-order-service</strong><span>512MB</span></div>
        <div class="resource"><strong>Redis</strong><span>128MB</span></div>
        <div class="resource"><strong>MySQL</strong><span>768MB</span></div>
        <div class="resource"><strong>RabbitMQ</strong><span>512MB</span></div>
        <div class="resource"><strong>并发阶梯</strong><span>{{ concurrencyText }}</span></div>
        <div class="resource"><strong>最近任务</strong><span>{{ lastTaskText }}</span></div>
      </div>
      <div class="progress" style="margin-top:15px"><div :style="{ width: progress + '%' }"></div></div>
      <div class="muted" style="margin-top:8px">{{ statusText }}</div>
    </div>

    <div v-else-if="!loading" class="card panel">
      <h3>还没有实验模板</h3>
      <p class="muted">从模板开始创建你的第一个实验场景。</p>
      <button class="btn primary" @click="$router.push('/scenes/new')">+ 新建实验</button>
    </div>

    <div class="grid cols-2" style="margin-top:16px">
      <div class="card panel">
        <h3>实时任务</h3>
        <p class="muted">任务运行状态由 experiment-service 维护，详细阶段和进度可在性能监控中查看。</p>
        <button class="btn soft" @click="$router.push('/monitor')">打开性能监控</button>
      </div>
      <div class="card panel">
        <h3>AI 分析</h3>
        <p class="muted">选择已完成任务后，可运行 LangGraph 工作流诊断，或生成资源建议。</p>
        <button class="btn soft" @click="$router.push('/report')">打开 AI 分析</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  pageTemplates,
  pageTasks,
  getVersion,
  createTask,
  type TemplateResponse,
  type TaskResponse,
} from '@/api/experiment'

const router = useRouter()
const loading = ref(false)
const running = ref(false)
const heroTemplate = ref<TemplateResponse | null>(null)
const concurrencyText = ref('100→800')
const lastTaskText = ref('—')
const progress = ref(0)
const statusText = ref('等待运行')


function parseTags(tags?: string) {
  return tags ? tags.split(',').map((s) => s.trim()).filter(Boolean) : []
}

async function loadHero() {
  loading.value = true
  try {
    const [tasks, templates] = await Promise.all([pageTasks(1, 5), pageTemplates(1, 10)])
    const runningTask = tasks.find(
      (t: TaskResponse) => t.status === 'RUNNING' || t.status === 'QUEUED' || t.status === 'CREATED',
    )
    if (runningTask) {
      router.replace(`/tasks/${runningTask.id}`)
      return
    }
    heroTemplate.value = templates[0] ?? null
    if (heroTemplate.value?.latestVersionId) {
      const v = await getVersion(heroTemplate.value.latestVersionId).catch(() => null)
      if (v?.runParamsJson) {
        try {
          const p = JSON.parse(v.runParamsJson)
          if (Array.isArray(p.concurrencyLadder)) {
            concurrencyText.value = p.concurrencyLadder.join('→')
          }
        } catch {}
      }
    }
    const last = tasks[0]
    if (last) lastTaskText.value = `#${last.id} ${last.status}`
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function runExperiment() {
  if (!heroTemplate.value?.latestVersionId) {
    ElMessage.warning('还没有可运行版本，请先编辑提交一个版本')
    return
  }
  running.value = true
  try {
    const task = await createTask(heroTemplate.value.latestVersionId)
    router.push(`/tasks/${task.id}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '启动任务失败')
  }
}

onMounted(loadHero)
</script>
