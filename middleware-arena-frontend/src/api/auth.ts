import request from './request'

export interface LoginData {
  username: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
}

export interface UserInfoResponse {
  id: number
  username: string
  nickname: string
  tier: string
  vipExpireAt?: string
}

// TODO: login —— 调 POST /auth/login，返回 { accessToken, refreshToken, userInfo }
// 成功后调用 userStore.setTokens 存储双 token，路由跳转首页
export function login(data: LoginData): Promise<LoginResponse> {
  return request.post('/auth/login', data).then((result) => result as unknown as LoginResponse)
}

export function refresh(refreshToken: string): Promise<LoginResponse> {
  return request.post('/auth/refresh', { refreshToken }).then((result) => result as unknown as LoginResponse)
}

export function getMe(): Promise<UserInfoResponse> {
  return request.get('/auth/me').then((result) => result as unknown as UserInfoResponse)
}

export function logout(refreshToken: string): Promise<void> {
  return request.post('/auth/logout', { refreshToken }).then(() => undefined)
}
