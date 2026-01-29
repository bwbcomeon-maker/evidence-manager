import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getMe, logout as apiLogout, type AuthUser } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const currentUser = ref<AuthUser | null>(null)

  const isLoggedIn = computed(() => !!currentUser.value)
  const isAdmin = computed(() => currentUser.value?.roleCode === 'SYSTEM_ADMIN')

  /** 是否可访问「作废证据」入口：SYSTEM_ADMIN / PROJECT_OWNER / PROJECT_AUDITOR */
  const canAccessVoidedEvidence = computed(() => {
    const code = currentUser.value?.roleCode
    return !!code && ['SYSTEM_ADMIN', 'PROJECT_OWNER', 'PROJECT_AUDITOR'].includes(code)
  })

  async function fetchMe() {
    try {
      const res = await getMe() as { code: number; data?: AuthUser }
      if (res?.code === 0 && res.data) {
        currentUser.value = res.data
        return res.data
      }
    } catch {
      currentUser.value = null
    }
    return null
  }

  function setUser(user: AuthUser | null) {
    currentUser.value = user
  }

  function clearUser() {
    currentUser.value = null
  }

  async function logout() {
    try {
      await apiLogout()
    } finally {
      clearUser()
    }
  }

  return {
    currentUser,
    isLoggedIn,
    isAdmin,
    canAccessVoidedEvidence,
    fetchMe,
    setUser,
    clearUser,
    logout
  }
})
