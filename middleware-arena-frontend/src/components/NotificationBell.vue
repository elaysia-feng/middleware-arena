<template>
  <el-badge :value="store.unreadCount" :hidden="store.unreadCount === 0" :max="99">
    <el-button text @click="open">
      <el-icon class="bell-icon"><Bell /></el-icon>
    </el-button>
  </el-badge>

  <el-drawer v-model="drawerOpen" title="消息通知" size="420px" direction="rtl">
    <div v-loading="store.loading">
      <el-empty v-if="!store.loading && drawerNotifications.length === 0" description="暂无消息" />
      <div
        v-for="n in drawerNotifications"
        :key="n.id"
        class="notif-item"
        :class="{ unread: !n.isRead }"
        @click="onClickItem(n)"
      >
        <div class="notif-header">
          <el-tag size="small" :type="tagType(n.type)">{{ labelOf(n.type) }}</el-tag>
          <span class="notif-time">{{ formatTime(n.createdAt) }}</span>
        </div>
        <div class="notif-title">{{ n.title }}</div>
        <div class="notif-content">{{ n.content }}</div>
      </div>
    </div>
    <template #footer>
      <div class="drawer-footer">
        <el-button @click="goAll">查看全部</el-button>
        <el-button type="primary" @click="markAllRead" :disabled="store.unreadCount === 0">
          全部已读
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'
import { useNotificationStore } from '@/stores/notification'
import type { NotificationResponse, NotificationType } from '@/api/notification'

const router = useRouter()
const store = useNotificationStore()
const drawerOpen = ref(false)

const drawerNotifications = computed(() => store.notifications.slice(0, 10))

function formatTime(t: string) {
  const d = new Date(t)
  return d.toLocaleString('zh-CN')
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

async function open() {
  drawerOpen.value = true
  if (store.notifications.length === 0) {
    await store.loadList()
  }
}

async function onClickItem(n: NotificationResponse) {
  if (!n.isRead) await store.readOne(n.id)
  // 后端 sourceType 实际只产出 "experiment"（NotificationServiceImpl.handleExperimentCompleted）
  // 当前 producer：实验完成时 sourceType=experiment, sourceId=taskId
  if (n.sourceType === 'experiment' && n.sourceId) {
    router.push(`/experiment/task/${n.sourceId}`)
  }
  drawerOpen.value = false
}

function goAll() {
  drawerOpen.value = false
  router.push('/notification')
}

// 后端没有批处理接口；只能串行读所有未读，10+ 条会慢
async function markAllRead() {
  const unread = store.notifications.filter((n) => !n.isRead)
  for (const n of unread) {
    await store.readOne(n.id).catch(() => undefined)
  }
  await store.refreshUnread()
  ElMessage.success(`已标记 ${unread.length} 条已读`)
}
</script>

<style scoped>
.bell-icon {
  font-size: 18px;
}
.notif-item {
  padding: 12px;
  border-bottom: 1px solid #ebeef5;
  cursor: pointer;
  transition: background 0.15s;
}
.notif-item:hover {
  background: #f5f7fa;
}
.notif-item.unread {
  background: #f0f9ff;
}
.notif-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.notif-time {
  color: #909399;
  font-size: 12px;
}
.notif-title {
  font-weight: 600;
  margin-bottom: 4px;
}
.notif-content {
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}
.drawer-footer {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}
</style>
