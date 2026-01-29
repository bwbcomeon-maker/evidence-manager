<template>
  <div class="home-page">
    <van-grid :column-num="2" :border="false" clickable>
      <van-grid-item
        icon="apps-o"
        text="项目列表"
        to="/projects"
      />
      <van-grid-item
        icon="description"
        text="证据管理"
        to="/evidence"
      />
      <van-grid-item
        v-if="auth.isAdmin"
        icon="manager-o"
        text="用户管理"
        to="/admin/users"
      />
    </van-grid>
  </div>
</template>

<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
</script>

<style scoped>
.home-page {
  padding: 16px;
  min-height: 100vh;
}
</style>
