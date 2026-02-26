<template>
  <div class="evidence-detail">
    <template v-if="evidence">
      <div class="evidence-detail__container">
        <div class="evidence-detail__card">
          <!-- 头部：类型图标 + 主标题 + 状态胶囊 -->
          <header class="evidence-detail__header">
            <div
              class="evidence-detail__type-icon"
              :style="{ background: evidenceFileIcon.bg, color: evidenceFileIcon.color }"
            >
              <van-icon :name="evidenceFileIcon.icon" size="28" />
            </div>
            <div class="evidence-detail__title-wrap">
              <h1 class="evidence-detail__title">{{ evidence.title || '—' }}</h1>
              <span
                class="evidence-detail__status-pill"
                :class="`evidence-detail__status-pill--${(effectiveStatus || '').toLowerCase()}`"
              >
                {{ mapStatusToText(effectiveStatus) }}
              </span>
            </div>
          </header>

          <!-- 核心信息：Grid 网格，移动端 1 列、大屏 2 列 -->
          <section class="evidence-detail__meta">
            <div
              v-for="item in metaItems"
              :key="item.label"
              class="meta-item"
            >
              <span class="meta-item__label">{{ item.label }}</span>
              <span class="meta-item__value">{{ item.value }}</span>
            </div>
          </section>

          <!-- 动态操作区：弹性布局，非全宽堆叠 -->
          <div class="evidence-detail__actions">
            <van-button class="btn-primary" icon="eye-o" @click="handlePreview">预览</van-button>
            <van-button class="btn-secondary" icon="down" plain @click="handleDownload">下载</van-button>
            <van-button v-if="canSubmit" class="btn-primary" @click="handleSubmit">提交</van-button>
            <van-button v-if="canDelete" class="btn-danger" icon="delete-o" plain @click="handleDelete">删除</van-button>
            <van-button v-if="canVoid" class="btn-danger" icon="warning-o" plain @click="handleVoid">作废</van-button>
          </div>
        </div>
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
            <!-- Word / Excel / PPT：iframe + preview=1 返回 inline，便于浏览器在线预览（Edge 等支持较好） -->
            <template v-else-if="isOfficePreview">
              <iframe
                :src="previewOfficeUrl"
                class="preview-iframe"
                title="Office 预览"
              />
              <p class="preview-office-hint">若无法显示，请使用 Edge 浏览器或点击下载</p>
            </template>
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
import { Empty, Dialog, Field, showToast, showLoadingToast, showSuccessToast, closeToast, showConfirmDialog } from 'vant'
import {
  downloadVersionFile,
  getEvidenceById,
  submitEvidence,
  deleteEvidence,
  invalidateEvidence,
  BIZ_TYPE_LABELS,
  type EvidenceListItem
} from '@/api/evidence'
import { useAuthStore } from '@/stores/auth'
import { getUsers, type AuthUserSimpleVO } from '@/api/users'
import { formatDateTime } from '@/utils/format'
import { getEffectiveEvidenceStatus, mapStatusToText } from '@/utils/evidenceStatus'

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
/** Word/Excel/PPT 的 MIME 类型（用于在线预览） */
const OFFICE_MIMES = [
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.ms-excel',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.ms-powerpoint',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation'
]
const isOfficePreview = computed(() => {
  const ct = (previewContentType.value || '').toLowerCase()
  return OFFICE_MIMES.some(m => ct.includes(m))
})
const previewOfficeUrl = computed(() => {
  const base = previewImageUrl.value
  if (!base) return ''
  return base + (base.includes('?') ? '&' : '?') + 'preview=1'
})
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

/** 头部文件类型图标配置（按 contentType / 文件名） */
const evidenceFileIcon = computed(() => {
  const e = evidence.value
  if (!e) return { icon: 'description', color: '#969799', bg: '#f7f8fa' }
  const ct = (e.contentType || '').toLowerCase()
  const fn = e.latestVersion?.originalFilename || ''
  const ext = fn.includes('.') ? fn.slice(fn.lastIndexOf('.') + 1).toLowerCase() : ''
  if (ct.startsWith('image/') || ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'heic'].includes(ext)) {
    return { icon: 'photo-o', color: '#07c160', bg: '#eefbf3' }
  }
  if (ct.includes('pdf') || ext === 'pdf') return { icon: 'description', color: '#ee4d2d', bg: '#fff1f0' }
  if (ct.includes('word') || ct.includes('wordprocessingml') || ['doc', 'docx'].includes(ext)) {
    return { icon: 'description', color: '#1989fa', bg: '#e8f4ff' }
  }
  if (ct.includes('excel') || ct.includes('spreadsheetml') || ['xls', 'xlsx', 'csv'].includes(ext)) {
    return { icon: 'bar-chart-o', color: '#07c160', bg: '#eefbf3' }
  }
  if (ct.includes('powerpoint') || ct.includes('presentation') || ['ppt', 'pptx'].includes(ext)) {
    return { icon: 'photo-o', color: '#ff976a', bg: '#fff7e8' }
  }
  if (['zip', 'rar', '7z'].includes(ext)) return { icon: 'gift-o', color: '#faad14', bg: '#fffbe6' }
  return { icon: 'description', color: '#969799', bg: '#f7f8fa' }
})

/** 元数据项（用于 Grid 展示，不含标题与状态） */
const metaItems = computed(() => {
  const e = evidence.value
  if (!e) return []
  const items: { label: string; value: string }[] = [
    { label: '业务类型', value: bizTypeLabel(e.bizType) },
    { label: '备注', value: e.note || '—' },
    { label: '文件类型', value: fileTypeDisplay(e) },
    { label: '上传人', value: uploaderDisplayName() },
    { label: '上传时间', value: formatDateTime(e.createdAt) }
  ]
  if (e.latestVersion) {
    items.push({ label: '文件名', value: e.latestVersion.originalFilename })
  }
  if (effectiveStatus.value === 'INVALID' && (e.invalidReason || e.invalidByUserId != null || e.invalidAt)) {
    items.push({ label: '作废原因', value: e.invalidReason || '—' })
    items.push({ label: '作废人', value: invalidatorDisplayName() })
    items.push({ label: '作废时间', value: e.invalidAt ? formatDateTime(e.invalidAt) : '—' })
  }
  return items
})

/** 从证据管理任一入口进入（按项目查看/我上传的/最近/作废/按类型）时仅保留预览、下载，不显示提交/删除/作废 */
const isReadOnlyFromEvidenceModule = computed(() =>
  route.query.from === 'evidence-by-project' || route.query.from === 'evidence'
)

/** 草稿可提交、可物理删除；已提交可作废；归档仅由项目申请归档执行，无单条归档 */
const canSubmit = computed(() => !isReadOnlyFromEvidenceModule.value && effectiveStatus.value === 'DRAFT' && (evidence.value?.permissions?.canSubmit !== false))
const canDelete = computed(() => !isReadOnlyFromEvidenceModule.value && effectiveStatus.value === 'DRAFT' && (evidence.value?.permissions?.canSubmit !== false))
const canVoid = computed(() => {
  if (isReadOnlyFromEvidenceModule.value) return false
  return effectiveStatus.value === 'SUBMITTED' && (evidence.value?.permissions?.canInvalidate === true || evidence.value?.canInvalidate === true)
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
  const isOffice = OFFICE_MIMES.some(m => ct.includes(m))
  const isTextLike = ct.startsWith('text/') || ct.includes('json') || ct.includes('xml')

  if (!isImage && !isPdf && !isOffice && !isTextLike) {
    showToast('该文件类型暂不支持预览，请下载查看')
    return
  }

  const versionId = evidence.value.latestVersion.versionId

  // 图片、PDF、Word/Excel/PPT：页面内弹层预览（Office 使用 iframe + preview=1 在线预览）
  if (isImage || isPdf || isOffice) {
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

async function handleDelete() {
  try {
    await showConfirmDialog({ title: '确认删除', message: '删除后不可恢复，是否继续？' })
  } catch {
    return
  }
  try {
    showLoadingToast({ message: '删除中...', forbidClick: true, duration: 0 })
    const res = await deleteEvidence(evidenceId.value) as { code: number; message?: string }
    if (res?.code === 0) {
      showSuccessToast('已删除')
      router.back()
    } else {
      showToast(res?.message || '删除失败')
    }
  } catch (e: any) {
    showToast(e?.response?.data?.message || e?.message || '删除失败')
  } finally {
    closeToast()
  }
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
  padding: 20px 0 28px;
  min-height: 100%;
  background: var(--app-bg, #f5f7fa);
}
.evidence-detail__container {
  max-width: 56rem;
  margin: 0 auto;
  padding: 0 16px;
}
/* 卡片容器：白底、微阴影、圆角 */
.evidence-detail__card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 8px rgba(0, 0, 0, 0.06), 0 2px 16px rgba(0, 0, 0, 0.04);
  padding: 24px 20px;
}
.evidence-detail__header {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 28px;
}
/* 文件类型图标：背景色块，类预览组件 */
.evidence-detail__type-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}
.evidence-detail__title-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 12px;
}
.evidence-detail__title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #1a1a1a;
  line-height: 1.35;
  flex: 1 1 auto;
  min-width: 0;
}
/* 状态：精致胶囊，浅灰底+深灰字（草稿） */
.evidence-detail__status-pill {
  display: inline-block;
  padding: 5px 14px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.3px;
  flex-shrink: 0;
}
.evidence-detail__status-pill--draft {
  background: #f2f3f5;
  color: #646566;
}
.evidence-detail__status-pill--submitted {
  background: #e8f4ff;
  color: #1989fa;
}
.evidence-detail__status-pill--archived {
  background: #eefbf3;
  color: #07c160;
}
.evidence-detail__status-pill--invalid {
  background: #fff1f0;
  color: #ee4d2d;
}
/* 元数据区：移动端单列，大屏双列 */
.evidence-detail__meta {
  display: grid;
  grid-template-columns: 1fr;
  gap: 20px 28px;
  margin-bottom: 28px;
}
@media (min-width: 768px) {
  .evidence-detail__meta {
    grid-template-columns: 1fr 1fr;
  }
}
.meta-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.meta-item__label {
  font-size: 12px;
  color: #969799;
  line-height: 1.35;
  font-weight: 400;
}
.meta-item__value {
  font-size: 15px;
  font-weight: 600;
  color: #323233;
  line-height: 1.45;
  word-break: break-all;
}
.evidence-detail__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: flex-start;
}
@media (min-width: 768px) {
  .evidence-detail__actions {
    justify-content: flex-end;
  }
}
.evidence-detail__actions .van-button {
  min-width: 0;
}
.evidence-detail__actions .btn-primary {
  background: var(--van-button-primary-background, #1989fa);
  color: #fff;
  border: none;
}
.evidence-detail__actions .btn-primary.van-button--plain {
  background: transparent;
  color: var(--van-button-primary-background, #1989fa);
  border: 1px solid currentColor;
}
.evidence-detail__actions .btn-secondary {
  background: transparent;
  color: var(--van-button-primary-background, #1989fa);
  border: 1px solid currentColor;
}
.evidence-detail__actions .btn-danger {
  background: transparent;
  color: var(--van-button-danger-color, #ee4d2d);
  border: 1px solid currentColor;
}
.evidence-detail__actions .btn-danger:active {
  background: rgba(238, 77, 45, 0.08);
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
.preview-office-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: #969799;
}
.preview-fallback {
  color: #969799;
  font-size: 14px;
}
</style>
