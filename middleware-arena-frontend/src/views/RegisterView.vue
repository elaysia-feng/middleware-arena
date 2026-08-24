<template>
  <div class="register-wrapper">
    <el-card class="register-card">
      <template #header>
        <h2 class="register-title">Middleware Arena 注册</h2>
      </template>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="default">
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            :prefix-icon="User"
            placeholder="3-32 位字符"
            maxlength="32"
          />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" :prefix-icon="Avatar" placeholder="选填，默认为用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :prefix-icon="Lock"
            placeholder="至少 6 位"
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            class="submit-btn"
            :loading="submitting"
            :disabled="submitting"
            @click="handleSubmit"
          >
            注 册
          </el-button>
        </el-form-item>
        <el-form-item>
          <div class="footer">
            已有账号？<el-link type="primary" @click="$router.push('/login')">去登录</el-link>
          </div>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Avatar } from '@element-plus/icons-vue'
import { register, getMe } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const userStore = useUserStore()

const form = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: '',
})

// 后端 @Size(min=3, max=32) 无字符集限制
const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名 3-32 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '至少 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, cb) => {
        if (value !== form.password) cb(new Error('两次密码不一致'))
        else cb()
      },
      trigger: 'blur',
    },
  ],
}

function safeRedirect(value: unknown): string {
  if (typeof value !== 'string') return '/'
  if (!value.startsWith('/') || value.startsWith('//')) return '/'
  return value
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    // 后端直接返回 LoginResponse，注册成功立即登录
    const tokens = await register({
      username: form.username,
      nickname: form.nickname.trim() || undefined,
      password: form.password,
    })
    userStore.setTokens(tokens.accessToken, tokens.refreshToken)
    try {
      userStore.setUserInfo(await getMe())
    } catch {
      await userStore.logout()
      ElMessage.error('注册成功但拉取用户信息失败，请登录')
      await router.push('/login')
      return
    }
    ElMessage.success('注册成功，已自动登录')
    await router.replace(safeRedirect(route.query.redirect))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '注册失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.register-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.register-card {
  width: 460px;
}
.register-title {
  margin: 0;
  text-align: center;
  font-size: 20px;
}
.submit-btn {
  width: 100%;
}
.footer {
  width: 100%;
  text-align: center;
  color: #909399;
}
</style>
