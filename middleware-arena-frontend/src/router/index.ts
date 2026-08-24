import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { pinia } from '@/stores'
import { useUserStore } from '@/stores/user'
import { getMe } from '@/api/auth'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    title?: string
    hideInMenu?: boolean
    icon?: string
  }
}

// 主菜单对应的 7 个 demo 视图 + 保留的 auth/community/account/notification 子路由
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', hideInMenu: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { title: '注册', hideInMenu: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      // 1. 实验总览
      {
        path: '',
        name: 'Overview',
        component: () => import('@/views/OverviewView.vue'),
        meta: { title: '实验总览' },
      },
      // 2. 实验场景
      {
        path: 'scenes',
        name: 'Scenes',
        component: () => import('@/views/ScenesView.vue'),
        meta: { title: '实验场景' },
      },
      // 3. 代码实验：创建 + 编辑现有
      {
        path: 'scenes/new',
        name: 'SceneCreate',
        component: () => import('@/views/EditorView.vue'),
        meta: { title: '新建实验', hideInMenu: true },
      },
      {
        path: 'scenes/:templateId/edit',
        name: 'Editor',
        component: () => import('@/views/EditorView.vue'),
        meta: { title: '编辑实验', hideInMenu: true },
      },
      // 实验详情
      {
        path: 'scenes/:templateId',
        name: 'ExperimentDetail',
        component: () => import('@/views/ExperimentDetailView.vue'),
        meta: { title: '实验详情', hideInMenu: true },
      },
      // 4. 压测任务
      {
        path: 'tasks',
        name: 'TaskList',
        component: () => import('@/views/TaskListView.vue'),
        meta: { title: '压测任务' },
      },
      {
        path: 'tasks/:taskId',
        name: 'TaskDetail',
        component: () => import('@/views/TaskDetailView.vue'),
        meta: { title: '任务详情', hideInMenu: true },
      },
      // 5. 性能监控
      {
        path: 'monitor',
        name: 'Monitor',
        component: () => import('@/views/MonitorView.vue'),
        meta: { title: '性能监控' },
      },
      // 6. AI 分析
      {
        path: 'report',
        name: 'Report',
        component: () => import('@/views/ReportView.vue'),
        meta: { title: 'AI 分析' },
      },
      // 7. 实验社区
      {
        path: 'community',
        name: 'CommunityList',
        component: () => import('@/views/CommunityListView.vue'),
        meta: { title: '实验社区' },
      },
      {
        path: 'community/post/create',
        name: 'PostCreate',
        component: () => import('@/views/PostEditorView.vue'),
        meta: { title: '发布帖子', hideInMenu: true },
      },
      {
        path: 'community/post/edit/:postId',
        name: 'PostEdit',
        component: () => import('@/views/PostEditorView.vue'),
        meta: { title: '编辑帖子', hideInMenu: true },
      },
      {
        path: 'community/post/:postId',
        name: 'PostDetail',
        component: () => import('@/views/PostDetailView.vue'),
        meta: { title: '帖子详情', hideInMenu: true },
      },
      {
        path: 'community/user/:userId',
        name: 'UserProfile',
        component: () => import('@/views/UserProfileView.vue'),
        meta: { title: '用户主页', hideInMenu: true },
      },
      // 消息中心 / 账号（不演示 demo 主菜单，靠顶栏铃铛 / avatar 进入）
      {
        path: 'notification',
        name: 'NotificationList',
        component: () => import('@/views/NotificationListView.vue'),
        meta: { title: '消息中心', hideInMenu: true },
      },
      {
        path: 'account',
        name: 'Account',
        component: () => import('@/views/AccountView.vue'),
        meta: { title: '账号', hideInMenu: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/ErrorView.vue'),
    meta: { title: '404', hideInMenu: true, requiresAuth: false },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

const whiteList = ['/login', '/register']

router.beforeEach(async (to) => {
  const userStore = useUserStore(pinia)
  if (whiteList.includes(to.path)) {
    return userStore.isLogin ? '/' : true
  }
  if (to.meta.requiresAuth !== false && !userStore.isLogin) {
    return `/login?redirect=${encodeURIComponent(to.fullPath)}`
  }
  if (userStore.isLogin && !userStore.userInfo) {
    try {
      userStore.setUserInfo(await getMe())
    } catch {
      await userStore.logout()
      return '/login'
    }
  }
  return true
})

router.afterEach((to) => {
  if (to.meta.title) document.title = `${to.meta.title} · Middleware Arena`
})

export default router
