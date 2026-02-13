<template>
  <div class="evidence-detail">
    <template v-if="evidence">
      <van-cell-group inset>
        <van-cell title="证据标题" :value="evidence.title || '—'" />
        <van-cell title="业务类型" :value="bizTypeLabel(evidence.bizType)" />
        <van-cell title="备注" :value="evidence.note || '—'" />
        <van-cell title="文件类型" :value="fileTypeDisplay(evidence)" />
        <van-cell title="当前状态">
          <template #value>
            <van-tag :type="statusTagType(effectiveStatus)">
              {{ mapStatusToText(effectiveStatus) }}
            </van-tag>
          </template>
        </van-cell>
        <van-cell title="上传人" :value="uploaderDisplayName()" />
        <van-cell title="上传时间" :value="formatDateTime(evidence.createdAt)" />
        <template v-if="effectiveStatus === 'INVALID' && (evidence.invalidReason || evidence.invalidByUserId != null || evidence.invalidAt)">
          <van-cell title="作废原因" :value="evidence.invalidReason || '—'" />
          <van-cell title="作废人" :value="invalidatorDisplayName()" />
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

      <!-- 预览弹层：页面内展示，避免手机端 window.open 被拦截 -->
      <van-popup
        v-model:show="showPreviewPopup"
        position="center"
        round
        :style="{ width: '94vw', maxWidth: '400px', maxHeight: '85vh' }"
        closeable
        close-icon-position="top-right"
        @closed="previewVersionId = null"
      >
        <div class="preview-popup-body">
          <template v-if="previewVersionId != null">
            <!-- 图片：直接使用接口 URL，同源会带 Cookie -->
            <img
              v-if="previewContentType?.startsWith('image/')"
              :src="previewImageUrl"
              class="preview-img"
              alt="预览"
              @error="onPreviewImageError"
            />
            <!-- PDF：iframe 内嵌 -->
            <iframe
              v-else-if="previewContentType === 'application/pdf'"
              :src="previewImageUrl"
              class="preview-iframe"
              title="PDF 预览"
            />
            <div v-else class="preview-fallback">该类型请在电脑端预览或下载查看</div>
          </template>
        </div>
      </van-popup>

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
  BIZ_TYPE_LABELS,
  type EvidenceListItem
} from '@/api/evidence'
import { useAuthStore } from '@/stores/auth'
import { getUsers, type AuthUserSimpleVO } from '@/api/users'
import { formatDateTime } from '@/utils/format'
import { getEffectiveEvidenceStatus, mapStatusToText, statusTagType } from '@/utils/evidenceStatus'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const evidence = ref<EvidenceListItem | null>(null)
const evidenceId = computed(() => Number(route.params.id))
const showInvalidateReasonDialog = ref(false)
const invalidateReasonText = ref('')
/** 预览弹层：页面内展示，避免手机端 window.open 被拦截 */
const showPreviewPopup = ref(false)
const previewVersionId = ref<number | null>(null)
const previewContentType = ref<string>('')
const previewImageUrl = computed(() =>
  previewVersionId.value != null ? `/api/evidence/versions/${previewVersionId.value}/download` : ''
)
/** 用户列表（用于兜底：接口未返回 createdByDisplayName 时按 userId 解析展示名） */
const userList = ref<AuthUserSimpleVO[]>([])

/** 业务类型中文展示 */
function bizTypeLabel(bizType: string | undefined) {
  if (!bizType) return '—'
  return BIZ_TYPE_LABELS[bizType] ?? bizType
}

/** 文件类型友好展示：将 MIME 转为可读名称，有文件名时附带扩展名 */
function fileTypeDisplay(e: EvidenceListItem | null): string {
  if (!e) return '—'
  const ct = (e.contentType || '').toLowerCase()
  let label = '—'
  if (ct.includes('pdf')) label = 'PDF'
  else if (ct.includes('wordprocessingml')) label = 'Word'
  else if (ct.includes('spreadsheetml')) label = 'Excel'
  else if (ct.includes('image')) label = '图片'
  else if (ct) label = e.contentType!
  const fn = e.latestVersion?.originalFilename
  const ext = fn?.includes('.') ? fn.slice(fn.lastIndexOf('.')) : ''
  if (label !== '—' && ext) return `${label} (${ext})`
  return label
}

/** 上传人展示名：优先用接口返回的 createdByDisplayName，否则用用户列表按 id 解析 */
function uploaderDisplayName(): string {
  const e = evidence.value
  if (!e) return '—'
  if (e.createdByDisplayName) return e.createdByDisplayName
  if (e.createdByUserId == null) return '—'
  const u = userList.value.find((x) => x.id === e.createdByUserId)
  return u ? (u.displayName || u.username || String(u.id)) : String(e.createdByUserId)
}

/** 作废人展示名：优先用接口返回的 invalidByDisplayName，否则用用户列表按 invalidByUserId 解析 */
function invalidatorDisplayName(): string {
  const e = evidence.value
  if (!e) return '—'
  if (e.invalidByUserId == null) return '—'
  if (e.invalidByDisplayName) return e.invalidByDisplayName
  const u = userList.value.find((x) => x.id === e.invalidByUserId)
  return u ? (u.displayName || u.username || String(u.id)) : String(e.invalidByUserId)
}

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
    if (res?.code === 0 && res.data) {
      evidence.value = res.data
      const needUserList =
        (res.data.createdByUserId != null && !res.data.createdByDisplayName) ||
        (res.data.invalidByUserId != null && !res.data.invalidByDisplayName)
      if (needUserList && userList.value.length === 0) {
        try {
          const r = await getUsers() as { code: number; data?: AuthUserSimpleVO[] }
          if (r?.code === 0 && r.data) userList.value = r.data
        } catch {
          // ignore
        }
      }
    } else {
      evidence.value = null
    }
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
  const isImage = ct.startsWith('image/')
  const isPdf = ct.includes('pdf')
  const isTextLike = ct.startsWith('text/') || ct.includes('json') || ct.includes('xml')

  if (!isImage && !isPdf && !isTextLike) {
    showToast('该文件类型暂不支持预览，请下载查看')
    return
  }

  const versionId = evidence.value.latestVersion.versionId

  // 图片、PDF：页面内弹层预览（手机端不会被弹窗拦截）
  if (isImage || isPdf) {
    previewContentType.value = evidence.value.contentType || ''
    previewVersionId.value = versionId
    showPreviewPopup.value = true
    return
  }

  // 文本类：仍用 blob + 新窗口（电脑端有效；手机端可能被拦截则提示下载）
  try {
    showLoadingToast({ message: '打开预览...', forbidClick: true, duration: 0 })
    const blob = await downloadVersionFile(versionId)
    const url = window.URL.createObjectURL(blob)
    const w = window.open(url, '_blank', 'noopener,noreferrer')
    setTimeout(() => window.URL.revokeObjectURL(url), 60_000)
    if (!w) showToast('若未打开新窗口，请使用下载')
  } catch (e: any) {
    showToast(e.message || '预览失败')
  } finally {
    closeToast()
  }
}

function onPreviewImageError() {
  showToast('预览加载失败，请检查网络或使用下载')
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
  // 始终拉取详情，以获取备注、上传人展示名、业务类型等完整数据
  fetchDetail()
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

.preview-popup-body {
  padding: 12px;
  min-height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f7f8fa;
}
.preview-img {
  max-width: 100%;
  max-height: 75vh;
  object-fit: contain;
  display: block;
}
.preview-iframe {
  width: 100%;
  height: 75vh;
  border: none;
}
.preview-fallback {
  color: #969799;
  font-size: 14px;
}
</style>
