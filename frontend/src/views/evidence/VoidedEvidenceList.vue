<template>
  <div v-if="allowed" class="evidence-list-page">
    <EvidenceList
      :filter-params="{ status: 'VOIDED' }"
      empty-description="暂无作废证据"
      force-void-tag
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import EvidenceList from '@/components/EvidenceList.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const allowed = ref(true)

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
