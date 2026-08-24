<template>
  <div class="login-wrapper">
    <el-card class="login-card">
      <template #header>
        <h2 class="login-title">Middleware Arena 登录</h2>
      </template>
      <el-alert
        v-if="registeredTip"
        type="success"
        :closable="false"
        show-icon
        title="注册成功，请登录"
        class="registered-tip"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="default">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            class="login-btn"
            :loading="submitting"
            :disabled="submitting"
            native-type="submit"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
        <el-form-item>
          <div class="footer">
            还没有账号？<el-link type="primary" @click="$router.push('/register')">立即注册</el-link>
          </div>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { login, getMe } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const userStore = useUserStore()

const form = reactive({ username: '', password: '' })

const registeredTip = computed(() => route.query.registered === '1')
const presetUsername = computed(() => {
  const u = route.query.username
  return typeof u === 'string' ? u : ''
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

function safeRedirect(value: unknown): string {
  if (typeof value !== 'string') return '/'
  // 只允许站内相对路径，防止 open-redirect（//evil.com）
  if (!value.startsWith('/') || value.startsWith('//')) return '/'
  return value
}

onMounted(() => {
  if (presetUsername.value) form.username = presetUsername.value
})

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const tokens = await login(form)
    userStore.setTokens(tokens.accessToken, tokens.refreshToken)
    try {
      userStore.setUserInfo(await getMe())
    } catch (meErr) {
      // 登录成功但 getMe 失败：清 tokens 让用户重登，避免半登录态
      await userStore.logout()
      ElMessage.error('登录信息已失效，请重新登录')
      return
    }
    ElMessage.success('登录成功')
    await router.replace(safeRedirect(route.query.redirect))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.login-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 420px;
}
.login-title {
  margin: 0;
  text-align: center;
  font-size: 20px;
}
.login-btn {
  width: 100%;
}
.footer {
  width: 100%;
  text-align: center;
  color: #909399;
}
.registered-tip {
  margin-bottom: 12px;
}
</style>
