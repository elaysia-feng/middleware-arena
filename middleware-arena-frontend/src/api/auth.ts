import request from './request'

export interface LoginData {
  username: string
  password: string
}

// TODO: login —— 调 POST /auth/login，返回 { accessToken, refreshToken, userInfo }
// 成功后调用 userStore.setTokens 存储双 token，路由跳转首页
export function login(data: LoginData) {
  return request.post('/auth/login', data)
}
