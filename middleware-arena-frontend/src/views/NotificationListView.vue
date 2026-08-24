<template>
  <div class="notif-wrapper" v-loading="loading">
    <div class="header-bar">
      <h2>消息中心</h2>
      <el-space>
        <el-tag>未读 {{ unreadCount }}</el-tag>
        <el-button @click="refresh">刷新</el-button>
        <el-button type="primary" :disabled="unreadCount === 0" @click="markAllRead">
          全部已读
        </el-button>
      </el-space>
    </div>
    <el-empty v-if="!loading && list.length === 0" description="还没有消息" />
    <el-table
      v-else
      :data="list"
      stripe
      :row-class-name="(row: any) => (row.isRead ? '' : 'unread-row')"
      @row-click="onRowClick"
    >
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="tagType(row.type)">{{ labelOf(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="标题" prop="title" min-width="200" />
      <el-table-column label="内容" prop="content" min-width="300">
        <template #default="{ row }">{{ truncate(row.content, 80) }}</template>
      </el-table-column>
      <el-table-column label="时间" width="180">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button v-if="!row.isRead" size="small" type="primary" @click.stop="markOne(row.id)">
            标记已读
          </el-button>
          <span v-else class="muted">已读</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useNotificationStore } from '@/stores/notification'
import type { NotificationResponse, NotificationType } from '@/api/notification'

const router = useRouter()
const store = useNotificationStore()
const loading = computed(() => store.loading)
const list = computed(() => store.notifications)
const unreadCount = computed(() => store.unreadCount)

function truncate(s: string, n: number) {
  return s.length > n ? s.slice(0, n) + '…' : s
}
function formatTime(t: string) {
  return new Date(t).toLocaleString('zh-CN')
}

// 后端实际枚举：experiment_done / announcement / mention
function labelOf(t: NotificationType) {
  if (t === 'experiment_done') return '实验'
  if (t === 'announcement') return '公告'
  if (t === 'mention') return '@我'
  return '消息'
}
function tagType(t: NotificationType): 'success' | 'warning' | 'info' | 'primary' | 'danger' {
  if (t === 'experiment_done') return 'success'
  if (t === 'mention') return 'warning'
  if (t === 'announcement') return 'primary'
  return 'info'
}

async function refresh() {
  await Promise.all([store.loadList(), store.refreshUnread()])
}

async function markOne(id: number) {
  await store.readOne(id)
}

async function markAllRead() {
  const unread = list.value.filter((n) => !n.isRead)
  for (const n of unread) {
    await store.readOne(n.id).catch(() => undefined)
  }
  await store.refreshUnread()
  ElMessage.success(`已标记 ${unread.length} 条已读`)
}

async function onRowClick(row: NotificationResponse) {
  if (!row.isRead) await store.readOne(row.id)
  // 后端 sourceType 当前只产出 "experiment"（NotificationServiceImpl.handleExperimentCompleted）
  if (row.sourceType === 'experiment' && row.sourceId) {
    router.push(`/experiment/task/${row.sourceId}`)
  }
}

onMounted(refresh)
</script>

<style scoped>
.notif-wrapper {
  max-width: 1200px;
  margin: 0 auto;
}
.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.muted {
  color: #c0c4cc;
}
:deep(.unread-row) {
  background: #f0f9ff;
}
</style>
