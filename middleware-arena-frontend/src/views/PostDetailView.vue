<template>
  <div class="post-wrapper" v-loading="loading">
    <el-card v-if="post">
      <h2 class="title">{{ post.title }}</h2>
      <div class="meta">
        <el-link type="primary" @click="$router.push(`/community/user/${post.authorId}`)">
          作者 #{{ post.authorId }}
        </el-link>
        <span class="dot">·</span>
        <span>{{ formatTime(post.createdAt) }}</span>
      </div>
      <div class="content">{{ post.content }}</div>
      <el-divider />
      <el-space wrap>
        <el-button :type="likeStatus.liked ? 'danger' : 'default'" @click="toggleLike">
          <el-icon><Star /></el-icon>
          {{ likeStatus.liked ? '已点赞' : '点赞' }} ({{ likeStatus.likeCount }})
        </el-button>
        <el-button :type="favoriteStatus.favorited ? 'warning' : 'default'" @click="toggleFavorite">
          <el-icon><Collection /></el-icon>
          {{ favoriteStatus.favorited ? '已收藏' : '收藏' }} ({{ favoriteStatus.favoriteCount }})
        </el-button>
        <el-button v-if="isOwner" @click="$router.push(`/community/post/edit/${post.id}`)">
          编辑
        </el-button>
        <el-button v-if="isOwner" type="danger" @click="handleDeletePost">删除</el-button>
      </el-space>
    </el-card>

    <el-card v-if="post" class="comment-section">
      <template #header>
        <h3>评论 ({{ post.commentCount }})</h3>
      </template>
      <div class="comment-input">
        <el-input v-model="newComment" type="textarea" :rows="3" placeholder="说点什么…" />
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="!newComment.trim()"
          @click="submitComment"
        >
          发表
        </el-button>
      </div>

      <el-empty v-if="!loading && comments.length === 0" description="还没有评论" />

      <div v-for="c in comments" :key="c.id" class="comment-item">
        <div class="comment-header">
          <el-link type="primary" @click="$router.push(`/community/user/${c.authorId}`)">
            用户 #{{ c.authorId }}
          </el-link>
          <span class="dot">·</span>
          <span class="time">{{ formatTime(c.createdAt) }}</span>
          <el-button
            v-if="userStore.userInfo && c.authorId === userStore.userInfo.id"
            size="small"
            type="danger"
            text
            @click="handleDeleteComment(c.id)"
          >
            删除
          </el-button>
        </div>
        <div class="comment-content">{{ c.content }}</div>
        <div class="reply-section">
          <el-button size="small" text @click="toggleReply(c.id)">
            {{ replyOpen.has(c.id) ? '收起回复' : '回复' }}
          </el-button>
          <div v-if="replyOpen.has(c.id)" class="reply-list">
            <div v-for="r in replies[c.id] || []" :key="r.id" class="reply-item">
              <el-link type="primary" @click="$router.push(`/community/user/${r.authorId}`)">
                用户 #{{ r.authorId }}
              </el-link>
              ：{{ r.content }}
              <span class="time">· {{ formatTime(r.createdAt) }}</span>
            </div>
            <div class="reply-input">
              <el-input v-model="replyText[c.id]" size="small" placeholder="回复…" />
              <el-button
                size="small"
                type="primary"
                :disabled="!(replyText[c.id] || '').trim()"
                @click="submitReply(c.id)"
              >
                发送
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Star, Collection } from '@element-plus/icons-vue'
import {
  getPost,
  getLikeStatus,
  likePost,
  unlikePost,
  getFavoriteStatus,
  favoritePost,
  unfavoritePost,
  pageComments,
  pageReplies,
  createComment,
  deleteComment,
  deletePost,
} from '@/api/community'
import type {
  PostResponse,
  CommentResponse,
  LikeStatusResponse,
  FavoriteStatusResponse,
} from '@/api/community'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const postId = computed(() => Number(route.params.postId))
const post = ref<PostResponse | null>(null)
const comments = ref<CommentResponse[]>([])
const replies = reactive<Record<number, CommentResponse[]>>({})
const replyOpen = ref<Set<number>>(new Set())
const replyText = reactive<Record<number, string>>({})

const newComment = ref('')
const submitting = ref(false)
const loading = ref(false)

const likeStatus = ref<LikeStatusResponse>({ liked: false, likeCount: 0 })
const favoriteStatus = ref<FavoriteStatusResponse>({ favorited: false, favoriteCount: 0 })

// 后端字段是 authorId，不是 userId
const isOwner = computed(
  () => !!post.value && !!userStore.userInfo && post.value.authorId === userStore.userInfo.id,
)

function formatTime(t: string) {
  return new Date(t).toLocaleString('zh-CN')
}

async function loadPost() {
  loading.value = true
  try {
    post.value = await getPost(postId.value)
    await Promise.all([loadLikeStatus(), loadFavoriteStatus(), loadComments()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载帖子失败')
  } finally {
    loading.value = false
  }
}

async function loadLikeStatus() {
  likeStatus.value = await getLikeStatus(postId.value).catch(() => ({
    liked: false,
    likeCount: post.value?.likeCount ?? 0,
  }))
}

async function loadFavoriteStatus() {
  favoriteStatus.value = await getFavoriteStatus(postId.value).catch(() => ({
    favorited: false,
    favoriteCount: post.value?.favoriteCount ?? 0,
  }))
}

async function loadComments() {
  // 后端 MAX_PAGE_SIZE=50
  comments.value = await pageComments(postId.value, 1, 50).catch(() => [])
}

async function toggleLike() {
  try {
    if (likeStatus.value.liked) await unlikePost(postId.value)
    else await likePost(postId.value)
    await loadLikeStatus()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

async function toggleFavorite() {
  try {
    if (favoriteStatus.value.favorited) await unfavoritePost(postId.value)
    else await favoritePost(postId.value)
    await loadFavoriteStatus()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

async function submitComment() {
  const content = newComment.value.trim()
  if (!content) return
  submitting.value = true
  try {
    await createComment(postId.value, { content })
    newComment.value = ''
    // 后端 CommentServiceImpl.addComment 同步 +1 计数，缓存失效。但前端需要主动拉新 post
    // 同时刷新评论列表 + 帖子（拿到新的 commentCount）
    await Promise.all([loadComments(), refreshPostHeader()])
    ElMessage.success('发表成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发表失败')
  } finally {
    submitting.value = false
  }
}

async function refreshPostHeader() {
  if (!post.value) return
  try {
    post.value = await getPost(postId.value)
  } catch {
    // 静默：评论已发，只是 header 没刷新
  }
}

async function toggleReply(commentId: number) {
  if (replyOpen.value.has(commentId)) {
    replyOpen.value.delete(commentId)
    return
  }
  replyOpen.value.add(commentId)
  if (!replies[commentId]) {
    replies[commentId] = await pageReplies(commentId, 1, 50).catch(() => [])
  }
}

async function submitReply(parentId: number) {
  const content = (replyText[parentId] || '').trim()
  if (!content) return
  try {
    await createComment(postId.value, { content, parentId })
    replyText[parentId] = ''
    replies[parentId] = await pageReplies(parentId, 1, 50).catch(() => [])
    await refreshPostHeader()
    ElMessage.success('回复成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '回复失败')
  }
}

async function handleDeleteComment(commentId: number) {
  try {
    await ElMessageBox.confirm('确认删除该评论？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteComment(postId.value, commentId)
    await Promise.all([loadComments(), refreshPostHeader()])
    ElMessage.success('已删除')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

async function handleDeletePost() {
  try {
    await ElMessageBox.confirm('确认删除该帖子？此操作不可恢复', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deletePost(postId.value)
    ElMessage.success('已删除')
    router.replace('/community')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

onMounted(loadPost)
</script>

<style scoped>
.post-wrapper {
  max-width: 900px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.title {
  margin: 0 0 8px;
}
.meta {
  color: #909399;
  font-size: 13px;
  display: flex;
  gap: 6px;
  align-items: center;
}
.meta .dot {
  color: #dcdfe6;
}
.content {
  white-space: pre-wrap;
  line-height: 1.7;
  padding: 16px 0;
}
.comment-input {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.comment-input .el-input {
  flex: 1;
}
.comment-item {
  padding: 12px 0;
  border-bottom: 1px solid #ebeef5;
}
.comment-header {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 13px;
  margin-bottom: 4px;
}
.comment-content {
  line-height: 1.6;
  padding-left: 8px;
}
.reply-section {
  padding-left: 16px;
  margin-top: 8px;
}
.reply-list {
  margin-top: 4px;
}
.reply-item {
  padding: 4px 0;
  font-size: 13px;
}
.reply-item .time {
  color: #909399;
  margin-left: 4px;
}
.reply-input {
  display: flex;
  gap: 4px;
  margin-top: 6px;
}
</style>
