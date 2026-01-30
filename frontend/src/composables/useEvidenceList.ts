import { ref, watch, nextTick, type Ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { listEvidence, type EvidenceListItem, type EvidenceGlobalListParams, type EvidenceListResponse } from '@/api/evidence'
import { formatDateTime } from '@/utils/format'
import { getEffectiveEvidenceStatus, mapStatusToText, statusTagType as utilStatusTagType } from '@/utils/evidenceStatus'

const PAGE_SIZE = 20

export interface UseEvidenceListOptions {
  pageSize?: number
}

/**
 * 证据列表逻辑复用：分页加载、下拉刷新、空态、错误提示、时间格式化、跳转详情
 * @param filterParamsRef 查询条件（不含 page/pageSize），变化时会重置并重新加载
 */
export function useEvidenceList(
  filterParamsRef: Ref<Partial<EvidenceGlobalListParams>>,
  options: UseEvidenceListOptions = {}
) {
  const router = useRouter()
  const pageSize = options.pageSize ?? PAGE_SIZE

  const records = ref<EvidenceListItem[]>([])
  const loading = ref(false)
  const finished = ref(false)
  const refreshing = ref(false)
  const error = ref<string | null>(null)

  const page = ref(1)

  function buildParams(overrides: { page: number; pageSize: number }): EvidenceGlobalListParams {
    const filter = filterParamsRef.value || {}
    return {
      ...filter,
      page: overrides.page,
      pageSize: overrides.pageSize
    }
  }

  async function fetchPage(isRefresh: boolean) {
    if (isRefresh) {
      page.value = 1
      finished.value = false
      error.value = null
    }
    const res = (await listEvidence(buildParams({ page: page.value, pageSize }))) as unknown as EvidenceListResponse
    if (res.code !== 0) {
      const msg = res.message || '加载失败'
      error.value = msg
      showToast(msg)
      finished.value = true
      return
    }
    const list = res.data?.records ?? []
    if (isRefresh) records.value = list
    else records.value.push(...list)
    if (list.length < pageSize) finished.value = true
    else page.value++
  }

  async function onLoad() {
    if (loading.value) return
    loading.value = true
    error.value = null
    try {
      await fetchPage(false)
    } finally {
      loading.value = false
    }
  }

  async function onRefresh() {
    await fetchPage(true)
    refreshing.value = false
  }

  function reset() {
    page.value = 1
    records.value = []
    finished.value = false
    error.value = null
  }

  function goDetail(item: EvidenceListItem) {
    router.push({
      path: `/evidence/detail/${item.evidenceId}`,
      state: { evidence: item }
    } as any)
  }

  function formatDate(str: string): string {
    return formatDateTime(str)
  }

  function itemLabel(item: EvidenceListItem): string {
    const parts: string[] = []
    if (item.createdAt) parts.push(`上传: ${formatDate(item.createdAt)}`)
    if (item.latestVersion?.originalFilename) parts.push(item.latestVersion.originalFilename)
    return parts.join(' · ') || '—'
  }

  function statusTagType(s: string): 'success' | 'danger' | 'default' | 'primary' {
    return utilStatusTagType(s)
  }

  function statusText(s: string): string {
    return mapStatusToText(s)
  }

  watch(
    filterParamsRef,
    () => {
      reset()
      nextTick(() => onLoad())
    },
    { deep: true }
  )

  return {
    records,
    loading,
    finished,
    refreshing,
    error,
    onLoad,
    onRefresh,
    reset,
    formatDate,
    itemLabel,
    statusTagType,
    statusText,
    getEffectiveEvidenceStatus: (item: EvidenceListItem) => getEffectiveEvidenceStatus(item),
    goDetail
  }
}
