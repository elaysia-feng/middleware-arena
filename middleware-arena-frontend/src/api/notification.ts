import request from './request'

// 后端 NotificationResponse.java 实际枚举：experiment_done / announcement / mention
export type NotificationType = 'experiment_done' | 'announcement' | 'mention' | string

export interface NotificationResponse {
  id: number
  userId?: number
  type: NotificationType
  title: string
  content: string
  // 后端字段是 isRead（Jackson 把 Boolean isRead 序列化成 JSON isRead），不是 read
  isRead: boolean
  // 后端字段是 sourceType / sourceId，不是 relatedType / relatedId
  sourceType?: 'experiment' | 'order' | 'system' | string
  sourceId?: number
  createdAt: string
}

export function listNotifications(page = 1, size = 20): Promise<NotificationResponse[]> {
  return request
    .get('/notification/list', { params: { page, size } })
    .then((r) => r as unknown as NotificationResponse[])
}

// 后端返回的是 ApiResponse<Long>，axios 拦截器解包后是 bare number
export function getUnreadCount(): Promise<number> {
  return request
    .get('/notification/unread-count')
    .then((r) => (typeof r === 'number' ? r : Number(r) || 0))
}

export function markRead(id: number): Promise<void> {
  return request.post(`/notification/${id}/read`).then(() => undefined)
}
