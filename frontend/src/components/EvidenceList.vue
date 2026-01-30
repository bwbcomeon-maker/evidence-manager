<template>
  <div class="evidence-list">
    <van-pull-refresh
      v-model="refreshing"
      :disabled="!enablePullRefresh"
      @refresh="onRefresh"
    >
      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        @load="onLoad"
      >
        <van-cell-group inset>
          <van-cell
            v-for="item in records"
            :key="item.evidenceId"
            :title="item.title"
            :label="itemLabel(item)"
            is-link
            @click="goDetail(item)"
          >
            <template #value>
              <van-tag :type="forceVoidTag ? 'danger' : statusTagType(item.evidenceStatus || item.status)">
                {{ forceVoidTag ? '作废' : statusText(item.evidenceStatus || item.status) }}
              </van-tag>
            </template>
          </van-cell>
        </van-cell-group>
        <van-empty
          v-if="!loading && !refreshing && records.length === 0"
          :description="emptyDescription"
        />
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<script setup lang="ts">
import { toRef, onMounted } from 'vue'
import { Cell, CellGroup, List, PullRefresh, Empty, Tag } from 'vant'
import { useEvidenceList } from '@/composables/useEvidenceList'
import type { EvidenceGlobalListParams } from '@/api/evidence'

const props = withDefaults(
  defineProps<{
    /** 查询条件（uploader/status/fileCategory/recentDays/projectId/nameLike 等） */
    filterParams?: Partial<EvidenceGlobalListParams>
    /** 空态文案 */
    emptyDescription?: string
    /** 是否开启下拉刷新 */
    enablePullRefresh?: boolean
    /** 作废列表固定显示「作废」标签 */
    forceVoidTag?: boolean
  }>(),
  {
    filterParams: () => ({}),
    emptyDescription: '暂无数据',
    enablePullRefresh: true,
    forceVoidTag: false
  }
)

const filterParamsRef = toRef(props, 'filterParams')
const {
  records,
  loading,
  finished,
  refreshing,
  onLoad,
  onRefresh,
  itemLabel,
  statusTagType,
  statusText,
  goDetail
} = useEvidenceList(filterParamsRef)

onMounted(() => {
  if (records.value.length === 0) onLoad()
})
</script>

<style scoped>
.evidence-list {
  padding: 12px 0;
  min-height: 100%;
}
</style>
