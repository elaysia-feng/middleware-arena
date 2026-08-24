import request from './request'

// 后端 CreateOrderRequest 实际是单商品扁平模型：productId / quantity / requestId
// 没有 items 数组、没有 remark；userId 由服务端 UserContext 推导
export interface CreateOrderRequest {
  productId: number
  quantity: number
  requestId?: string
}

// 后端 OrderResponse 实际字段（没有 items、没有 remark/paidAt/finishedAt，没有 totalAmount）
// amount / unitPrice 单位均为"分"，前端必须除以 100
export interface OrderResponse {
  id: number
  orderNo: string
  userId: number
  productId: number
  quantity: number
  unitPrice: number // 单位：分
  amount: number // 单位：分 = unitPrice * quantity
  status: string
  createdAt: string
}

export function createOrder(data: CreateOrderRequest): Promise<OrderResponse> {
  return request.post('/order/create', data).then((r) => r as unknown as OrderResponse)
}

export function getOrder(orderId: number): Promise<OrderResponse> {
  return request.get(`/order/${orderId}`).then((r) => r as unknown as OrderResponse)
}

export const ORDER_STATUSES = {
  PENDING: 'PENDING',
  PAID: 'PAID',
  SHIPPED: 'SHIPPED',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
} as const
export type OrderStatus = (typeof ORDER_STATUSES)[keyof typeof ORDER_STATUSES]
