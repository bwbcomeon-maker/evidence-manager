<template>
  <div class="me-page">
    <van-cell-group inset>
      <van-cell v-if="auth.currentUser" :title="auth.currentUser.realName || auth.currentUser.username" :label="`@${auth.currentUser.username} · ${roleLabel(auth.currentUser.roleCode)}`" />
      <van-cell v-if="auth.isAdmin" title="用户管理" is-link to="/admin/users" />
      <van-cell title="退出登录" is-link @click="onLogout" />
    </van-cell-group>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const roleLabels: Record<string, string> = {
  SYSTEM_ADMIN: '系统管理员',
  PROJECT_OWNER: '项目负责人',
  PROJECT_EDITOR: '项目编辑',
  PROJECT_VIEWER: '项目查看',
  PROJECT_AUDITOR: '项目审计'
}

function roleLabel(code: string) {
  return roleLabels[code] ?? code
}

async function onLogout() {
  await auth.logout()
  router.replace('/login')
}
</script>

<style scoped>
.me-page {
  padding: 16px 0;
  min-height: 100vh;
}
</style>
