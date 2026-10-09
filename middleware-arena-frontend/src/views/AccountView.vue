<template>
  <div class="account-wrapper">
    <el-card v-loading="loading" class="account-card">
      <template #header>
        <h2 class="account-title">账号信息</h2>
      </template>
      <el-descriptions v-if="user" :column="2" border>
        <el-descriptions-item label="用户名">{{ user.username }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ user.nickname || '—' }}</el-descriptions-item>
        <el-descriptions-item label="会员等级">
          <el-tag :type="vipTagType" effect="dark">{{ tierLabel }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="VIP 到期">{{ vipText }}</el-descriptions-item>
      </el-descriptions>
      <el-divider />
      <el-space wrap>
        <el-button type="primary" :loading="recharging" @click="handleRecharge">
          一键开通 VIP（mock）
        </el-button>
        <el-button @click="refreshUser">刷新</el-button>
      </el-space>
      <p class="tip">VIP 实际状态以 effectiveTier 为准（后端 AuthServiceImpl 在 vipExpireAt 过期时返回 FREE）。</p>
    </el-card>

  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getMe, mockRecharge } from '@/api/auth'

const userStore = useUserStore()
const loading = ref(false)
const recharging = ref(false)

const user = computed(() => userStore.userInfo)

// 用 effectiveTier 而非 tier：tier 是 DB 里的"购买记录"，effectiveTier 才是真实有效期内的等级
const tierLabel = computed(() => {
  const t = userStore.isVipActive ? 'VIP' : (user.value?.effectiveTier ?? user.value?.tier ?? 'FREE')
  return t === 'VIP' ? 'VIP' : '普通用户'
})

const vipTagType = computed<'success' | 'info'>(() =>
  userStore.isVipActive ? 'success' : 'info',
)

const vipText = computed(() => {
  if (!userStore.isVipActive) return '未开通 / 已过期'
  return user.value?.vipExpireAt ?? '未开通'
})

async function refreshUser() {
  loading.value = true
  try {
    userStore.setUserInfo(await getMe())
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取用户信息失败')
  } finally {
    loading.value = false
  }
}

async function handleRecharge() {
  recharging.value = true
  try {
    const result = await mockRecharge()
    ElMessage.success(`已开通 VIP，到期：${result.vipExpireAt ?? '—'}`)
    await refreshUser()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '充值失败')
  } finally {
    recharging.value = false
  }
}

onMounted(async () => {
  if (!userStore.userInfo) await refreshUser()
})
</script>

<style scoped>
.account-wrapper {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.account-card {
  max-width: 900px;
}
.account-title {
  margin: 0;
  font-size: 18px;
}
.tip {
  margin-top: 12px;
  color: #909399;
  font-size: 12px;
}
</style>
