<template>
  <div class="evidence-list-page">
    <van-tabs v-model:active="activeTab" sticky>
      <van-tab title="图片" name="image" />
      <van-tab title="文档" name="document" />
      <van-tab title="视频" name="video" />
    </van-tabs>
    <EvidenceList
      :filter-params="{ fileCategory: activeTab }"
      :empty-description="emptyText"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Tabs, Tab } from 'vant'
import EvidenceList from '@/components/EvidenceList.vue'

const activeTab = ref<'image' | 'document' | 'video'>('image')
const emptyText = computed(() => {
  const t: Record<string, string> = {
    image: '暂无图片类证据',
    document: '暂无文档类证据',
    video: '暂无视频类证据'
  }
  return t[activeTab.value] ?? '暂无数据'
})
</script>

<style scoped>
.evidence-list-page {
  min-height: 100%;
}
</style>
