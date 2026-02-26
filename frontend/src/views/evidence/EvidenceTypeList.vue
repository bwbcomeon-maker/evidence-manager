<template>
  <div class="evidence-type-page">
    <van-tabs v-model:active="activeTab" sticky>
      <van-tab title="图片" name="image" />
      <van-tab title="文档" name="document" />
      <van-tab title="视频" name="video" />
    </van-tabs>
    <EvidenceList
      :filter-params="{ fileCategory: activeTab }"
      :empty-description="emptyText"
      :display-mode="activeTab"
      from-evidence-module
      :from-tab="activeTab"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Tabs, Tab } from 'vant'
import EvidenceList from '@/components/EvidenceList.vue'

const route = useRoute()
const VALID_TABS = ['image', 'document', 'video'] as const
function tabFromQuery(q: unknown): 'image' | 'document' | 'video' {
  return VALID_TABS.includes(q as any) ? (q as typeof VALID_TABS[number]) : 'image'
}
const activeTab = ref<'image' | 'document' | 'video'>(tabFromQuery(route.query.tab))
watch(() => route.query.tab, (tab) => {
  activeTab.value = tabFromQuery(tab)
})
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
.evidence-type-page {
  min-height: 100%;
  background: var(--app-bg);
}
</style>
