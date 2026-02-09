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
      <van-tabbar-item name="home" icon="home-o">首页</van-tabbar-item>
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

/** 由路由 path 推导当前 Tab（单一真相源），使 /evidence、/evidence/by-project 等均高亮「证据」 */
const activeTab = ref<'home' | 'projects' | 'evidence' | 'me'>('home')
watch(
  () => route.path,
  (path) => {
    if (path.startsWith('/projects')) activeTab.value = 'projects'
    else if (path.startsWith('/evidence')) activeTab.value = 'evidence'
    else if (path.startsWith('/me')) activeTab.value = 'me'
    else activeTab.value = 'home'
  },
  { immediate: true }
)
const tabPathMap: Record<string, string> = {
  home: '/home',
  projects: '/projects',
  evidence: '/evidence',
  me: '/me'
}
function onTabChange(nameOrIndex: string | number) {
  const tabNames = ['home', 'projects', 'evidence', 'me'] as const
  const name = typeof nameOrIndex === 'number' ? tabNames[nameOrIndex] : nameOrIndex
  const path = tabPathMap[name]
  if (!path) return
  activeTab.value = name
  if (route.path !== path) router.push(path)
}

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
  min-height: 0;
  padding-bottom: env(safe-area-inset-bottom, 0);
}
/* 有 Tabbar 时预留底部高度，避免空态/列表内容覆盖 Tabbar 导致点击被吞 */
.layout-content--with-tabbar {
  padding-bottom: calc(var(--van-tabbar-height, 50px) + env(safe-area-inset-bottom, 0));
}
/* 保证 Tabbar 始终在内容层之上，避免空态区域遮挡（Vant 默认 z-index 仅 1） */
.main-tabbar {
  z-index: 10;
}
/* 返回文字强化：仅字重，不新增颜色/阴影 */
:deep(.van-nav-bar__text) {
  font-weight: 600;
}
</style>
