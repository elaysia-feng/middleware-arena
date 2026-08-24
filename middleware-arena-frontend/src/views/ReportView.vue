<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>AI 分析</h1>
        <div class="muted">资源建议与 LangGraph 性能诊断均通过 Agent 服务实时执行</div>
      </div>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="完整性能诊断" name="analysis">
        <div class="card panel">
          <el-form inline @submit.prevent="runAnalysis">
            <el-form-item label="任务 ID">
              <el-input-number v-model="taskId" :min="1" :precision="0" />
            </el-form-item>
            <el-form-item label="基线任务 ID（可选）">
              <el-input-number v-model="baselineTaskId" :min="1" :precision="0" />
            </el-form-item>
            <el-form-item>
              <button class="btn primary" :disabled="analyzing" @click="runAnalysis">
                {{ analyzing ? '分析中…' : '运行工作流分析' }}
              </button>
            </el-form-item>
          </el-form>
          <p class="muted">工作流会读取该任务的可信上下文，完成指标、日志、代码与相似实验分析。</p>
        </div>

        <div v-if="analysis" class="card panel" style="margin-top:16px">
          <div class="result-head"><h2>诊断结果</h2><span class="tag">{{ analysis.status }}</span></div>
          <p v-if="analysis.result.report" class="report-text">{{ analysis.result.report }}</p>
          <pre v-else class="json-result">{{ JSON.stringify(analysis.result, null, 2) }}</pre>
        </div>
      </el-tab-pane>

      <el-tab-pane label="资源建议" name="resource">
        <div class="card panel">
          <el-form label-position="top" @submit.prevent="runResourceAdvice">
            <el-form-item label="实验类型">
              <el-input v-model="resourceForm.experimentType" placeholder="如 REDIS、RABBITMQ" />
            </el-form-item>
            <div class="grid cols-2">
              <el-form-item label="最低 CPU 核数"><el-input-number v-model="resourceForm.ruleCpu" :min="0.1" :step="0.1" /></el-form-item>
              <el-form-item label="最低内存 MB"><el-input-number v-model="resourceForm.ruleMemory" :min="1" /></el-form-item>
              <el-form-item label="最大 CPU 核数"><el-input-number v-model="resourceForm.maxCpu" :min="0.1" :step="0.1" /></el-form-item>
              <el-form-item label="最大内存 MB"><el-input-number v-model="resourceForm.maxMemory" :min="1" /></el-form-item>
            </div>
            <button class="btn primary" :disabled="advising" @click="runResourceAdvice">
              {{ advising ? '计算中…' : '获取资源建议' }}
            </button>
          </el-form>
        </div>

        <div v-if="resourceAdvice" class="card panel" style="margin-top:16px">
          <h2>推荐资源：{{ resourceAdvice.final_budget.cpus }} 核 / {{ resourceAdvice.final_budget.memory_mb }} MB</h2>
          <p class="muted">实验类型：{{ resourceAdvice.experiment_type }}；{{ resourceAdvice.llm_used ? '已结合 LLM 建议' : '已使用规则兜底' }}</p>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { analyzeTask, getResourceAdvice, type AnalyzeResponse, type ResourceAdviceResponse } from '@/api/agent'

const route = useRoute()
const activeTab = ref('analysis')
const taskId = ref(Number(route.query.taskId) || undefined)
const baselineTaskId = ref<number | undefined>()
const analyzing = ref(false)
const advising = ref(false)
const analysis = ref<AnalyzeResponse>()
const resourceAdvice = ref<ResourceAdviceResponse>()
const resourceForm = reactive({ experimentType: 'REDIS', ruleCpu: 0.5, ruleMemory: 512, maxCpu: 2, maxMemory: 2048 })

async function runAnalysis() {
  if (!taskId.value) return ElMessage.warning('请输入要分析的任务 ID')
  analyzing.value = true
  try {
    analysis.value = await analyzeTask(taskId.value, baselineTaskId.value)
    ElMessage.success('分析完成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '分析失败')
  } finally {
    analyzing.value = false
  }
}

async function runResourceAdvice() {
  if (!resourceForm.experimentType.trim()) return ElMessage.warning('请输入实验类型')
  advising.value = true
  try {
    resourceAdvice.value = await getResourceAdvice({
      experiment_type: resourceForm.experimentType.trim(),
      run_params: {},
      rule_budget: { cpus: resourceForm.ruleCpu, memory_mb: resourceForm.ruleMemory },
      max_budget: { cpus: resourceForm.maxCpu, memory_mb: resourceForm.maxMemory },
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资源建议获取失败')
  } finally {
    advising.value = false
  }
}
</script>

<style scoped>
.result-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.report-text { white-space: pre-wrap; line-height: 1.7; }
.json-result { margin: 0; padding: 14px; overflow: auto; border-radius: 8px; background: #f6f8fb; white-space: pre-wrap; }
</style>
