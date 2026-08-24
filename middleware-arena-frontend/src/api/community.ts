import request from './request'

// ----- Types -----
// 后端 CreatePostRequest.tags 是单个 String（备注：当前未持久化，预留字段），
// 提交时用逗号拼成字符串。后端 PostResponse 没有 tags 字段返回。
export interface CreatePostRequest {
  title: string
  content: string
  tags?: string // 逗号分隔字符串
}

export interface PostResponse {
  id: number
  title: string
  content: string
  // 后端是 authorId，不是 userId；没有 username/nickname 联表
  authorId: number
  likeCount: number
  commentCount: number
  favoriteCount: number
  createdAt: string
}

export interface CommentRequest {
  content: string
  parentId?: number
}

// 后端 CommentResponse 实际字段，没有 username/nickname
export interface CommentResponse {
  id: number
  postId: number
  authorId: number
  parentId?: number
  content: string
  createdAt: string
}

export interface LikeStatusResponse {
  liked: boolean
  likeCount: number
}

export interface FavoriteStatusResponse {
  favorited: boolean
  favoriteCount: number
}

// 后端只返回 userId + following；没有 followerCount/followingCount
export interface FollowStatusResponse {
  userId: number
  following: boolean
}

// ----- Posts -----
export function createPost(data: CreatePostRequest): Promise<PostResponse> {
  return request.post('/community/post/create', data).then((r) => r as unknown as PostResponse)
}

export function updatePost(postId: number, data: CreatePostRequest): Promise<PostResponse> {
  return request.put(`/community/post/${postId}`, data).then((r) => r as unknown as PostResponse)
}

export function deletePost(postId: number): Promise<void> {
  return request.delete(`/community/post/${postId}`).then(() => undefined)
}

export function getPost(postId: number): Promise<PostResponse> {
  return request.get(`/community/post/${postId}`).then((r) => r as unknown as PostResponse)
}

// 后端 PageServiceImpl.validatePage 把 size 上限锁在 MAX_PAGE_SIZE=50，超过 400
export function pagePosts(page = 1, size = 10): Promise<PostResponse[]> {
  return request
    .get('/community/post/page', { params: { page, size } })
    .then((r) => r as unknown as PostResponse[])
}

// ----- Comments -----
export function createComment(postId: number, data: CommentRequest): Promise<CommentResponse> {
  return request
    .post(`/community/post/${postId}/comment`, data)
    .then((r) => r as unknown as CommentResponse)
}

// 后端 MAX_PAGE_SIZE=50
export function pageComments(postId: number, page = 1, size = 20): Promise<CommentResponse[]> {
  return request
    .get(`/community/post/${postId}/comment/page`, { params: { page, size } })
    .then((r) => r as unknown as CommentResponse[])
}

export function pageReplies(parentId: number, page = 1, size = 20): Promise<CommentResponse[]> {
  return request
    .get(`/community/comment/${parentId}/reply/page`, { params: { page, size } })
    .then((r) => r as unknown as CommentResponse[])
}

export function deleteComment(postId: number, commentId: number): Promise<void> {
  return request.delete(`/community/post/${postId}/comment/${commentId}`).then(() => undefined)
}

// ----- Like -----
// 后端用 PUT（收藏/点赞/关注接口都注册了 PUT 和 POST 兼容）
export function likePost(postId: number): Promise<void> {
  return request.put(`/community/post/${postId}/like`).then(() => undefined)
}

export function unlikePost(postId: number): Promise<void> {
  return request.delete(`/community/post/${postId}/like`).then(() => undefined)
}

export function getLikeStatus(postId: number): Promise<LikeStatusResponse> {
  return request
    .get(`/community/post/${postId}/like/status`)
    .then((r) => r as unknown as LikeStatusResponse)
}

// ----- Favorite -----
export function favoritePost(postId: number): Promise<void> {
  return request.post(`/community/post/${postId}/favorite`).then(() => undefined)
}

export function unfavoritePost(postId: number): Promise<void> {
  return request.delete(`/community/post/${postId}/favorite`).then(() => undefined)
}

export function getFavoriteStatus(postId: number): Promise<FavoriteStatusResponse> {
  return request
    .get(`/community/post/${postId}/favorite/status`)
    .then((r) => r as unknown as FavoriteStatusResponse)
}

// ----- Follow -----
export function followUser(userId: number): Promise<void> {
  return request.post(`/community/user/${userId}/follow`).then(() => undefined)
}

export function unfollowUser(userId: number): Promise<void> {
  return request.delete(`/community/user/${userId}/follow`).then(() => undefined)
}

export function getFollowStatus(userId: number): Promise<FollowStatusResponse> {
  return request
    .get(`/community/user/${userId}/follow/status`)
    .then((r) => r as unknown as FollowStatusResponse)
}

// ----- Search -----
export function searchPosts(keyword: string, page = 1, size = 10): Promise<PostResponse[]> {
  // 后端 SearchServiceImpl.search 关键词不能空，否则抛 PARAM_INVALID
  const kw = keyword.trim()
  if (!kw) return pagePosts(page, size)
  return request
    .get('/community/search', { params: { keyword: kw, page, size } })
    .then((r) => r as unknown as PostResponse[])
}
