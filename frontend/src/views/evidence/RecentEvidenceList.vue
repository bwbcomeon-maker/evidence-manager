<template>
  <div class="evidence-list-page">
    <van-dropdown-menu>
      <van-dropdown-item v-model="recentDays" :options="dayOptions" />
    </van-dropdown-menu>
    <EvidenceList
      :filter-params="{ recentDays }"
      empty-description="暂无最近上传的证据"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { DropdownMenu, DropdownItem } from 'vant'
import EvidenceList from '@/components/EvidenceList.vue'

const recentDays = ref(7)
const dayOptions = [
  { text: '最近 7 天', value: 7 },
  { text: '最近 30 天', value: 30 }
]
</script>

<style scoped>
.evidence-list-page {
  min-height: 100%;
}
</style>
