<template>
  <div class="evidence-list-page">
    <EvidenceList
      :filter-params="{ uploader: 'me' }"
      empty-description="暂无我上传的证据"
    />
  </div>
</template>

<script setup lang="ts">
import EvidenceList from '@/components/EvidenceList.vue'
</script>

<style scoped>
.evidence-list-page {
  min-height: 100%;
}
</style>
