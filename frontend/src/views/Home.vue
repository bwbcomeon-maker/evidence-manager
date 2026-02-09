<template>
  <div class="home-page">
    <van-grid :column-num="2" :border="false" clickable>
      <van-grid-item
        class="home-entry home-entry--projects"
        icon="apps-o"
        text="项目列表"
        @click="go('/projects')"
      />
      <van-grid-item
        class="home-entry home-entry--evidence"
        icon="description"
        text="证据管理"
        @click="go('/evidence')"
      />
      <van-grid-item
        v-if="canBatchAssign"
        class="home-entry home-entry--batch-assign"
        icon="friends-o"
        text="批量分配项目"
        @click="go('/batch-assign-projects')"
      />
      <van-grid-item
        v-if="auth.isAdmin"
        class="home-entry home-entry--admin"
        icon="manager-o"
        text="用户管理"
        @click="go('/admin/users')"
      />
    </van-grid>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

/** 仅系统管理员、PMO 可批量分配项目 */
const canBatchAssign = computed(() => {
  const code = auth.currentUser?.roleCode
  return code === 'SYSTEM_ADMIN' || code === 'PMO'
})

function go(path: string) {
  router.push(path)
}
</script>

<style scoped>
.home-page {
  padding: 16px;
  min-height: 100vh;
  box-sizing: border-box;
}

/* 锤子式：留白+对齐+内容权重，无装饰线/阴影；强调在功能块内部 */
.home-entry :deep(.van-grid-item__content) {
  box-shadow: none;
}

.home-entry--evidence :deep(.van-grid-item__text),
.home-entry--projects :deep(.van-grid-item__text),
.home-entry--batch-assign :deep(.van-grid-item__text),
.home-entry--admin :deep(.van-grid-item__text) {
  font-weight: 600;
  color: var(--van-text-color, #323233);
}
</style>
