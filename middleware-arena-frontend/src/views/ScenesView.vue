<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <div>
        <h1>实验场景</h1>
        <div class="muted">从模板开始，修改代码、配置和资源限制</div>
      </div>
      <button class="btn primary" @click="$router.push('/scenes/new')">创建自定义场景</button>
    </div>

    <!-- 内置场景：首次进入时由后端复制白名单源码，之后编辑自己的模板版本。 -->
    <div class="grid cols-3">
      <div v-for="s in presetScenes" :key="s.key" class="card scene-card">
        <div class="scene-icon">{{ s.icon }}</div>
        <h3>{{ s.name }}</h3>
        <p>{{ s.desc }}</p>
        <span v-for="t in s.tags" :key="t" class="tag">{{ t }}</span>
        <p>
          <button
            class="btn primary"
            :disabled="creatingKey === s.builtinKey"
            @click="enterScene(s.builtinKey)"
          >
            {{ creatingKey === s.builtinKey ? '正在创建…' : '进入实验' }}
          </button>
        </p>
      </div>
    </div>

    <h2 style="margin-top:32px;font-size:20px">我的实验模板</h2>
    <el-empty v-if="!loading && templates.length === 0" description="还没有实验模板" />
    <el-row :gutter="16" style="margin-top:12px">
      <el-col v-for="t in templates" :key="t.id" :xs="24" :sm="12" :md="8" :lg="6">
        <el-card class="tpl-card" shadow="hover" @click="$router.push(`/scenes/${t.id}`)">
          <h3>{{ t.name }}</h3>
          <p class="muted">{{ t.description || '暂无描述' }}</p>
          <el-tag v-if="t.middlewareType" size="small" style="margin-right:4px">
            {{ t.middlewareType }}
          </el-tag>
          <span class="muted">v{{ t.latestVersionNo ?? 1 }}</span>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createBuiltinTemplate, pageTemplates, type TemplateResponse } from '@/api/experiment'

const router = useRouter()
const templates = ref<TemplateResponse[]>([])
const loading = ref(false)
const creatingKey = ref('')

const presetScenes = [
  {
    key: 'redis',
    builtinKey: 'redis',
    name: 'Redis 缓存优化',
    icon: '⚡',
    desc: '对比无缓存、普通缓存、互斥锁和逻辑过期策略。',
    tags: ['缓存命中率', '热点 Key'],
  },
  {
    key: 'rabbitmq',
    builtinKey: 'rabbitmq',
    name: 'RabbitMQ 异步下单',
    icon: '🐇',
    desc: '对比同步下单与异步削峰，观察积压与消费速度。',
    tags: ['消息积压', '消费并发'],
  },
  {
    key: 'seata',
    builtinKey: 'seata',
    name: 'Seata 分布式事务',
    icon: '🔗',
    desc: '观察 AT 模式下的一致性、锁冲突和事务开销。',
    tags: ['AT 模式', '全局事务'],
  },
  {
    key: 'elasticsearch',
    builtinKey: 'elasticsearch',
    name: 'Elasticsearch 搜索',
    icon: '🔎',
    desc: '对比 MySQL LIKE 和 ES 全文搜索性能。',
    tags: ['全文检索', '索引同步'],
  },
  {
    key: 'community-interaction',
    builtinKey: 'community-interaction',
    name: '社区互动可靠持久化',
    icon: '◉',
    desc: '观察 Redis Lua、Stream Outbox、RabbitMQ 与 MySQL 幂等落库。',
    tags: ['Lua', 'Stream Outbox', '最终一致'],
  },
]

async function load() {
  loading.value = true
  try {
    templates.value = await pageTemplates(1, 50)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function enterScene(builtinKey: string) {
  const template = templates.value.find((item) => {
    if (builtinKey === 'community-interaction') return item.middlewareType === 'REDIS_RABBITMQ'
    return item.middlewareType?.toLowerCase() === builtinKey
  })
  if (template) {
    router.push(`/scenes/${template.id}/edit`)
    return
  }
  creatingKey.value = builtinKey
  try {
    const template = await createBuiltinTemplate(builtinKey)
    router.push(`/scenes/${template.id}/edit`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建内置模板失败')
  } finally {
    creatingKey.value = ''
  }
}

onMounted(load)
</script>

<style scoped>
.tpl-card {
  margin-bottom: 16px;
  cursor: pointer;
  transition: transform 0.15s;
}
.tpl-card:hover {
  transform: translateY(-2px);
}
.tpl-card h3 {
  margin: 0 0 6px;
}
</style>
