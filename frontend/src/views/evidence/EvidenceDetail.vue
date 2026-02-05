<template>
  <div class="evidence-detail">
    <template v-if="evidence">
      <van-cell-group inset>
        <van-cell title="证据标题" :value="evidence.title" />
        <van-cell title="项目ID" :value="String(evidence.projectId)" />
        <van-cell title="业务类型" :value="evidence.bizType" />
        <van-cell title="文件类型" :value="evidence.contentType || '—'" />
        <van-cell title="当前状态">
          <template #value>
            <van-tag :type="statusTagType(effectiveStatus)">
              {{ mapStatusToText(effectiveStatus) }}
            </van-tag>
          </template>
        </van-cell>
        <van-cell title="上传人" :value="evidence.createdBy || '—'" />
        <van-cell title="上传时间" :value="formatDateTime(evidence.createdAt)" />
        <template v-if="effectiveStatus === 'INVALID' && (evidence.invalidReason || evidence.invalidBy || evidence.invalidAt)">
          <van-cell title="作废原因" :value="evidence.invalidReason || '—'" />
          <van-cell title="作废人" :value="evidence.invalidBy || '—'" />
          <van-cell title="作废时间" :value="evidence.invalidAt ? formatDateTime(evidence.invalidAt) : '—'" />
        </template>
        <van-cell v-if="evidence.latestVersion" title="文件名" :value="evidence.latestVersion.originalFilename" />
      </van-cell-group>

      <div class="actions">
        <van-button type="primary" block icon="eye-o" @click="handlePreview">预览</van-button>
        <van-button type="primary" block plain icon="down" @click="handleDownload">下载</van-button>
        <van-button v-if="canSubmit" type="primary" block plain @click="handleSubmit">提交</van-button>
        <van-button v-if="canArchive" type="primary" block plain @click="handleArchive">归档</van-button>
        <van-button v-if="canVoid" type="danger" block plain icon="warning-o" @click="handleVoid">作废</van-button>
      </div>

      <!-- 作废原因弹窗 -->
      <van-dialog
        v-model:show="showInvalidateReasonDialog"
        title="确认作废"
        show-cancel-button
        :before-close="onInvalidateReasonConfirm"
      >
        <van-field
          v-model="invalidateReasonText"
          type="textarea"
          rows="3"
          placeholder="请填写作废原因（必填）"
          maxlength="500"
          show-word-limit
        />
      </van-dialog>
    </template>
    <van-empty v-else description="无法加载详情，请从列表进入" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Cell, CellGroup, Button, Tag, Empty, Dialog, Field, showToast, showLoadingToast, showSuccessToast, closeToast, showConfirmDialog } from 'vant'
import {
  downloadVersionFile,
  getEvidenceById,
  submitEvidence,
  archiveEvidence,
  invalidateEvidence,
  type EvidenceListItem
} from '@/api/evidence'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/format'
import { getEffectiveEvidenceStatus, mapStatusToText, statusTagType } from '@/utils/evidenceStatus'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const evidence = ref<EvidenceListItem | null>(null)
const evidenceId = computed(() => Number(route.params.id))
const showInvalidateReasonDialog = ref(false)
const invalidateReasonText = ref('')

/** 当前状态（evidenceStatus 优先，与列表/弹窗一致） */
const effectiveStatus = computed(() => getEffectiveEvidenceStatus(evidence.value))

/** V1：提交/归档/作废只读后端 permissions，兼容扁平 canInvalidate */
const canSubmit = computed(() => effectiveStatus.value === 'DRAFT' && (evidence.value?.permissions?.canSubmit !== false))
const canArchive = computed(() => effectiveStatus.value === 'SUBMITTED' && (evidence.value?.permissions?.canArchive === true || evidence.value?.canInvalidate === true))
const canVoid = computed(() => {
  const s = effectiveStatus.value
  return (s === 'DRAFT' || s === 'SUBMITTED') && (evidence.value?.permissions?.canInvalidate === true || evidence.value?.canInvalidate === true)
})

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
    await showConfirmDialog({ title: '确认提交', message: '提交后进入管理流程，是否继续？' })
  } catch {
    return
  }
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
  showConfirmDialog({ title: '确认归档', message: '归档后仅用于留存，是否继续？' })
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
  invalidateReasonText.value = ''
  showInvalidateReasonDialog.value = true
}

async function onInvalidateReasonConfirm(action: string): Promise<boolean> {
  if (action !== 'confirm') return true
  const reason = invalidateReasonText.value?.trim()
  if (!reason) {
    showToast('请填写作废原因')
    return false
  }
  try {
    showLoadingToast({ message: '作废中...', forbidClick: true, duration: 0 })
    const res = await invalidateEvidence(evidenceId.value, reason) as { code: number; message?: string }
    if (res?.code === 0) {
      showSuccessToast('已作废')
      await fetchDetail()
      invalidateReasonText.value = ''
      return true
    }
    showToast(res?.message || '作废失败')
    return false
  } catch (e: any) {
    showToast(e?.response?.data?.message || e?.message || '作废失败')
    return false
  } finally {
    closeToast()
  }
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
