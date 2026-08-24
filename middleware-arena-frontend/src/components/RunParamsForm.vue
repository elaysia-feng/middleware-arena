<template>
  <div class="rp-form">
    <el-form label-position="top">
      <div class="rp-intro">
        <strong>基础压测配置</strong>
        <span>下面的默认值可以直接提交；需要精细调参时再修改即可。</span>
      </div>

      <el-form-item label="压测接口">
        <div class="field-help">不选择时使用模板默认接口；库存和余额属于内部服务接口，必须确认对应实验拓扑已启动。</div>
        <el-select
          v-model="local.endpoint"
          clearable
          placeholder="跟随模板默认接口"
          style="width:100%"
          @change="handleEndpointChange"
        >
          <el-option
            v-for="endpoint in endpointOptions"
            :key="`${endpoint.method} ${endpoint.path}`"
            :label="`${endpoint.method} ${endpoint.path} — ${endpoint.label}`"
            :value="endpoint.path"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="并发用户数（逐级增加）">
        <div class="field-help">k6 会依次使用这些并发用户数，例如 20 → 50 → 100。</div>
        <div class="row">
          <el-input
            v-model="stageInput"
            placeholder="输入数字，用逗号分隔，如 20,50,100"
            @keyup.enter="addStages"
            class="grow"
          />
          <el-button @click="addStages">添加并发档位</el-button>
        </div>
        <div class="stage-list">
          <el-tag
            v-for="(c, i) in local.concurrencyLadder"
            :key="`${i}-${c}`"
            closable
            class="stage-tag"
            @close="removeStage(i)"
          >
            {{ c }} VU
          </el-tag>
          <span v-if="!local.concurrencyLadder?.length" class="muted">未设置，将使用默认值</span>
        </div>
      </el-form-item>

      <el-form-item label="每档压测时长">
        <div class="field-help">每个并发档位运行多久，支持秒（s）或分钟（m），最长 10 分钟。</div>
        <el-input v-model="local.duration" placeholder="例如 30s、2m">
          <template #prepend>时长</template>
        </el-input>
      </el-form-item>

      <el-form-item label="请求参数（JSON 请求体）">
        <div class="field-help">发送给接口的业务参数，必须是合法 JSON；订单接口默认使用商品 1、购买 1 件，登录用户由系统自动识别。</div>
        <el-input
          v-model="local.requestBodyText"
          type="textarea"
          :rows="4"
          placeholder='例如 {"productId":1,"quantity":1}'
        />
        <div v-if="requestBodyError" class="error-tip">{{ requestBodyError }}</div>
      </el-form-item>

      <el-form-item label="请求头（通常不需要修改）">
        <div class="field-help">用于补充接口协议，例如 JSON 请求通常需要 Content-Type。</div>
        <el-button size="small" @click="addHeader">+ 添加请求头</el-button>
        <div v-for="(h, i) in local.headersList" :key="i" class="header-row">
          <el-input v-model="h.key" placeholder="Content-Type" class="h-key" />
          <el-input v-model="h.value" placeholder="application/json" class="h-value" />
          <el-button size="small" type="danger" @click="removeHeader(i)">×</el-button>
        </div>
      </el-form-item>
    </el-form>

    <div class="rp-raw">
      <el-button text @click="rawOpen = !rawOpen">
        {{ rawOpen ? '隐藏' : '查看' }} runParamsJson 序列化预览
      </el-button>
      <MonacoEditor v-if="rawOpen" :model-value="rawJson" language="json" height="200px" read-only />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import MonacoEditor from '@/components/MonacoEditor.vue'

export interface RunParams {
  concurrencyLadder?: number[]
  duration?: string
  endpoint?: string
  httpMethod?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  requestBody?: unknown // K6RunnerImpl 期望对象/数组，不是字符串
  headers?: Record<string, string>
}

interface HeaderRow {
  key: string
  value: string
}

interface LocalState {
  concurrencyLadder?: number[]
  duration?: string
  endpoint?: string
  httpMethod?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  // requestBody 用 textarea 收原始文本，序列化时再 JSON.parse
  requestBodyText: string
  headersList: HeaderRow[]
}

const endpointOptions = [
  { method: 'GET' as const, path: '/product/1', label: '商品查询 / 缓存读取' },
  { method: 'GET' as const, path: '/order/1', label: '订单详情 / 缓存读取' },
  { method: 'POST' as const, path: '/order/create', label: '创建订单（库存 + 余额）' },
  { method: 'POST' as const, path: '/storage/deduct', label: '扣减库存（内部接口）' },
  { method: 'POST' as const, path: '/account/deduct', label: '扣减余额（内部接口）' },
]

const props = defineProps<{ modelValue: RunParams }>()
const emit = defineEmits<{ 'update:modelValue': [params: RunParams] }>()

const defaultRunParams: Required<Pick<RunParams, 'concurrencyLadder' | 'duration' | 'requestBody' | 'headers'>> = {
  concurrencyLadder: [20, 50, 100],
  duration: '30s',
  requestBody: { productId: 1, quantity: 1 },
  headers: { 'Content-Type': 'application/json' },
}

const local = reactive<LocalState>({
  ...props.modelValue,
  endpoint: props.modelValue.endpoint || undefined,
  httpMethod: props.modelValue.httpMethod,
  concurrencyLadder: props.modelValue.concurrencyLadder?.length
    ? [...props.modelValue.concurrencyLadder]
    : [...defaultRunParams.concurrencyLadder],
  duration: props.modelValue.duration || defaultRunParams.duration,
  requestBodyText: props.modelValue.requestBody !== undefined
    ? JSON.stringify(props.modelValue.requestBody, null, 2)
    : JSON.stringify(defaultRunParams.requestBody, null, 2),
  headersList: props.modelValue.headers
    ? Object.entries(props.modelValue.headers).map(([k, v]) => ({ key: k, value: v }))
    : Object.entries(defaultRunParams.headers).map(([key, value]) => ({ key, value })),
})

const rawOpen = ref(false)
const stageInput = ref('')
let loadingFromParent = false

function loadModelValue(params: RunParams) {
  loadingFromParent = true
  local.endpoint = params.endpoint || undefined
  local.httpMethod = params.httpMethod
  local.concurrencyLadder = params.concurrencyLadder?.length
    ? [...params.concurrencyLadder]
    : [...defaultRunParams.concurrencyLadder]
  local.duration = params.duration || defaultRunParams.duration
  local.requestBodyText = params.requestBody !== undefined
    ? JSON.stringify(params.requestBody, null, 2)
    : JSON.stringify(defaultRunParams.requestBody, null, 2)
  local.headersList = params.headers
    ? Object.entries(params.headers).map(([key, value]) => ({ key, value }))
    : Object.entries(defaultRunParams.headers).map(([key, value]) => ({ key, value }))
  nextTick(() => {
    loadingFromParent = false
  })
}

const requestBodyError = computed(() => {
  const t = local.requestBodyText.trim()
  if (!t) return ''
  try {
    JSON.parse(t)
    return ''
  } catch (err) {
    return `JSON 不合法：${err instanceof Error ? err.message : String(err)}`
  }
})

function buildRequestBody(): unknown | undefined {
  const t = local.requestBodyText.trim()
  if (!t) return undefined
  return JSON.parse(t)
}

function sync() {
  const out: RunParams = {}
  if (local.endpoint) {
    out.endpoint = local.endpoint
    out.httpMethod = local.httpMethod || endpointOptions.find((item) => item.path === local.endpoint)?.method
  }
  if (local.concurrencyLadder && local.concurrencyLadder.length) {
    out.concurrencyLadder = local.concurrencyLadder.filter((n) => Number.isFinite(n) && n > 0)
  }
  if (local.duration?.trim()) out.duration = local.duration.trim()
  try {
    const body = buildRequestBody()
    if (body !== undefined) out.requestBody = body
  } catch {
    // 序列化期允许错误（显示在 UI），不 emit 到父组件
  }
  const hdr: Record<string, string> = {}
  for (const h of local.headersList) {
    const k = h.key.trim()
    if (k) hdr[k] = h.value
  }
  if (Object.keys(hdr).length) out.headers = hdr
  emit('update:modelValue', out)
}

function handleEndpointChange(endpoint?: string) {
  local.httpMethod = endpointOptions.find((item) => item.path === endpoint)?.method
}

watch(
  local,
  () => {
    if (loadingFromParent) return
    sync()
  },
  { deep: true },
)

watch(
  () => props.modelValue,
  (params) => loadModelValue(params),
)

watch(
  () => local.endpoint,
  (endpoint) => {
    local.httpMethod = endpointOptions.find((item) => item.path === endpoint)?.method
  },
)

sync()

function addStages() {
  const parts = stageInput.value.split(/[,，\s]+/).filter(Boolean)
  if (!parts.length) return
  if (!local.concurrencyLadder) local.concurrencyLadder = []
  for (const p of parts) {
    const n = Number(p)
    if (Number.isFinite(n) && n > 0) local.concurrencyLadder.push(n)
  }
  stageInput.value = ''
}

function removeStage(i: number) {
  local.concurrencyLadder?.splice(i, 1)
}

function addHeader() {
  local.headersList.push({ key: '', value: '' })
}

function removeHeader(i: number) {
  local.headersList.splice(i, 1)
}

const rawJson = computed(() => {
  try {
    const out: RunParams = {}
    if (local.endpoint) {
      out.endpoint = local.endpoint
      out.httpMethod = local.httpMethod || endpointOptions.find((item) => item.path === local.endpoint)?.method
    }
    if (local.concurrencyLadder?.length) out.concurrencyLadder = local.concurrencyLadder
    if (local.duration?.trim()) out.duration = local.duration.trim()
    const body = buildRequestBody()
    if (body !== undefined) out.requestBody = body
    const hdr: Record<string, string> = {}
    for (const h of local.headersList) {
      const k = h.key.trim()
      if (k) hdr[k] = h.value
    }
    if (Object.keys(hdr).length) out.headers = hdr
    return JSON.stringify(out, null, 2)
  } catch (err) {
    return `// 序列化失败：${err instanceof Error ? err.message : String(err)}`
  }
})
</script>

<style scoped>
.rp-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.rp-intro {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-left: 3px solid #5b8cff;
  background: #f4f7ff;
  color: #53617a;
  font-size: 13px;
}
.rp-intro strong {
  color: #253858;
  font-size: 14px;
}
.field-help {
  margin: -4px 0 8px;
  color: #8490a5;
  font-size: 12px;
  line-height: 1.5;
}
.row {
  display: flex;
  gap: 8px;
}
.grow {
  flex: 1;
}
.stage-list {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 8px;
}
.stage-tag {
  margin-right: 4px;
}
.muted {
  color: #909399;
  font-size: 12px;
}
.header-row {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
}
.h-key {
  width: 180px;
}
.h-value {
  flex: 1;
}
.rp-raw {
  margin-top: 8px;
}
.error-tip {
  color: #f56c6c;
  font-size: 12px;
  margin-top: 4px;
}
</style>
