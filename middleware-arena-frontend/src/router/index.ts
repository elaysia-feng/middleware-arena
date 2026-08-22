import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { pinia } from '@/stores'
import { useUserStore } from '@/stores/user'
import { getMe } from '@/api/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('@/views/HomeView.vue'),
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// TODO[双 token]：路由前置守卫 —— 登录态校验
// 1. 无 accessToken 时跳转 /login（白名单路由豁免）
// 2. 有 accessToken 但无 userInfo 时，调 /auth/me 拉取用户信息
// 3. 双 token 刷新逻辑接入后实现
router.beforeEach(async (to) => {
  const userStore = useUserStore(pinia)
  if (to.path === '/login') return userStore.isLogin ? '/' : true
  if (!userStore.isLogin) return `/login?redirect=${encodeURIComponent(to.fullPath)}`
  if (!userStore.userInfo) {
    try {
      userStore.setUserInfo(await getMe())
    } catch {
      userStore.logout()
      return '/login'
    }
  }
  return true
})

export default router
