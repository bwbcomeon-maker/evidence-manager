<template>
  <router-view />
  <!-- 移动端扫码在浏览器内打开时：点击一次进入全屏，隐藏地址栏/底栏 -->
  <div
    v-if="showFullscreenHint"
    class="fullscreen-hint"
    @click="enterFullscreen"
  >
    点击进入全屏
  </div>
  <!-- 添加到主屏入口：仅在浏览器内打开且未安装时显示 -->
  <div
    v-if="showAddToHomeButton"
    class="add-to-home-btn"
    @click="openAddToHome"
  >
    添加到主屏
  </div>
  <!-- iOS / 无安装接口时：显示操作说明 -->
  <van-dialog
    v-model:show="showAddToHomeTip"
    title="添加到主屏幕"
    :show-confirm-button="true"
    confirm-button-text="知道了"
    class="add-to-home-dialog"
  >
    <div class="add-to-home-tip">
      <template v-if="isIOS()">
        <p>在 Safari 中点击底部<strong>「分享」</strong>按钮，</p>
        <p>选择<strong>「添加到主屏幕」</strong>即可。</p>
      </template>
      <template v-else>
        <p>请使用浏览器菜单中的<strong>「添加到主屏幕」</strong>或<strong>「安装应用」</strong>功能。</p>
      </template>
    </div>
  </van-dialog>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

const showFullscreenHint = ref(false)
const showAddToHomeButton = ref(false)
const showAddToHomeTip = ref(false)
interface InstallPromptEvent extends Event {
  prompt(): Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}
let installPrompt: InstallPromptEvent | null = null

function isIOS(): boolean {
  return /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
}

function isMobile(): boolean {
  return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent) || 'ontouchstart' in window
}

function isStandalone(): boolean {
  return (
    (window as Window & { standalone?: boolean }).standalone === true ||
    window.matchMedia('(display-mode: standalone)').matches ||
    (window.navigator as Navigator & { standalone?: boolean }).standalone === true
  )
}

function isFullscreen(): boolean {
  return !!(
    document.fullscreenElement ||
    (document as Document & { webkitFullscreenElement?: Element }).webkitFullscreenElement
  )
}

function enterFullscreen(): void {
  const el = document.documentElement
  const req =
    el.requestFullscreen ||
    (el as HTMLElement & { webkitRequestFullscreen?: () => Promise<void> }).webkitRequestFullscreen
  if (req) {
    req.call(el).then(() => {
      showFullscreenHint.value = false
    }).catch(() => {})
  } else {
    showFullscreenHint.value = false
  }
}

onMounted(() => {
  if (isMobile() && !isStandalone()) {
    showAddToHomeButton.value = true
    if (!isFullscreen()) showFullscreenHint.value = true
    document.addEventListener('fullscreenchange', onFullscreenChange)
    document.addEventListener('webkitfullscreenchange', onFullscreenChange)
  }
  window.addEventListener('beforeinstallprompt', onBeforeInstallPrompt)
})

onUnmounted(() => {
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  document.removeEventListener('webkitfullscreenchange', onFullscreenChange)
  window.removeEventListener('beforeinstallprompt', onBeforeInstallPrompt)
})

function onFullscreenChange(): void {
  if (!isFullscreen()) showFullscreenHint.value = true
  else showFullscreenHint.value = false
}

function openAddToHome(): void {
  if (installPrompt) {
    installPrompt.prompt()
    installPrompt.userChoice.then((choice) => {
      if (choice.outcome === 'accepted') installPrompt = null
    }).catch(() => {})
  } else {
    showAddToHomeTip.value = true
  }
}

function onBeforeInstallPrompt(e: Event): void {
  e.preventDefault()
  installPrompt = e as InstallPromptEvent
}
</script>

<style>
/* 全局移动端设计变量（现代 iOS 蓝 + 浅灰背景 + 白卡片） */
:root {
  --app-primary: #007AFF;
  --app-bg: #F5F7FA;
  --app-card: #FFFFFF;
  --app-tabbar-height: 60px;
  --app-tap-min-height: 44px;
  --app-card-radius: 12px;
  --app-card-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  --app-text-secondary: #8E8E93;
  /* 别名：与现有 --app-* 保持一致，便于表单/详情页引用 */
  --primary-color: #007AFF;
  --bg-body: #F5F7FA;
  --bg-card: #FFFFFF;
  --text-main: #323233;
  --text-placeholder: #C7C7CC;
  /* 与 Vant 主题一致 */
  --van-primary-color: #007AFF;
  --van-button-primary-background: #007AFF;
  --van-button-primary-border-color: #007AFF;
  --van-tabbar-item-active-color: #007AFF;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

#app {
  min-height: 100vh;
  background-color: var(--app-bg);
}

/* 可点击按钮、列表项最小点击区域 */
.van-button,
.van-cell {
  min-height: var(--app-tap-min-height);
}

/* ---------- 全局表单与交互控件规范 ---------- */
/* 表单项：输入框/下拉等高度统一，无原生边框，下划线或白卡片包裹 */
.van-field--min-height .van-field__body input,
.van-field--min-height .van-field__body textarea,
.van-field__control {
  min-height: 44px;
}
.van-field__control::placeholder {
  color: var(--text-placeholder);
}
.van-field__label {
  color: var(--text-main);
}
.van-cell-group--inset.form-card-style {
  margin: 0;
  border-radius: var(--app-card-radius);
  overflow: hidden;
  box-shadow: var(--app-card-shadow);
}
.van-cell-group.form-card-style .van-cell {
  background: var(--bg-card);
}
/* 聚焦时轻微视觉反馈 */
.van-field__body input:focus,
.van-field__body textarea:focus {
  outline: none;
}
.van-cell-group.form-card-style .van-field:focus-within {
  background: rgba(0, 122, 255, 0.02);
}

/* 底部固定操作区：提交按钮吸底、全宽、大圆角、主色 */
.fixed-submit-wrap {
  position: sticky;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 12px 16px;
  padding-bottom: calc(12px + env(safe-area-inset-bottom, 0));
  background: var(--bg-card);
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.06);
  z-index: 5;
}
.fixed-submit-wrap .van-button {
  width: 100%;
  min-height: 48px;
  border-radius: 24px;
  background: var(--primary-color) !important;
  border: none !important;
  font-weight: 600;
}

/* 移动端全屏提示条：点击后进入全屏，隐藏地址栏/底栏 */
.fullscreen-hint {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 9999;
  padding: 10px 16px;
  padding-top: calc(10px + env(safe-area-inset-top, 0));
  background: var(--app-primary);
  color: #fff;
  font-size: 14px;
  text-align: center;
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  -webkit-tap-highlight-color: transparent;
}

/* 添加到主屏：右下角悬浮按钮 */
.add-to-home-btn {
  position: fixed;
  right: 16px;
  bottom: calc(24px + env(safe-area-inset-bottom, 0));
  z-index: 9998;
  padding: 10px 16px;
  background: var(--app-primary);
  color: #fff;
  font-size: 13px;
  border-radius: 22px;
  box-shadow: 0 2px 12px rgba(0, 122, 255, 0.4);
  -webkit-tap-highlight-color: transparent;
}

.add-to-home-dialog .add-to-home-tip {
  padding: 8px 0 16px;
  color: var(--text-main);
  font-size: 15px;
  line-height: 1.6;
}
.add-to-home-dialog .add-to-home-tip strong {
  color: var(--app-primary);
}
</style>
