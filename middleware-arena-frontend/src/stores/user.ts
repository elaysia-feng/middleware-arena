import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

interface UserInfo {
  id: string
  username: string
  email: string
}

export const useUserStore = defineStore('user', () => {
  const accessToken = ref<string>('')
  const refreshToken = ref<string>('')
  const userInfo = ref<UserInfo | null>(null)

  const isLogin = computed(() => !!accessToken.value)

  // TODO[双 token]：setTokens —— 登录成功后存储 accessToken + refreshToken
  function setTokens(access: string, refresh: string) {
    accessToken.value = access
    refreshToken.value = refresh
  }

  // TODO[双 token]：logout —— 清除 token + 用户信息，可能需调后端注销接口
  function logout() {
    accessToken.value = ''
    refreshToken.value = ''
    userInfo.value = null
  }

  return {
    accessToken,
    refreshToken,
    userInfo,
    isLogin,
    setTokens,
    logout,
  }
})
