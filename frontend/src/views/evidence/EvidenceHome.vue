<template>
  <div class="evidence-home">
    <!-- 顶部固定：全局搜索 -->
    <div class="evidence-search-bar">
      <van-search
        v-model="searchKeyword"
        shape="round"
        placeholder="搜索项目、阶段、证据、上传人或备注..."
        background="#ffffff"
        @update:model-value="onSearchInput"
        @search="searchGlobalEvidence"
      />
    </div>

    <!-- 搜索提示区：仅在搜索框为空时展示（紧凑标签云） -->
    <div v-if="!searchKeyword.trim()" class="search-guide">
      <div class="search-guide__title">
        <span class="search-guide__icon">💡</span>
        <span class="search-guide__text">试试直接搜索：</span>
      </div>
      <div class="search-guide__tags">
        <van-tag type="primary" plain round>
          <span class="tag-prefix">项目 | </span>
          <span class="tag-keyword">XX医院信息化升级项目</span>
        </van-tag>
        <van-tag type="success" plain round>
          <span class="tag-prefix">阶段 | </span>
          <span class="tag-keyword">采购与设备到货阶段</span>
        </van-tag>
        <van-tag type="warning" plain round>
          <span class="tag-prefix">类型 | </span>
          <span class="tag-keyword">到货验收单用户签字</span>
        </van-tag>
        <van-tag type="warning" plain round>
          <span class="tag-prefix">类型 | </span>
          <span class="tag-keyword">设备到货现场照片</span>
        </van-tag>
        <van-tag type="danger" plain round>
          <span class="tag-prefix">人员 | </span>
          <span class="tag-keyword">张三</span>
        </van-tag>
        <van-tag type="primary" plain round>
          <span class="tag-prefix">备注 | </span>
          <span class="tag-keyword">质保期说明</span>
        </van-tag>
      </div>
    </div>

    <!-- 状态 A：搜索框为空时显示原有菜单 -->
    <template v-if="!searchKeyword.trim()">
      <main class="evidence-home-main">
        <div class="evidence-card">
          <van-cell title="按项目查看证据" icon="apps-o" is-link class="evidence-cell" @click="goToEvidence('/evidence/by-project')" />
          <van-cell title="我上传的证据" icon="user-o" is-link class="evidence-cell" @click="goToEvidence('/evidence/my')" />
          <van-cell title="最近上传的证据" icon="clock-o" is-link class="evidence-cell" @click="goToEvidence('/evidence/recent')" />
          <van-cell
            v-if="auth.canAccessVoidedEvidence"
            icon="warning-o"
            is-link
            class="evidence-cell"
            @click="goToEvidence('/evidence/voided')"
          >
            <template #title>
              <span>作废证据</span>
              <van-tag type="warning" size="medium" class="audit-tag">审计</van-tag>
            </template>
          </van-cell>
          <van-cell title="按文件类型查看" icon="description" is-link class="evidence-cell evidence-cell--last" @click="goToEvidence('/evidence/type')" />
        </div>
      </main>
    </template>

    <!-- 状态 B：有搜索词时显示搜索结果 -->
    <template v-else>
      <main class="evidence-home-main evidence-home-main--search">
        <van-loading v-if="isSearching" class="search-loading" vertical size="24">搜索中...</van-loading>
        <template v-else-if="searchResults.length > 0">
          <van-list class="search-result-list">
            <div
              v-for="item in searchResults"
              :key="item.evidenceId"
              class="search-result-card"
              @click="goToEvidenceDetail(item)"
            >
              <div class="search-result-card-body">
                <div class="search-result-title">{{ item.title || '未命名' }}</div>
                <div class="search-result-desc">
                  所属项目：{{ item.projectName || '—' }}
                </div>
                <div class="search-result-meta">
                  上传人：{{ item.createdByDisplayName || '—' }} | 时间：{{ formatCreatedAt(item.createdAt) }}
                </div>
              </div>
              <van-tag :type="searchResultStatusTagType(item.evidenceStatus)" size="medium" class="search-result-tag">
                {{ searchResultStatusText(item.evidenceStatus) }}
              </van-tag>
            </div>
          </van-list>
        </template>
        <van-empty v-else class="search-empty" description="暂无匹配证据" />
      </main>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Cell, Tag, Search, List, Loading, Empty } from 'vant'
import { useAuthStore } from '@/stores/auth'
import { getEvidenceGlobalSearch, type EvidenceSearchResultItem } from '@/api/evidence'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

/** 全局搜索关键词 */
const searchKeyword = ref('')
/** 搜索结果（来自 GET /api/evidence/global-search） */
const searchResults = ref<EvidenceSearchResultItem[]>([])
/** 搜索请求中 */
const isSearching = ref(false)

const SEARCH_DEBOUNCE_MS = 500
let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null

function onSearchInput() {
  const q = searchKeyword.value.trim()
  if (!q) {
    searchResults.value = []
    return
  }
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(() => {
    searchDebounceTimer = null
    searchGlobalEvidence()
  }, SEARCH_DEBOUNCE_MS)
}

/**
 * 全局证据搜索：防抖结束后或用户按搜索/回车时调用真实接口。
 */
async function searchGlobalEvidence() {
  const q = searchKeyword.value.trim()
  if (!q) {
    searchResults.value = []
    return
  }
  isSearching.value = true
  searchResults.value = []
  try {
    const res = (await getEvidenceGlobalSearch({
      keyword: q,
      page: 1,
      pageSize: 20
    })) as { code: number; data?: { records: EvidenceSearchResultItem[] } }
    if (res?.code === 0 && Array.isArray(res.data?.records)) {
      searchResults.value = res.data.records
    } else {
      searchResults.value = []
    }
  } catch {
    searchResults.value = []
  } finally {
    isSearching.value = false
  }
}

/** 示例标签点击：填充关键字并立即触发搜索 */
function onSearchExampleClick(keyword: string) {
  const q = keyword.trim()
  if (!q) return
  searchKeyword.value = q
  // 直接调用真实搜索，避免再经过防抖延时
  searchGlobalEvidence()
}

function searchResultStatusText(status?: string): string {
  if (!status) return '—'
  const m: Record<string, string> = {
    DRAFT: '待提交',
    SUBMITTED: '已提交',
    ARCHIVED: '已归档',
    INVALID: '作废'
  }
  return m[status] ?? status
}

function searchResultStatusTagType(status?: string): 'primary' | 'success' | 'danger' | 'default' {
  if (status === 'INVALID') return 'danger'
  if (status === 'SUBMITTED' || status === 'ARCHIVED') return 'primary'
  return 'default'
}

/** 格式化 createdAt 用于列表展示（后端可能为 ISO 字符串） */
function formatCreatedAt(createdAt: string): string {
  if (!createdAt) return '—'
  try {
    const d = new Date(createdAt)
    if (Number.isNaN(d.getTime())) return createdAt
    return d.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  } catch {
    return createdAt
  }
}

/**
 * 点击搜索结果：跳转到项目详情页证据 Tab，并传递 scrollStage/scrollType 供页面定位到具体证据项。
 * 同时带 from=evidence、returnKeyword，便于返回时回到本页并恢复搜索列表。
 */
function goToEvidenceDetail(item: EvidenceSearchResultItem) {
  const query: Record<string, string> = { tab: 'evidence', from: 'evidence' }
  if (item.stageCode?.trim() && item.evidenceTypeCode?.trim()) {
    query.scrollStage = item.stageCode.trim()
    query.scrollType = item.evidenceTypeCode.trim()
  }
  const kw = searchKeyword.value.trim()
  if (kw) query.returnKeyword = kw
  router.push({
    path: `/projects/${item.projectId}`,
    query
  })
}

/** 从 URL 恢复搜索词并拉取结果（返回时回到查询列表） */
watch(
  () => (route.path === '/evidence' ? (route.query.keyword as string | undefined) : undefined),
  (keyword) => {
    if (typeof keyword === 'string' && keyword.trim() !== '') {
      searchKeyword.value = keyword.trim()
      searchGlobalEvidence()
    }
  },
  { immediate: true }
)

/** 进入证据子页：带时间戳 push，避免历史栈中旧实例被复用导致样式/状态回退 */
function goToEvidence(path: string) {
  router.push({ path, query: { _t: String(Date.now()) } })
}
</script>

<style scoped>
.evidence-home {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: var(--bg-body);
}

/* 顶部固定搜索栏：白底，与项目列表页风格一致 */
.evidence-search-bar {
  position: sticky;
  top: 0;
  z-index: 10;
  padding: 12px 16px;
  background: var(--bg-body);
}
.evidence-search-bar :deep(.van-search) {
  padding: 0;
}
.evidence-search-bar :deep(.van-search__content) {
  background: #ffffff;
  border: 1px solid #dcdfe6;
  border-radius: 20px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

/* 搜索提示区块 */
.search-guide {
  margin: 4px 16px 8px;
  padding: 6px 8px;
  background: #f7f8fa;
  border-radius: 8px;
}

.search-guide__title {
  display: flex;
  align-items: center;
  margin-bottom: 4px;
  font-size: 12px;
  color: #969799;
}

.search-guide__icon {
  margin-right: 6px;
  font-size: 14px;
}

.search-guide__text {
  line-height: 1.5;
}

.search-guide__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-prefix {
  font-size: 11px;
  color: #969799;
}

.tag-keyword {
  font-size: 12px;
  font-weight: 500;
}

.evidence-home-main {
  flex: 1;
  padding-top: 0;
  padding-left: 16px;
  padding-right: 16px;
  padding-bottom: 16px;
}
.evidence-home-main--search {
  padding-top: 8px;
}

.evidence-card {
  background: var(--app-card);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-card-shadow);
  overflow: hidden;
}
.evidence-card :deep(.van-cell) {
  min-height: var(--app-tap-min-height);
  padding: 0 16px;
  display: flex;
  align-items: center;
}
.evidence-card :deep(.van-cell__left-icon),
.evidence-card :deep(.van-cell__title),
.evidence-card :deep(.van-cell__value),
.evidence-card :deep(.van-cell__right-icon) {
  display: flex;
  align-items: center;
}
.evidence-card :deep(.van-cell::after) {
  border-bottom: 1px solid #ebedf0;
  left: 16px;
  right: 0;
}
.evidence-cell.evidence-cell--last :deep(.van-cell::after),
.evidence-card :deep(.van-cell:last-child::after) {
  display: none;
}
.evidence-card :deep(.van-cell__left-icon) {
  margin-right: 12px;
  color: var(--app-primary);
}
.evidence-card :deep(.van-cell__right-icon) {
  color: #c8c9cc;
}
.audit-tag {
  margin-left: 8px;
  vertical-align: middle;
}

/* 状态 B：搜索结果 */
.search-loading {
  padding: 32px 0;
}
.search-result-list {
  padding: 0;
}
.search-result-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  margin-bottom: 10px;
  background: var(--app-card);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-card-shadow);
  cursor: pointer;
}
.search-result-card:active {
  opacity: 0.96;
}
.search-result-card-body {
  flex: 1;
  min-width: 0;
}
.search-result-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--van-gray-8);
  margin-bottom: 6px;
}
.search-result-desc,
.search-result-meta {
  font-size: 12px;
  color: var(--van-gray-6);
  line-height: 1.4;
}
.search-result-meta {
  margin-top: 2px;
}
.search-result-tag {
  flex-shrink: 0;
}
.search-empty {
  padding: 48px 0;
}
</style>
