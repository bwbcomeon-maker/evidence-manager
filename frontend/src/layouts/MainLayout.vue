<template>
  <div class="main-layout">
    <van-nav-bar
      v-if="!hideLayoutNav"
      :title="(route.meta.title as string) || '证据管理'"
      :left-arrow="showBack"
      :left-text="showBack ? '返回' : ''"
      fixed
      placeholder
      @click-left="showBack ? onBack() : undefined"
    />
    <main class="layout-content" :class="{ 'layout-content--with-tabbar': showTabbar }">
      <router-view :key="route.fullPath" />
    </main>
    <van-tabbar v-if="showTabbar" v-model="activeTab" placeholder class="main-tabbar" @change="onTabChange">
      <van-tabbar-item name="projects" icon="apps-o">项目</van-tabbar-item>
      <van-tabbar-item name="evidence" icon="description">证据</van-tabbar-item>
      <van-tabbar-item name="me" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
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
/** 为 true 时由页面自绘导航栏，布局不渲染顶部栏（避免成员管理等页出现双栏） */
const hideLayoutNav = computed(() => !!route.meta.hideLayoutNav)

/** 由路由 path 推导当前 Tab（单一真相源），默认主页为「项目」 */
const activeTab = ref<'projects' | 'evidence' | 'me'>('projects')
watch(
  () => route.path,
  (path) => {
    if (path.startsWith('/projects')) activeTab.value = 'projects'
    else if (path.startsWith('/evidence')) activeTab.value = 'evidence'
    else if (path.startsWith('/me')) activeTab.value = 'me'
  },
  { immediate: true }
)
const tabPathMap: Record<string, string> = {
  projects: '/projects',
  evidence: '/evidence',
  me: '/me'
}
function onTabChange(nameOrIndex: string | number) {
  const tabNames = ['projects', 'evidence', 'me'] as const
  const name = typeof nameOrIndex === 'number' ? tabNames[nameOrIndex] : nameOrIndex
  const path = tabPathMap[name]
  if (!path) return
  activeTab.value = name
  if (route.path !== path && !route.path.startsWith(path + '/')) router.push(path)
}

function onBack() {
  const fromProject = route.query.fromProject as string | undefined
  if (fromProject && route.path.startsWith('/evidence/detail/')) {
    // 用 replace 替换当前历史，避免「项目详情 → 返回」时又回到证据详情
    router.replace({ path: `/projects/${fromProject}`, query: { tab: 'evidence' } })
    return
  }
  // 项目详情页：从「按项目查看证据」进入则返回到证据管理，否则回到项目列表
  if (route.path.match(/^\/projects\/[^/]+$/)) {
    if (route.query.from === 'evidence-by-project') {
      router.replace('/evidence/by-project')
    } else {
      router.replace('/projects')
    }
    return
  }
  if (window.history.length > 1) {
    router.back()
  } else {
    const fallback = route.path.startsWith('/evidence') && route.path !== '/evidence' ? '/evidence' : '/projects'
    router.replace(fallback)
  }
}
</script>

<style scoped>
.main-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--app-bg, #F5F7FA);
}
.layout-content {
  flex: 1;
  min-height: 0;
  padding-bottom: env(safe-area-inset-bottom, 0);
}
.layout-content--with-tabbar {
  padding-bottom: calc(var(--app-tabbar-height, 60px) + env(safe-area-inset-bottom, 0));
}
.main-tabbar {
  z-index: 10;
  min-height: var(--app-tabbar-height, 60px) !important;
  padding-bottom: env(safe-area-inset-bottom, 0);
}
.main-tabbar :deep(.van-tabbar-item) {
  min-height: 60px;
  padding: 8px 0;
}
/* 返回文字强化：仅字重，不新增颜色/阴影 */
:deep(.van-nav-bar__text) {
  font-weight: 600;
}
</style>
