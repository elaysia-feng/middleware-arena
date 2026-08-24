import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  listNotifications,
  getUnreadCount,
  markRead,
  type NotificationResponse,
} from '@/api/notification'

// 每 10s 拉一次未读数。SSE 是后续替代方案（后端 /notification/stream 已有 SseEmitter，前端暂未接入）。
const POLL_INTERVAL_MS = 10_000

export const useNotificationStore = defineStore('notification', () => {
  const notifications = ref<NotificationResponse[]>([])
  const unreadCount = ref(0)
  const loading = ref(false)
  let pollHandle: ReturnType<typeof setInterval> | null = null

  async function refreshUnread() {
    try {
      const n = await getUnreadCount()
      unreadCount.value = typeof n === 'number' ? n : 0
    } catch {
      // 静默
    }
  }

  async function loadList(page = 1, size = 20) {
    loading.value = true
    try {
      notifications.value = await listNotifications(page, size)
    } catch (error) {
      notifications.value = []
    } finally {
      loading.value = false
    }
  }

  async function readOne(id: number) {
    try {
      await markRead(id)
    } catch {
      // 即便请求失败也乐观更新 UI
    }
    const target = notifications.value.find((n) => n.id === id)
    if (target && !target.isRead) {
      target.isRead = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    }
    refreshUnread()
  }

  function start() {
    if (pollHandle) return
    refreshUnread()
    pollHandle = setInterval(refreshUnread, POLL_INTERVAL_MS)
  }

  function stop() {
    if (pollHandle) {
      clearInterval(pollHandle)
      pollHandle = null
    }
  }

  return {
    notifications,
    unreadCount,
    loading,
    start,
    stop,
    refreshUnread,
    loadList,
    readOne,
  }
})
