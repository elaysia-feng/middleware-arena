import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

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
router.beforeEach((_to, _from, next) => {
  next()
})

export default router
