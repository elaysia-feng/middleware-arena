import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { logout as apiLogout } from '@/api/auth'

interface UserInfo {
  id: number
  username: string
  nickname: string
  tier: string
  effectiveTier: string
  vipExpireAt?: string
}

const ACCESS_TOKEN_KEY = 'ma.accessToken'
const REFRESH_TOKEN_KEY = 'ma.refreshToken'

export const useUserStore = defineStore('user', () => {
  const accessToken = ref(localStorage.getItem(ACCESS_TOKEN_KEY) ?? '')
  const refreshToken = ref(localStorage.getItem(REFRESH_TOKEN_KEY) ?? '')
  const userInfo = ref<UserInfo | null>(null)

  const isLogin = computed(() => !!accessToken.value)

  // source of truth：effectiveTier（后端 AuthServiceImpl 一律返回，VIP 过期就是 FREE）
  const isVipActive = computed(
    () => userInfo.value?.effectiveTier === 'VIP' || userInfo.value?.effectiveTier === 'SVIP',
  )

  function setTokens(access: string, refresh: string) {
    accessToken.value = access
    refreshToken.value = refresh
    localStorage.setItem(ACCESS_TOKEN_KEY, access)
    localStorage.setItem(REFRESH_TOKEN_KEY, refresh)
  }

  function setUserInfo(info: UserInfo) {
    userInfo.value = info
  }

  // 真正调用 /auth/logout，删 Redis 里 refreshToken，避免被盗用到 TTL 过期
  async function logout() {
    const rt = refreshToken.value
    try {
      if (rt) await apiLogout(rt)
    } catch {
      // 即使后端失败也继续清本地
    } finally {
      accessToken.value = ''
      refreshToken.value = ''
      userInfo.value = null
      localStorage.removeItem(ACCESS_TOKEN_KEY)
      localStorage.removeItem(REFRESH_TOKEN_KEY)
    }
  }

  return {
    accessToken,
    refreshToken,
    userInfo,
    isLogin,
    isVipActive,
    setTokens,
    setUserInfo,
    logout,
  }
})
