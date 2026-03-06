<template>
  <div class="main-layout">
    <!-- 有返回时：沉浸式透明导航栏 -->
    <header
      v-if="!hideLayoutNav && showBack"
      class="app-nav-bar"
      :class="{ 'app-nav-bar--scrolled': navScrolled }"
    >
      <div class="app-nav-bar-inner">
        <div class="nav-left" @click="onBack()">
          <svg class="nav-back-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M15 18l-6-6 6-6" />
          </svg>
        </div>
        <h1 class="nav-title">{{ (route.meta.title as string) || '证据管理' }}</h1>
        <div class="nav-right" aria-hidden="true" />
      </div>
    </header>
    <!-- 有返回时：占位，避免内容顶到导航栏下 -->
    <div v-if="!hideLayoutNav && showBack" class="app-nav-bar-placeholder" />
    <!-- 无返回时：沿用全宽 NavBar -->
    <van-nav-bar
      v-else-if="!hideLayoutNav"
      :title="(route.meta.title as string) || '证据管理'"
      fixed
      placeholder
    >
      <template #right>
        <span />
      </template>
    </van-nav-bar>
    <main
      class="layout-content"
      :class="{ 'layout-content--with-tabbar': showTabbar }"
      @scroll.passive="onContentScroll"
    >
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
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/** 沉浸式导航栏：滚动超过阈值时显示实体背景 */
const navScrolled = ref(false)
const SCROLL_THRESHOLD = 50

function onWindowScroll() {
  const y = window.scrollY ?? document.documentElement.scrollTop ?? 0
  navScrolled.value = y > SCROLL_THRESHOLD
}
function onContentScroll(e: Event) {
  const el = e.target as HTMLElement
  navScrolled.value = (el?.scrollTop ?? 0) > SCROLL_THRESHOLD
}

onMounted(() => {
  auth.fetchMe()
  window.addEventListener('scroll', onWindowScroll, { passive: true })
})
onUnmounted(() => {
  window.removeEventListener('scroll', onWindowScroll)
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
    // 返回到该项目详情页的「证据管理」Tab；保留 from、returnKeyword 以便再次返回时回到证据管理并恢复搜索列表
    const query: Record<string, string> = { tab: 'evidence' }
    const from = route.query.from as string | undefined
    if (from === 'evidence-by-project' || from === 'evidence') query.from = from
    const returnKeyword = route.query.returnKeyword as string | undefined
    if (returnKeyword) query.returnKeyword = returnKeyword
    const expandedStage = route.query.expandedStage as string | undefined
    if (expandedStage) query.expandedStage = expandedStage
    router.replace({ path: `/projects/${fromProject}`, query })
    return
  }
  // 项目详情页：从「按项目查看证据」进入则返回到证据管理，否则回到项目列表；若带 returnKeyword 则回到搜索列表
  if (route.path.match(/^\/projects\/[^/]+$/)) {
    const from = route.query.from as string | undefined
    const returnKeyword = route.query.returnKeyword as string | undefined
    if (from === 'evidence-by-project') {
      router.replace('/evidence/by-project')
    } else if (from === 'evidence') {
      // 从证据管理全局搜索进入时，返回证据管理并带上 keyword 以恢复搜索列表
      router.replace({ path: '/evidence', query: returnKeyword ? { keyword: returnKeyword } : {} })
    } else {
      router.replace('/projects')
    }
    return
  }
  // 从「按文件类型查看」的某 Tab 进入证据详情后返回：回到按文件类型查看页并恢复当时的 Tab（文档/视频/图片）
  const fromTab = route.query.fromTab as string | undefined
  if (route.path.startsWith('/evidence/detail/') && fromTab && ['image', 'document', 'video'].includes(fromTab)) {
    router.replace({ path: '/evidence/type', query: { tab: fromTab } })
    return
  }
  // 证据管理下所有子页（按项目查看、我上传的、最近上传、作废、按类型）返回：统一回到证据管理首页，避免历史栈残留导致再次进入时复用旧实例/旧样式
  if (route.path.startsWith('/evidence/') && route.path !== '/evidence' && !route.path.startsWith('/evidence/detail/')) {
    router.replace('/evidence')
    return
  }
  // 「我的」下的用户管理返回：统一回到我的页，避免历史栈残留
  if (route.path === '/admin/users') {
    router.replace('/me')
    return
  }
  // 批量分配项目返回：统一回到项目列表，避免历史栈残留
  if (route.path === '/batch-assign-projects') {
    router.replace('/projects')
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
/* 非激活项：图标与文字统一浅灰 #c8c9cc，突出当前选中蓝色 */
.main-tabbar :deep(.van-tabbar-item) {
  min-height: 60px;
  padding: 8px 0;
  color: #c8c9cc !important;
}
.main-tabbar :deep(.van-tabbar-item .van-icon),
.main-tabbar :deep(.van-tabbar-item__text) {
  color: #c8c9cc !important;
}
.main-tabbar :deep(.van-tabbar-item--active) {
  color: var(--van-tabbar-item-active-color, #007AFF) !important;
}
.main-tabbar :deep(.van-tabbar-item--active .van-icon),
.main-tabbar :deep(.van-tabbar-item--active .van-tabbar-item__text) {
  color: var(--van-tabbar-item-active-color, #007AFF) !important;
}
/* 无返回时的 NavBar：消除底部空隙，便于项目列表等页搜索框上移 */
.main-layout :deep(.van-nav-bar) {
  margin-bottom: 0;
}
/* 导航栏占位与主内容之间无额外空隙（避免灰色鸿沟 >30px） */
.main-layout :deep(.van-nav-bar__placeholder) {
  margin-bottom: 0;
}
:deep(.van-nav-bar__text) {
  font-weight: 600;
}

/* ---------- 沉浸式透明导航栏（透明 → 滚动后实体） ---------- */
.app-nav-bar {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  z-index: 999;
  padding-top: env(safe-area-inset-top);
  background: transparent;
  transition: background 0.2s ease, box-shadow 0.2s ease;
}
.app-nav-bar::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 1px;
  background: rgba(0, 0, 0, 0.06);
  pointer-events: none;
}
.app-nav-bar--scrolled {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}
.app-nav-bar--scrolled::after {
  background: rgba(0, 0, 0, 0.06);
}

.app-nav-bar-inner {
  display: flex;
  align-items: center;
  min-height: 48px;
  padding: 12px 16px 12px 8px;
}

/* 返回按钮：触控热区 ≥48×48px，左侧留足 padding，全屏/刘海屏易点 */
.nav-left {
  min-width: 56px;
  min-height: 48px;
  margin: -12px 0 -12px -8px;
  padding: 12px 12px 12px 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  color: #1A1A1A;
}
.nav-left:active {
  opacity: 0.7;
}

.nav-back-icon {
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  pointer-events: none;
}

.nav-title {
  flex: 1;
  margin: 0;
  padding: 0 12px;
  font-size: 17px;
  font-weight: 600;
  color: #1A1A1A;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nav-right {
  min-width: 48px;
  flex-shrink: 0;
}

.app-nav-bar-placeholder {
  height: calc(env(safe-area-inset-top) + 48px);
  flex-shrink: 0;
}
</style>
