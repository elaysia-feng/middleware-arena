// 前后端共享的通用类型。ApiResponse<T> 在 src/api/request.ts 内部私有维护。
export interface PageQuery {
  page: number
  size: number
}

// 后端目前是 List<T> 不带 total，先在前端用这个保守结构；以后后端补 PageResult 直接换。
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}
