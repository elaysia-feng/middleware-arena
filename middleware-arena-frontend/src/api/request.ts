import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { pinia } from '@/stores'
import { useUserStore } from '@/stores/user'

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

// 自定义错误，把后端 numeric code 也带出来；下游 catch 可以区分 400/403/500 等
export class ApiError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.code = code
    this.name = 'ApiError'
  }
}

interface RetryRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

const baseURL = import.meta.env.VITE_API_BASE || '/api'
const request = axios.create({ baseURL, timeout: 10000 })
let refreshPromise: Promise<string> | null = null

request.interceptors.request.use((config) => {
  const userStore = useUserStore(pinia)
  if (userStore.accessToken) {
    config.headers.Authorization = `Bearer ${userStore.accessToken}`
  }
  return config
})

async function refreshAccessToken(): Promise<string> {
  const userStore = useUserStore(pinia)
  if (!userStore.refreshToken) throw new Error('登录状态已失效')

  if (!refreshPromise) {
    refreshPromise = axios
      .post<ApiResponse<{ accessToken: string; refreshToken: string }>>(
        `${baseURL}/auth/refresh`,
        { refreshToken: userStore.refreshToken },
      )
      .then(({ data }) => {
        if (data.code !== 200) throw new ApiError(data.code, data.message)
        userStore.setTokens(data.data.accessToken, data.data.refreshToken)
        return data.data.accessToken
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

async function retryAfterRefresh(config: RetryRequestConfig) {
  if (config._retry || config.url?.includes('/auth/refresh')) {
    throw new ApiError(401, '登录状态已失效')
  }
  config._retry = true
  config.headers.Authorization = `Bearer ${await refreshAccessToken()}`
  return request(config)
}

request.interceptors.response.use(
  async (response): Promise<any> => {
    const body = response.data as ApiResponse<unknown>
    if (body.code === 200) return body.data
    if (body.code === 401) return retryAfterRefresh(response.config as RetryRequestConfig)
    if (typeof body.code === 'number') {
      return Promise.reject(new ApiError(body.code, body.message || '请求失败'))
    }
    // Python Agent 通过 Gateway 返回 FastAPI 原始 JSON，不使用 Java ApiResponse 包装。
    return response.data
  },
  async (error: AxiosError): Promise<any> => {
    if (error.response?.status === 401 && error.config) {
      try {
        return await retryAfterRefresh(error.config as RetryRequestConfig)
      } catch (refreshError) {
        await useUserStore(pinia).logout()
        // 保留 redirect query，便于登录后回到原页面
        const redirect = encodeURIComponent(location.pathname + location.search)
        if (location.pathname !== '/login') {
          location.assign(`/login?redirect=${redirect}`)
        }
        return Promise.reject(
          refreshError instanceof Error ? refreshError : new Error('登录状态已失效'),
        )
      }
    }
    return Promise.reject(error)
  },
)

export default request
