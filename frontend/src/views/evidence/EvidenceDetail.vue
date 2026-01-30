<template>
  <div class="evidence-detail">
    <template v-if="evidence">
      <van-cell-group inset>
        <van-cell title="证据标题" :value="evidence.title" />
        <van-cell title="项目ID" :value="String(evidence.projectId)" />
        <van-cell title="业务类型" :value="evidence.bizType" />
        <van-cell title="文件类型" :value="evidence.contentType || '—'" />
        <van-cell title="状态">
          <template #value>
            <van-tag :type="lifecycleTagType(evidence.evidenceStatus || evidence.status)">
              {{ lifecycleStatusText(evidence.evidenceStatus || evidence.status) }}
            </van-tag>
          </template>
        </van-cell>
        <van-cell title="上传人" :value="evidence.createdBy || '—'" />
        <van-cell title="上传时间" :value="formatDateTime(evidence.createdAt)" />
        <van-cell v-if="evidence.latestVersion" title="文件名" :value="evidence.latestVersion.originalFilename" />
      </van-cell-group>

      <div class="actions">
        <van-button type="primary" block icon="eye-o" @click="handlePreview">预览</van-button>
        <van-button type="primary" block plain icon="down" @click="handleDownload">下载</van-button>
        <van-button v-if="canSubmit" type="primary" block plain @click="handleSubmit">提交</van-button>
        <van-button v-if="canArchive" type="primary" block plain @click="handleArchive">归档</van-button>
        <van-button v-if="canVoid" type="danger" block plain icon="warning-o" @click="handleVoid">作废</van-button>
      </div>
    </template>
    <van-empty v-else description="无法加载详情，请从列表进入" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Cell, CellGroup, Button, Tag, Empty, showToast, showLoadingToast, showSuccessToast, closeToast, showConfirmDialog } from 'vant'
import {
  downloadVersionFile,
  getEvidenceById,
  submitEvidence,
  archiveEvidence,
  invalidateEvidence,
  type EvidenceListItem,
  type EvidenceStatus
} from '@/api/evidence'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const evidence = ref<EvidenceListItem | null>(null)
const evidenceId = computed(() => Number(route.params.id))

function lifecycleStatusText(s: string): string {
  const m: Record<string, string> = { DRAFT: '草稿', SUBMITTED: '已提交', ARCHIVED: '已归档', INVALID: '已作废', invalid: '已作废', archived: '已归档', active: '有效' }
  return m[s] || s
}

function lifecycleTagType(s: string): 'success' | 'danger' | 'default' | 'primary' {
  if (s === 'INVALID' || s === 'invalid') return 'danger'
  if (s === 'ARCHIVED' || s === 'archived') return 'success'
  if (s === 'SUBMITTED') return 'primary'
  return 'default'
}

const canSubmit = computed(() => (evidence.value?.evidenceStatus || evidence.value?.status) === 'DRAFT')
const canArchive = computed(() => (evidence.value?.evidenceStatus || evidence.value?.status) === 'SUBMITTED' && auth.isAdmin)
const canVoid = computed(() => (evidence.value?.evidenceStatus || evidence.value?.status) === 'SUBMITTED' && auth.isAdmin)

function initFromState() {
  const state = history.state as { evidence?: EvidenceListItem } | undefined
  if (state?.evidence && state.evidence.evidenceId === evidenceId.value) {
    evidence.value = state.evidence
  }
}

async function fetchDetail() {
  try {
    const res = await getEvidenceById(evidenceId.value) as { code: number; data?: EvidenceListItem }
    if (res?.code === 0 && res.data) evidence.value = res.data
  } catch {
    evidence.value = null
  }
}

async function handlePreview() {
  if (!evidence.value?.latestVersion) {
    showToast('暂无版本文件')
    return
  }
  const ct = (evidence.value.contentType || '').toLowerCase()
  const isPreviewable =
    ct.includes('pdf') ||
    ct.startsWith('image/') ||
    ct.startsWith('text/') ||
    ct.includes('json') ||
    ct.includes('xml')
  if (!isPreviewable) {
    showToast('该文件类型暂不支持预览，请下载查看')
    return
  }
  try {
    showLoadingToast({ message: '打开预览...', forbidClick: true, duration: 0 })
    const blob = await downloadVersionFile(evidence.value.latestVersion.versionId)
    const url = window.URL.createObjectURL(blob)
    window.open(url, '_blank', 'noopener,noreferrer')
    setTimeout(() => window.URL.revokeObjectURL(url), 60_000)
  } catch (e: any) {
    showToast(e.message || '预览失败')
  } finally {
    closeToast()
  }
}

async function handleDownload() {
  if (!evidence.value?.latestVersion) {
    showToast('暂无版本文件')
    return
  }
  try {
    showLoadingToast({ message: '下载中...', forbidClick: true, duration: 0 })
    const blob = await downloadVersionFile(evidence.value.latestVersion.versionId)
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = evidence.value.latestVersion.originalFilename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    showSuccessToast('下载成功')
  } catch (e: any) {
    showToast(e.message || '下载失败')
  } finally {
    closeToast()
  }
}

async function handleSubmit() {
  try {
    showLoadingToast({ message: '提交中...', forbidClick: true, duration: 0 })
    const res = await submitEvidence(evidenceId.value) as { code: number; message?: string }
    if (res?.code === 0) {
      showSuccessToast('已提交')
      await fetchDetail()
    } else {
      showToast(res?.message || '提交失败')
    }
  } catch (e: any) {
    showToast(e?.response?.data?.message || e?.message || '提交失败')
  } finally {
    closeToast()
  }
}

async function handleArchive() {
  showConfirmDialog({ title: '确认归档', message: '确定将该证据归档吗？归档后为最终有效状态。' })
    .then(async () => {
      try {
        showLoadingToast({ message: '归档中...', forbidClick: true, duration: 0 })
        const res = await archiveEvidence(evidenceId.value) as { code: number; message?: string }
        if (res?.code === 0) {
          showSuccessToast('已归档')
          await fetchDetail()
        } else {
          showToast(res?.message || '归档失败')
        }
      } catch (e: any) {
        showToast(e?.response?.data?.message || e?.message || '归档失败')
      } finally {
        closeToast()
      }
    })
    .catch(() => {})
}

function handleVoid() {
  showConfirmDialog({
    title: '确认作废',
    message: '确定要将该证据标记为作废吗？作废后仍可查看与下载，但会列入作废证据。'
  })
    .then(async () => {
      try {
        showLoadingToast({ message: '作废中...', forbidClick: true, duration: 0 })
        const res = await invalidateEvidence(evidenceId.value) as { code: number; message?: string }
        if (res?.code === 0) {
          showSuccessToast('已作废')
          await fetchDetail()
        } else {
          showToast(res?.message || '作废失败')
        }
      } catch (e: any) {
        showToast(e?.response?.data?.message || e?.message || '作废失败')
      } finally {
        closeToast()
      }
    })
    .catch(() => {})
}

onMounted(() => {
  initFromState()
  if (!evidence.value) fetchDetail()
})
</script>

<style scoped>
.evidence-detail {
  padding: 16px 0;
  min-height: 100%;
}
.actions {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
