<template>
  <div class="evidence-home">
    <van-cell-group inset>
      <van-cell title="按项目查看证据" is-link to="/projects" />
      <van-cell title="我上传的证据" is-link to="/evidence/my" />
      <van-cell title="最近上传的证据" is-link to="/evidence/recent" />
      <van-cell
        v-if="auth.canAccessVoidedEvidence"
        is-link
        to="/evidence/voided"
      >
        <template #title>
          <span>作废证据</span>
          <van-tag type="warning" size="medium" class="audit-tag">审计</van-tag>
        </template>
      </van-cell>
      <van-cell title="按文件类型查看" is-link to="/evidence/type" />
    </van-cell-group>
  </div>
</template>

<script setup lang="ts">
import { Cell, CellGroup, Tag } from 'vant'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
</script>

<style scoped>
.evidence-home {
  padding: 16px 0;
  min-height: 100%;
}
.audit-tag {
  margin-left: 8px;
  vertical-align: middle;
}
</style>
