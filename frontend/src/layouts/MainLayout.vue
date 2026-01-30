<template>
  <div class="main-layout">
    <van-nav-bar
      :title="(route.meta.title as string) || '证据管理'"
      :left-arrow="showBack"
      :left-text="showBack ? '返回' : ''"
      fixed
      placeholder
      @click-left="showBack ? onBack() : undefined"
    />
    <main class="layout-content">
      <router-view />
    </main>
    <van-tabbar v-if="showTabbar" route placeholder>
      <van-tabbar-item to="/home" icon="home-o">首页</van-tabbar-item>
      <van-tabbar-item to="/projects" icon="apps-o">项目</van-tabbar-item>
      <van-tabbar-item to="/evidence" icon="description">证据</van-tabbar-item>
      <van-tabbar-item to="/me" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

onMounted(() => {
  auth.fetchMe()
})

const showBack = computed(() => !!route.meta.showBack)
const showTabbar = computed(() => !!route.meta.showTabbar)

function onBack() {
  const fromProject = route.query.fromProject as string | undefined
  if (fromProject && route.path.startsWith('/evidence/detail/')) {
    // 用 replace 替换当前历史，避免「项目详情 → 返回」时又回到证据详情
    router.replace({ path: `/projects/${fromProject}`, query: { tab: 'evidence' } })
    return
  }
  // 项目详情页的返回一律回到项目列表，避免历史栈中有证据详情时返回到错误页
  if (route.path.match(/^\/projects\/[^/]+$/)) {
    router.replace('/projects')
    return
  }
  if (window.history.length > 1) {
    router.back()
  } else {
    const fallback = route.path.startsWith('/evidence') && route.path !== '/evidence' ? '/evidence' : '/home'
    router.replace(fallback)
  }
}
</script>

<style scoped>
.main-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f7f8fa;
}
.layout-content {
  flex: 1;
  padding-bottom: env(safe-area-inset-bottom, 0);
}
/* 返回文字强化：仅字重，不新增颜色/阴影 */
:deep(.van-nav-bar__text) {
  font-weight: 600;
}
</style>
