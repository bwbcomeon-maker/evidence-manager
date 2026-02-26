<template>
  <div class="evidence-home">
    <main class="evidence-home-main">
      <div class="evidence-card">
        <van-cell title="按项目查看证据" icon="apps-o" is-link to="/evidence/by-project" class="evidence-cell" />
        <van-cell title="我上传的证据" icon="user-o" is-link to="/evidence/my" class="evidence-cell" />
        <van-cell title="最近上传的证据" icon="clock-o" is-link to="/evidence/recent" class="evidence-cell" />
        <van-cell
          v-if="auth.canAccessVoidedEvidence"
          icon="warning-o"
          is-link
          to="/evidence/voided"
          class="evidence-cell"
        >
          <template #title>
            <span>作废证据</span>
            <van-tag type="warning" size="medium" class="audit-tag">审计</van-tag>
          </template>
        </van-cell>
        <van-cell title="按文件类型查看" icon="description" is-link to="/evidence/type" class="evidence-cell evidence-cell--last" />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { Cell, CellGroup, Tag } from 'vant'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
</script>

<style scoped>
/* 页面主容器：纵向 Flex，充满视口，全局浅灰背景 */
.evidence-home {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: var(--bg-body);
}

/* 主要内容区：顶部呼吸空间 + 中间自适应拉伸 */
.evidence-home-main {
  flex: 1;
  padding-top: calc(env(safe-area-inset-top) + 64px);
  padding-left: 16px;
  padding-right: 16px;
  padding-bottom: 16px;
}

.evidence-card {
  background: var(--app-card);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-card-shadow);
  overflow: hidden;
}

.evidence-card :deep(.van-cell) {
  min-height: var(--app-tap-min-height);
  padding: 0 16px;
  display: flex;
  align-items: center;
}
.evidence-card :deep(.van-cell__left-icon),
.evidence-card :deep(.van-cell__title),
.evidence-card :deep(.van-cell__value),
.evidence-card :deep(.van-cell__right-icon) {
  display: flex;
  align-items: center;
}
.evidence-card :deep(.van-cell::after) {
  border-bottom: 1px solid #ebedf0;
  left: 16px;
  right: 0;
}
.evidence-cell.evidence-cell--last :deep(.van-cell::after),
.evidence-card :deep(.van-cell:last-child::after) {
  display: none;
}
.evidence-card :deep(.van-cell__left-icon) {
  margin-right: 12px;
  color: var(--app-primary);
}
.evidence-card :deep(.van-cell__right-icon) {
  color: #c8c9cc;
}

.audit-tag {
  margin-left: 8px;
  vertical-align: middle;
}
</style>
