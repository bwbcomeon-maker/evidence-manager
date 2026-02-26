<template>
  <router-view />
</template>

<script setup lang="ts">
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

</style>
