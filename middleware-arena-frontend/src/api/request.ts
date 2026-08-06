import axios from 'axios'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 10000,
})

// TODO[双 token]：请求拦截器 —— 从 user store 取 accessToken 注入 Authorization header
// 注意：axios 实例在模块顶层创建，store 可能尚未初始化，需在拦截器内通过 import 或
// 从 pinia 实例动态获取 user store（避免循环依赖）。
request.interceptors.request.use(
  (config) => {
    // TODO: 从 user store 读取 accessToken，设置 config.headers.Authorization = `Bearer ${token}`
    return config
  },
  (error) => {
    return Promise.reject(error)
  },
)

// TODO[双 token]：响应拦截器 —— 解包 Result 并处理 401 刷新
// 1. 后端返回统一结构 { code: number, data: T, message: string }
// 2. code === 200 时直接返回 data，否则 reject(message)
// 3. 收到 401 时：用 refreshToken 调 POST /auth/refresh 获取新 accessToken，
//    存入 store 后重放原请求；refresh 也 401 则跳登录页
request.interceptors.response.use(
  (response) => {
    const res = response.data
    // TODO: 根据后端统一返回格式解包
    return res
  },
  (error) => {
    // TODO: 401 → refresh token 流程
    return Promise.reject(error)
  },
)

export default request
