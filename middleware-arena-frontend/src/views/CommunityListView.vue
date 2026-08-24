<template>
  <div class="community-wrapper">
    <el-card class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索帖子标题/内容…"
        clearable
        :prefix-icon="Search"
        @keyup.enter="handleSearch"
        class="search-input"
      />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button v-if="keyword" @click="clearSearch">清除</el-button>
      <el-button type="success" :icon="EditPen" @click="$router.push('/community/post/create')">
        发帖
      </el-button>
    </el-card>

    <el-empty v-if="!loading && posts.length === 0" description="还没有帖子" />

    <el-row v-loading="loading" :gutter="16">
      <el-col v-for="post in posts" :key="post.id" :xs="24" :sm="12" :md="8">
        <el-card class="post-card" shadow="hover" @click="openPost(post.id)">
          <h3 class="post-title">{{ post.title }}</h3>
          <p class="post-content">{{ truncate(post.content, 80) }}</p>
          <div class="post-meta">
            <span>作者 #{{ post.authorId }}</span>
            <span class="dot">·</span>
            <span>{{ formatTime(post.createdAt) }}</span>
          </div>
          <div class="post-stats">
            <span><el-icon><ChatDotRound /></el-icon> {{ post.commentCount ?? 0 }}</span>
            <span><el-icon><Star /></el-icon> {{ post.likeCount ?? 0 }}</span>
            <span><el-icon><Collection /></el-icon> {{ post.favoriteCount ?? 0 }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <div class="pagination">
      <el-pagination
        v-model:current-page="page"
        :total="estimatedTotal"
        layout="total, prev, pager, next, jumper"
        @current-change="reload"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, EditPen, ChatDotRound, Star, Collection } from '@element-plus/icons-vue'
import { pagePosts, searchPosts } from '@/api/community'
import type { PostResponse } from '@/api/community'

const route = useRoute()
const router = useRouter()
const posts = ref<PostResponse[]>([])
const loading = ref(false)
const page = ref(1)
const size = 10
const estimatedTotal = ref(0)
const keyword = ref('')

function formatTime(t: string) {
  return new Date(t).toLocaleString('zh-CN')
}

function truncate(s: string, n: number) {
  return s.length > n ? s.slice(0, n) + '…' : s
}

async function reload() {
  loading.value = true
  try {
    const kw = keyword.value.trim()
    const data: PostResponse[] = kw
      ? await searchPosts(kw, page.value, size)
      : await pagePosts(page.value, size)
    posts.value = data
    estimatedTotal.value =
      data.length >= size ? page.value * size + 1 : (page.value - 1) * size + data.length
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载帖子失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  reload()
}

function clearSearch() {
  keyword.value = ''
  page.value = 1
  reload()
}

function openPost(id: number) {
  router.push(`/community/post/${id}`)
}

onMounted(() => {
  const q = route.query.keyword
  if (typeof q === 'string') keyword.value = q
  reload()
})
</script>

<style scoped>
.community-wrapper {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
}
.search-input {
  flex: 1;
  max-width: 480px;
}
.post-card {
  margin-bottom: 16px;
  cursor: pointer;
  transition: transform 0.15s;
}
.post-card:hover {
  transform: translateY(-2px);
}
.post-title {
  margin: 0 0 8px;
  font-size: 16px;
}
.post-content {
  color: #606266;
  margin: 0 0 12px;
  line-height: 1.5;
  min-height: 48px;
}
.post-meta {
  font-size: 12px;
  color: #909399;
  display: flex;
  gap: 6px;
  align-items: center;
}
.post-meta .dot {
  color: #dcdfe6;
}
.post-stats {
  display: flex;
  gap: 12px;
  margin-top: 8px;
  font-size: 13px;
  color: #909399;
}
.post-stats span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.pagination {
  display: flex;
  justify-content: center;
  padding: 16px;
}
</style>
