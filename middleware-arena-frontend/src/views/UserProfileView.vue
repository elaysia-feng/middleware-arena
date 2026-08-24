<template>
  <div class="profile-wrapper" v-loading="loading">
    <el-card>
      <div class="header">
        <el-avatar :size="64">{{ avatarLetter }}</el-avatar>
        <div class="meta">
          <h2>用户 #{{ userId }}</h2>
          <p class="bio">@user_{{ userId }}</p>
          <p v-if="!isSelf && (status.following !== undefined || posts.length)" class="stats">
            <span>{{ posts.length }} 帖子</span>
            <span class="dot">·</span>
            <span>{{ status.following ? '已关注' : '未关注' }}</span>
          </p>
        </div>
        <div class="actions">
          <el-button
            v-if="!isSelf"
            :type="status.following ? 'default' : 'primary'"
            :loading="following"
            @click="toggleFollow"
          >
            {{ status.following ? '已关注' : '关注' }}
          </el-button>
          <el-button v-else @click="$router.push('/account')">账号设置</el-button>
        </div>
      </div>
    </el-card>

    <el-card>
      <template #header>
        <h3>{{ isSelf ? '我' : 'TA' }}的帖子（{{ posts.length }}）</h3>
      </template>
      <el-empty v-if="!loading && posts.length === 0" description="还没有帖子" />
      <el-row :gutter="16">
        <el-col v-for="post in posts" :key="post.id" :xs="24" :sm="12" :md="8">
          <el-card shadow="hover" class="post-card" @click="$router.push(`/community/post/${post.id}`)">
            <h4>{{ post.title }}</h4>
            <p class="excerpt">{{ truncate(post.content, 80) }}</p>
            <div class="meta-line">
              <span>{{ formatTime(post.createdAt) }}</span>
              <span><el-icon><ChatDotRound /></el-icon> {{ post.commentCount ?? 0 }}</span>
              <span><el-icon><Star /></el-icon> {{ post.likeCount ?? 0 }}</span>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Star, ChatDotRound } from '@element-plus/icons-vue'
import { pagePosts, getFollowStatus, followUser, unfollowUser } from '@/api/community'
import type { PostResponse, FollowStatusResponse } from '@/api/community'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const userStore = useUserStore()
const userId = computed(() => Number(route.params.userId))
const isSelf = computed(
  () => !!userStore.userInfo && userStore.userInfo.id === userId.value,
)

// 后端没有 followerCount / followingCount，只能显示状态本身
const status = ref<FollowStatusResponse>({ userId: 0, following: false })
const posts = ref<PostResponse[]>([])
const loading = ref(false)
const following = ref(false)

const avatarLetter = computed(() => String(userId.value).charAt(0))

function truncate(s: string, n: number) {
  return s.length > n ? s.slice(0, n) + '…' : s
}
function formatTime(t: string) {
  return new Date(t).toLocaleString('zh-CN')
}

async function loadProfile() {
  loading.value = true
  try {
    if (!isSelf.value) {
      status.value = await getFollowStatus(userId.value).catch(() => ({
        userId: userId.value,
        following: false,
      }))
    }
    // 后端没有「按用户过滤帖子」接口；pagePosts size 上限 50，超过会 400
    const list = await pagePosts(1, 50).catch(() => [])
    posts.value = list.filter((p) => p.authorId === userId.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function toggleFollow() {
  following.value = true
  try {
    if (status.value.following) await unfollowUser(userId.value)
    else await followUser(userId.value)
    status.value = await getFollowStatus(userId.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  } finally {
    following.value = false
  }
}

onMounted(loadProfile)
</script>

<style scoped>
.profile-wrapper {
  max-width: 1100px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.header {
  display: flex;
  gap: 16px;
  align-items: center;
}
.meta {
  flex: 1;
}
.meta h2 {
  margin: 0 0 4px;
}
.bio {
  color: #909399;
  margin: 0 0 8px;
}
.stats {
  color: #909399;
  font-size: 13px;
  margin: 0;
  display: flex;
  gap: 6px;
  align-items: center;
}
.stats .dot {
  color: #dcdfe6;
}
.post-card {
  margin-bottom: 16px;
  cursor: pointer;
}
.post-card h4 {
  margin: 0 0 6px;
}
.excerpt {
  color: #606266;
  margin: 0 0 8px;
  line-height: 1.5;
  min-height: 48px;
}
.meta-line {
  display: flex;
  gap: 12px;
  color: #909399;
  font-size: 13px;
  align-items: center;
}
.meta-line span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
