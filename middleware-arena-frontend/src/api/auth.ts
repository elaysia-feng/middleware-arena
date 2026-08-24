import request from './request'

export interface LoginRequest {
  username: string
  password: string
}

// 后端 RegisterRequest 实际只有 username / password / nickname 三字段，没有 email；user 表也没有 email 列
export interface RegisterRequest {
  username: string // @Size(min=3, max=32)，无字符集限制
  password: string
  nickname?: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
}

export interface UserInfoResponse {
  id: number
  username: string
  nickname: string
  // tier / effectiveTier 都存在；effectiveTier 是实际生效的等级（VIP过期后变 FREE）
  tier: string
  effectiveTier: string
  vipExpireAt?: string
}

export interface MembershipResponse {
  userId: number
  tier: string
  // effectiveTier 是 source of truth：vipExpireAt 过期时返回 "FREE"
  effectiveTier: string
  vipExpireAt?: string
}

export function register(data: RegisterRequest): Promise<LoginResponse> {
  return request
    .post('/auth/register', data)
    .then((result) => result as unknown as LoginResponse)
}

export function login(data: LoginRequest): Promise<LoginResponse> {
  return request
    .post('/auth/login', data)
    .then((result) => result as unknown as LoginResponse)
}

export function refresh(refreshToken: string): Promise<LoginResponse> {
  return request
    .post('/auth/refresh', { refreshToken })
    .then((result) => result as unknown as LoginResponse)
}

// 关键：必须主动调用后端 /auth/logout，服务端会从 Redis 删除 refreshToken；否则被盗的 token 能用到 TTL 过期
export function logout(refreshToken: string): Promise<void> {
  return request.post('/auth/logout', { refreshToken }).then(() => undefined)
}

export function getMe(): Promise<UserInfoResponse> {
  return request.get('/auth/me').then((result) => result as unknown as UserInfoResponse)
}

export function mockRecharge(): Promise<MembershipResponse> {
  return request
    .post('/auth/vip/mock-recharge')
    .then((result) => result as unknown as MembershipResponse)
}
