<template>
  <div v-if="allowed" class="evidence-list-page">
    <van-notice-bar v-if="isAuditorOnly" left-icon="info-o" color="#1989fa" background="#ecf9ff">
      可查看不等于可作废/可操作，操作按钮以项目权限为准。
    </van-notice-bar>
    <EvidenceList
      :filter-params="{ status: 'VOIDED' }"
      empty-description="暂无作废证据"
      force-void-tag
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import EvidenceList from '@/components/EvidenceList.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const allowed = ref(true)
/** AUDITOR 仅只读入口，无操作位 */
const isAuditorOnly = computed(() => {
  const code = auth.currentUser?.roleCode
  return code === 'AUDITOR' || code === 'PROJECT_AUDITOR'
})

onMounted(() => {
  if (!auth.canAccessVoidedEvidence) {
    showToast('无权限访问')
    router.replace('/evidence')
    allowed.value = false
  }
})
</script>

<style scoped>
.evidence-list-page {
  min-height: 100%;
}
</style>
