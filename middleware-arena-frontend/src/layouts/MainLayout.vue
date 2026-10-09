<template>
  <div class="app">
    <aside class="sidebar">
      <div class="brand">
        <div class="logo">M</div>
        <div>Middleware<br />Arena</div>
      </div>
      <div class="nav-title">工作台</div>
      <nav class="nav">
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          :class="{ active: isActive(item.path) }"
        >
          <span>{{ item.icon }}</span>
          <span>{{ item.title }}</span>
        </router-link>
      </nav>
      <div class="side-bottom">
        <strong>中间件性能实验平台</strong><br />
        Spring Cloud · Redis · RabbitMQ · Seata · Elasticsearch<br />
        <span class="tag">v2.1</span>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <div class="top-actions">
          <button class="btn primary" @click="$router.push('/scenes/new')">＋ 新建实验</button>
          <NotificationBell />
          <div class="avatar" @click="$router.push('/account')" :title="userNick">
            {{ avatarLetter }}
          </div>
        </div>
      </header>

      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import NotificationBell from '@/components/NotificationBell.vue'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notification'

const route = useRoute()
const userStore = useUserStore()
const notificationStore = useNotificationStore()

interface MenuItem {
  path: string
  title: string
  icon: string
}

const menuItems: MenuItem[] = [
  { path: '/', title: '实验总览', icon: '⌂' },
  { path: '/scenes', title: '实验场景', icon: '◫' },
  { path: '/scenes/new', title: '代码实验', icon: '⌘' },
  { path: '/tasks', title: '压测任务', icon: '▣' },
  { path: '/monitor', title: '性能监控', icon: '⌁' },
  { path: '/report', title: 'AI 分析', icon: '✦' },
]

function isActive(path: string) {
  // 选中态：精确匹配 /，前缀匹配其他
  if (path === '/') return route.path === '/'
  return route.path === path || route.path.startsWith(path + '/')
}

const userNick = computed(
  () => userStore.userInfo?.nickname || userStore.userInfo?.username || '',
)
const avatarLetter = computed(() => {
  const n = userNick.value.trim()
  return n ? n.charAt(0) : '?'
})

onMounted(() => {
  notificationStore.start()
})
onBeforeUnmount(() => {
  notificationStore.stop()
})

// 监听登出：从外部触发时（router 守卫或别处调 userStore.logout）清掉 polling
// —— 已通过 MainLayout 自己卸载 cleanup
</script>

<style scoped>
/* 全部由 src/assets/demo.css 提供，scoped 留空让全局样式生效 */
</style>
