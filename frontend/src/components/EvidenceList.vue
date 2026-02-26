<template>
  <div class="evidence-list" :class="{ 'evidence-list--image-gallery': displayMode === 'image' }">
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
        <!-- 按文件类型：图片 Tab - 响应式网格照片库 -->
        <div v-if="displayMode === 'image'" class="evidence-image-gallery">
          <div
            v-for="item in records"
            :key="item.evidenceId"
            class="evidence-image-card"
            @click="goToDetail(item)"
          >
            <div class="evidence-image-banner">
              <img
                v-if="isImageType(item.contentType) && item.latestVersion"
                :src="`/api/evidence/versions/${item.latestVersion.versionId}/download`"
                :alt="item.title"
                class="evidence-image-img"
              />
              <div v-else class="evidence-image-placeholder" :style="{ background: getFileIconConfig(getEvidenceFileName(item)).bg }">
                <van-icon :name="getFileIconConfig(getEvidenceFileName(item)).icon" size="32" :color="getFileIconConfig(getEvidenceFileName(item)).color" />
              </div>
            </div>
            <div class="evidence-image-footer">
              <span class="evidence-image-filename">{{ item.latestVersion?.originalFilename || item.title || '未命名' }}</span>
              <span class="evidence-card-badge badge--small" :class="badgeClass(item)">{{ forceVoidTag ? '作废' : statusText(effectiveStatus(item)) }}</span>
            </div>
          </div>
        </div>
        <!-- 按文件类型：视频 Tab 网格 + 播放图标 -->
        <div v-else-if="displayMode === 'video'" class="evidence-grid evidence-grid--video">
          <div
            v-for="item in records"
            :key="item.evidenceId"
            class="evidence-grid-card evidence-grid-card--video"
            @click="goToDetail(item)"
          >
            <div class="evidence-grid-preview evidence-grid-preview--video">
              <div class="evidence-grid-placeholder" :style="{ background: getFileIconConfig(getEvidenceFileName(item)).bg }">
                <van-icon :name="getFileIconConfig(getEvidenceFileName(item)).icon" size="32" :color="getFileIconConfig(getEvidenceFileName(item)).color" />
              </div>
              <div class="evidence-grid-play">
                <van-icon name="play-circle-o" size="48" color="rgba(255,255,255,0.95)" />
              </div>
            </div>
            <div class="evidence-grid-info">
              <span class="evidence-grid-name">{{ item.latestVersion?.originalFilename || item.title || '未命名' }}</span>
              <span class="evidence-card-badge badge--small" :class="badgeClass(item)">{{ forceVoidTag ? '作废' : statusText(effectiveStatus(item)) }}</span>
            </div>
          </div>
        </div>
        <!-- 默认 / 文档：卡片列表（左侧图标 + 标题 + 副标题 + 胶囊标签） -->
        <template v-else>
          <div
            v-for="item in records"
            :key="item.evidenceId"
            class="evidence-card"
            :class="{ 'evidence-card--voided': voidedStyle }"
            @click="goToDetail(item)"
          >
            <div class="evidence-card-icon-wrap" :style="{ background: getFileIconConfig(getEvidenceFileName(item)).bg }">
              <div class="evidence-card-icon-inner" :style="{ color: getFileIconConfig(getEvidenceFileName(item)).color }">
                <van-icon
                  :name="getFileIconConfig(getEvidenceFileName(item)).icon"
                  size="20"
                  class="evidence-card-icon"
                />
                <span class="evidence-card-icon-label">{{ getFileIconConfig(getEvidenceFileName(item)).label }}</span>
              </div>
              <span v-if="voidedStyle" class="evidence-card-icon-voided" aria-hidden="true">×</span>
            </div>
            <div class="evidence-card-body">
              <div class="evidence-card-header">
                <h3 class="evidence-card-title">{{ item.title || '未命名' }}</h3>
                <span
                  class="evidence-card-badge"
                  :class="badgeClass(item)"
                >
                  {{ forceVoidTag ? '作废' : statusText(effectiveStatus(item)) }}
                </span>
              </div>
              <div class="evidence-card-subtitle">{{ itemLabel(item) }}</div>
            </div>
            <van-icon name="arrow" class="evidence-card-arrow" />
          </div>
        </template>
        <van-empty
          v-if="!loading && !refreshing && records.length === 0"
          :description="emptyDescription"
          image-size="80"
        />
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<script setup lang="ts">
import { toRef, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { List, PullRefresh, Empty, Icon } from 'vant'
import { useEvidenceList } from '@/composables/useEvidenceList'
import { getFileIconConfig, getEvidenceFileName, isImageType } from '@/utils/fileIcon'
import type { EvidenceGlobalListParams, EvidenceListItem } from '@/api/evidence'

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
    /** 作废列表视觉弱化：标题变淡、图标角标 */
    voidedStyle?: boolean
    /** 展示模式：list/document 卡片列表，image 图片网格，video 视频网格 */
    displayMode?: 'list' | 'document' | 'image' | 'video'
    /** 从证据管理子页进入：详情页仅保留预览、下载 */
    fromEvidenceModule?: boolean
    /** 来自「按文件类型查看」时的当前 Tab（image/document/video），返回时恢复该 Tab */
    fromTab?: string
  }>(),
  {
    filterParams: () => ({}),
    emptyDescription: '暂无数据',
    enablePullRefresh: true,
    forceVoidTag: false,
    voidedStyle: false,
    displayMode: 'list',
    fromEvidenceModule: false,
    fromTab: undefined
  }
)

const router = useRouter()
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
  getEffectiveEvidenceStatus,
  goDetail
} = useEvidenceList(filterParamsRef)
const effectiveStatus = (item: EvidenceListItem) => getEffectiveEvidenceStatus(item) ?? ''

/** 跳转详情：从证据管理子页进入时带 from=evidence；若来自按文件类型查看则带 fromTab 便于返回时恢复 Tab */
function goToDetail(item: EvidenceListItem) {
  if (props.fromEvidenceModule) {
    const query: Record<string, string> = { from: 'evidence' }
    if (props.fromTab) query.fromTab = props.fromTab
    router.push({
      path: `/evidence/detail/${item.evidenceId}`,
      query,
      state: { evidence: item }
    } as any)
  } else {
    goDetail(item)
  }
}

/** 胶囊标签样式类：与「按项目查看证据」一致 */
function badgeClass(item: EvidenceListItem) {
  if (props.forceVoidTag) return 'badge--void'
  const t = statusTagType(effectiveStatus(item))
  return {
    'badge--draft': t === 'default',
    'badge--submitted': t === 'primary',
    'badge--archived': t === 'success',
    'badge--void': t === 'danger'
  }
}

onMounted(() => {
  if (records.value.length === 0) onLoad()
})
</script>

<style scoped>
.evidence-list {
  padding: 16px;
  min-height: 100%;
  background: var(--app-bg);
}
.evidence-list--image-gallery {
  padding: 0;
}

/* 卡片化：独立圆角卡片 + 垂直间距，与「按项目查看证据」一致 */
.evidence-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--app-card);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-card-shadow);
  padding: 14px 16px;
  margin-bottom: 12px;
  min-height: var(--app-tap-min-height);
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.evidence-card:active {
  opacity: 0.96;
}

.evidence-card--voided .evidence-card-title {
  color: #b0b0b0;
  font-weight: 500;
}

/* 左侧文件类型图标：容器 48x48，内层图标 + 格式文字（Flex 纵向居中） */
.evidence-card-icon-wrap {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}
.evidence-card-icon-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  min-width: 0;
}
.evidence-card-icon-inner .evidence-card-icon {
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}
.evidence-card-icon-label {
  font-size: 10px;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.evidence-card-icon-voided {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: rgba(255, 59, 48, 0.9);
  color: #fff;
  font-size: 12px;
  line-height: 16px;
  text-align: center;
  z-index: 2;
}

.evidence-card-body {
  flex: 1;
  min-width: 0;
}
.evidence-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}
.evidence-card-title {
  flex: 1;
  font-size: 17px;
  font-weight: 600;
  color: #323233;
  margin: 0;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.evidence-card-subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: var(--app-text-secondary, #8E8E93);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 胶囊标签：与按项目查看证据一致 */
.evidence-card-badge {
  flex-shrink: 0;
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}
.badge--draft {
  background: #ebedf0;
  color: #969799;
}
.badge--submitted {
  background: rgba(0, 122, 255, 0.12);
  color: var(--app-primary);
}
.badge--archived {
  background: rgba(7, 193, 96, 0.12);
  color: #07c160;
}
.badge--void {
  background: rgba(255, 59, 48, 0.12);
  color: #ff3b30;
}

.evidence-card-arrow {
  flex-shrink: 0;
  color: #c8c9cc;
  font-size: 16px;
}

/* ---------- 按文件类型：图片 Tab - 响应式网格照片库 ---------- */
.evidence-image-gallery {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
  padding: 20px;
  padding-bottom: 24px;
  min-height: 100%;
  background: var(--app-bg);
}
@media (max-width: 560px) {
  .evidence-image-gallery {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
    padding: 12px;
    padding-bottom: 20px;
  }
}
.evidence-image-card {
  background: var(--app-card);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-card-shadow);
  overflow: hidden;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.evidence-image-card:active {
  opacity: 0.96;
}
/* 图片区域：固定宽高比 4:3，顶部圆角，底部直角 */
.evidence-image-banner {
  position: relative;
  width: 100%;
  aspect-ratio: 4 / 3;
  background: #f0f0f0;
  overflow: hidden;
  border-radius: var(--app-card-radius) var(--app-card-radius) 0 0;
}
.evidence-image-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.evidence-image-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
/* 信息区：文件名 + 状态标签，单行省略 */
.evidence-image-footer {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.evidence-image-filename {
  font-size: 14px;
  color: #323233;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.evidence-image-footer .evidence-card-badge {
  align-self: flex-start;
}

/* ---------- 按文件类型：视频 Tab 网格 ---------- */
.evidence-grid {
  display: grid;
  gap: 12px;
  padding: 16px;
  padding-bottom: 24px;
}
.evidence-grid--video {
  grid-template-columns: repeat(2, 1fr);
}
.evidence-grid-card {
  background: var(--app-card);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-card-shadow);
  overflow: hidden;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.evidence-grid-card:active {
  opacity: 0.96;
}
.evidence-grid-preview {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  background: #f0f0f0;
  overflow: hidden;
}
.evidence-grid-preview--video {
  position: relative;
}
.evidence-grid-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.evidence-grid-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.evidence-grid-play {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.2);
  pointer-events: none;
}
.evidence-grid-info {
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.evidence-grid-name {
  font-size: 13px;
  font-weight: 600;
  color: #323233;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.evidence-grid-info .evidence-card-badge {
  align-self: flex-start;
}
.badge--small {
  font-size: 11px;
  padding: 2px 8px;
}
</style>
