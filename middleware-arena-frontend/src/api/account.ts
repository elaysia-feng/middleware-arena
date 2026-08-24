import request from './request'

// 后端 AccountBalanceResponse 实际只有 userId + balance 两个字段
// balance 单位是"分"（cents），前端要用 balance/100 显示元
export interface AccountBalanceResponse {
  userId: number
  balance: number // 单位：分（cents）
}

export function getBalance(userId: number): Promise<AccountBalanceResponse> {
  return request
    .get(`/account/balance/${userId}`)
    .then((r) => r as unknown as AccountBalanceResponse)
}

// 工具：分 → 元，保留 2 位小数
export function formatCents(cents: number | undefined | null): string {
  if (cents === undefined || cents === null || !Number.isFinite(cents)) return '—'
  return (cents / 100).toFixed(2)
}
