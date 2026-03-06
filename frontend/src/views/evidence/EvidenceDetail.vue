<template>
  <div class="evidence-detail">
    <template v-if="evidence">
      <!-- 1. 沉浸式图片预览区：约 35% 高度，深色剧场模式 -->
      <div v-if="evidence.latestVersion" class="evidence-detail__hero">
        <div class="evidence-detail__media-wrap">
          <template v-if="previewType === 'image'">
            <img
              :src="mediaBoxUrl"
              class="evidence-detail__media-img"
              alt="预览"
              @click="openImagePreview"
              @error="onMediaBoxImageError"
            />
            <div class="evidence-detail__watermark">系统自动提取时间/位置</div>
          </template>
          <template v-else-if="previewType === 'video'">
            <video
              :src="mediaBoxUrl"
              class="evidence-detail__media-video"
              controls
              controlslist="nodownload"
              preload="metadata"
              playsinline
            />
          </template>
          <template v-else-if="previewType === 'pdf'">
            <iframe :src="mediaBoxUrl" class="evidence-detail__media-iframe" title="PDF 预览" />
          </template>
          <template v-else-if="previewType === 'office'">
            <div class="evidence-detail__placehold evidence-detail__placehold--office">
              <van-icon name="description" size="48" color="#969799" />
              <p class="evidence-detail__placehold-text">由于格式限制，请下载后查看</p>
              <van-button class="btn-download-inline" icon="down" size="small" :loading="isDownloading" :disabled="isDownloading" @click="handleDownload">下载</van-button>
            </div>
          </template>
          <template v-else>
            <div class="evidence-detail__placehold">
              <van-icon :name="evidenceFileIcon.icon" size="56" :color="evidenceFileIcon.color" />
              <p class="evidence-detail__placehold-text">请下载后查看</p>
              <van-button class="btn-download-inline" icon="down" size="small" :loading="isDownloading" :disabled="isDownloading" @click="handleDownload">下载</van-button>
            </div>
          </template>
        </div>
      </div>

      <div class="evidence-detail__container" :class="{ 'has-hero': evidence.latestVersion }">
        <!-- 2. 核心信息卡片：向上偏移覆盖图片底，圆角 12px -->
        <div class="evidence-detail__core-card">
          <header class="evidence-detail__header">
            <div class="evidence-detail__type-icon" :style="{ background: evidenceFileIcon.bg, color: evidenceFileIcon.color }">
              <van-icon :name="evidenceFileIcon.icon" size="28" />
            </div>
            <div class="evidence-detail__title-wrap">
              <h1 class="evidence-detail__title">{{ evidence.title || '—' }}</h1>
              <van-tag round plain :type="statusTagTypeForCard(effectiveStatus)" class="evidence-detail__status-tag">
                {{ statusDisplayText }}
              </van-tag>
            </div>
          </header>
          <!-- 元数据：上传人、上传时间带图标 -->
          <div class="evidence-detail__meta-row">
            <span class="evidence-detail__meta-item">
              <van-icon name="user-o" class="evidence-detail__meta-icon" />
              {{ uploaderDisplayName() }}
            </span>
            <span class="evidence-detail__meta-item">
              <van-icon name="clock-o" class="evidence-detail__meta-icon" />
              {{ formatDateTime(evidence.createdAt) }}
            </span>
          </div>
          <section class="evidence-detail__meta">
            <div v-for="item in metaItems" :key="item.label" class="meta-item">
              <span class="meta-item__label">{{ item.label }}</span>
              <span class="meta-item__value">{{ item.value }}</span>
            </div>
          </section>
        </div>

        <!-- 3. 审核记录时间轴 -->
        <div class="evidence-detail__audit-card">
          <h3 class="evidence-detail__audit-title">审核记录</h3>
          <van-steps direction="vertical" :active="auditStepsActive">
            <van-step v-for="(step, idx) in auditSteps" :key="idx">
              <h3 class="audit-step-title">{{ step.title }}</h3>
              <div v-if="step.rejectReason" class="audit-reject-reason">驳回原因：{{ step.rejectReason }}</div>
              <p v-else class="audit-step-desc">{{ step.desc }}</p>
            </van-step>
          </van-steps>
        </div>

        <!-- 操作区：预览、下载、提交、作废、标记不符合 -->
        <div v-if="hasExtraActions" class="evidence-detail__actions">
          <van-button v-if="!hasInPagePreview" class="btn-primary action-btn" icon="eye-o" @click="handlePreview">预览</van-button>
          <van-button class="btn-secondary action-btn" icon="down" plain :loading="isDownloading" :disabled="isDownloading" @click="handleDownload">下载</van-button>
          <van-button v-if="canSubmit" class="btn-primary action-btn" icon="success" @click="handleSubmit">提交</van-button>
          <van-button v-if="canVoid" class="btn-danger action-btn" icon="warning-o" plain @click="handleVoid">作废</van-button>
          <van-button v-if="canMarkReject" class="btn-danger action-btn" icon="warning-o" plain @click="openMarkRejectDialogFromDetail">
            {{ hasLocalReject ? '修改标记' : '标记不符合' }}
          </van-button>
          <van-button v-if="canMarkReject && hasLocalReject" class="btn-secondary action-btn" plain @click="clearMarkRejectFromDetail">取消标记</van-button>
        </div>
      </div>

      <!-- 预览弹层 -->

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
      <!-- 详情页内「标记不符合」弹窗：与项目详情页共享草稿，PMO/Admin 在审批时可直接在详情页记录不符合原因 -->
      <van-dialog
        v-model:show="showMarkRejectDialog"
        title="标记不符合"
        show-cancel-button
        :before-close="onMarkRejectConfirmFromDetail"
      >
        <van-field
          v-model="markRejectCommentText"
          type="textarea"
          rows="3"
          placeholder="请填写不符合原因（仅作为本次退回草稿保存）"
          maxlength="500"
          show-word-limit
        />
      </van-dialog>
    </template>
    <van-loading v-else-if="detailLoading" class="detail-loading" vertical size="24">加载中...</van-loading>
    <van-empty v-else description="无法加载详情，证据可能不存在或已删除" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Empty, Dialog, Field, showToast, showLoadingToast, showSuccessToast, showFailToast, closeToast, showConfirmDialog, showImagePreview } from 'vant'
import 'vant/es/toast/style'
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
import { getEffectiveEvidenceStatus, mapStatusToText, statusTagType } from '@/utils/evidenceStatus'
import { getFriendlyErrorMessage } from '@/utils/errorMessage'
import { useArchiveRejectDraftStore } from '@/stores/archiveRejectDraft'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const archiveRejectDraftStore = useArchiveRejectDraftStore()

const evidence = ref<EvidenceListItem | null>(null)
const evidenceId = computed(() => Number(route.params.id))
/** 详情请求中（避免未完成时显示“无法加载”提示） */
const detailLoading = ref(true)
const showInvalidateReasonDialog = ref(false)
const invalidateReasonText = ref('')
/** 预览弹层：页面内展示，避免手机端 window.open 被拦截 */
const showPreviewPopup = ref(false)
/** 下载中状态，用于按钮 loading 与防重复点击 */
const isDownloading = ref(false)
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

/** 当前用户是否为 PMO 或系统管理员（可在审批时标记不符合） */
const isPMOOrAdmin = computed(() => {
  const code = auth.currentUser?.roleCode
  return code === 'PMO' || code === 'SYSTEM_ADMIN'
})

/** 来自哪个项目详情页（仅当路由显式带上 fromProject，才允许在详情页标记不符合） */
const fromProjectId = computed<number | null>(() => {
  const qp = Number(route.query.fromProject)
  if (!Number.isNaN(qp) && qp > 0) return qp
  return null
})

/** 与项目详情页共用的「标记不符合」草稿存储（key = projectId，下挂 evidenceId -> comment） */
const localRejectMap = computed<Record<string, string>>({
  get() {
    const pid = fromProjectId.value
    if (!pid) return {}
    return archiveRejectDraftStore.getProjectDraft(pid)
  },
  set(val) {
    const pid = fromProjectId.value
    if (!pid) return
    archiveRejectDraftStore.setProjectDraft(pid, val)
  }
})

/** 当前证据在本次审批中的本地标记内容 */
const currentLocalRejectComment = computed(() => {
  const e = evidence.value
  if (!e) return ''
  const local = localRejectMap.value[String(e.evidenceId)]
  return typeof local === 'string' ? local : ''
})

const hasLocalReject = computed(() => currentLocalRejectComment.value.trim().length > 0)

/** 仅在从项目详情进入、且当前用户为 PMO/Admin 且证据为已提交时，才展示「标记不符合」操作 */
const canMarkReject = computed(() => {
  return (
    !!fromProjectId.value &&
    isPMOOrAdmin.value &&
    effectiveStatus.value === 'SUBMITTED'
  )
})

/** 详情页内「标记不符合」弹窗状态 */
const showMarkRejectDialog = ref(false)
const markRejectCommentText = ref('')

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

/** 媒体预览区：根据文件类型返回 image | video | pdf | office | other */
const previewType = computed(() => {
  const e = evidence.value
  if (!e?.latestVersion) return 'other'
  const ct = (e.contentType || '').toLowerCase()
  const fn = e.latestVersion.originalFilename || ''
  const ext = fn.includes('.') ? fn.slice(fn.lastIndexOf('.') + 1).toLowerCase() : ''
  if (ct.startsWith('image/') || ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].includes(ext)) return 'image'
  if (ct.startsWith('video/') || ['mp4', 'mov', 'webm'].includes(ext)) return 'video'
  if (ct.includes('pdf') || ext === 'pdf') return 'pdf'
  if (OFFICE_MIMES.some(m => ct.includes(m)) || ['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx'].includes(ext)) return 'office'
  return 'other'
})

/** 媒体区/iframe 使用的下载 URL（同源带 Cookie） */
const mediaBoxUrl = computed(() => {
  const v = evidence.value?.latestVersion
  return v ? `/api/evidence/versions/${v.versionId}/download` : ''
})

/** 是否已在页面内展示预览（图片/视频/PDF），可隐藏「预览」按钮 */
const hasInPagePreview = computed(() => ['image', 'video', 'pdf'].includes(previewType.value))

/** 点击图片唤起 Vant ImagePreview 手势缩放 */
function openImagePreview() {
  const url = mediaBoxUrl.value
  if (!url) return
  showImagePreview({ images: [url], startPosition: 0 })
}

function onMediaBoxImageError() {
  showToast('图片加载失败，请检查网络或使用下载')
}

/** 元数据项（用于 Grid 展示，不含标题与状态） */
const metaItems = computed(() => {
  const e = evidence.value
  if (!e) return []
  const items: { label: string; value: string }[] = [
    { label: '业务类型', value: bizTypeLabel(e.bizType) },
    { label: '备注', value: e.note || '—' },
    { label: '文件类型', value: fileTypeDisplay(e) }
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

/** 状态 Tag 类型（Light 模式：已驳回浅红、已通过浅绿等） */
function statusTagTypeForCard(status: string | null | undefined): 'success' | 'danger' | 'default' | 'primary' {
  if (evidence.value?.rejectComment) return 'danger'
  return statusTagType(status)
}

/** 状态展示文案：有驳回原因时显示「已驳回」 */
const statusDisplayText = computed(() => (evidence.value?.rejectComment ? '已驳回' : mapStatusToText(effectiveStatus.value)))

/** 审核轨迹步骤（上传 → 提交 → 驳回(若有)） */
const auditSteps = computed(() => {
  const e = evidence.value
  if (!e) return []
  const steps: { title: string; desc: string; rejectReason?: string }[] = []
  steps.push({
    title: '上传成功',
    desc: formatDateTime(e.createdAt)
  })
  if (e.evidenceStatus && e.evidenceStatus !== 'DRAFT') {
    steps.push({
      title: '已提交',
      desc: e.updatedAt ? formatDateTime(e.updatedAt) : '—'
    })
  }
  if (e.rejectComment) {
    steps.push({
      title: '已驳回',
      desc: '',
      rejectReason: e.rejectComment
    })
  }
  return steps
})

const auditStepsActive = computed(() => Math.max(0, auditSteps.value.length - 1))

/** 是否显示额外操作区（预览/下载/提交/作废/标记不符合） */
const hasExtraActions = computed(() =>
  !hasInPagePreview.value || canSubmit.value || canVoid.value || canMarkReject.value
)

/** 从证据管理任一入口进入（按项目查看/我上传的/最近/作废/按类型）时仅保留预览、下载，不显示提交/删除/作废 */
const isReadOnlyFromEvidenceModule = computed(() =>
  route.query.from === 'evidence-by-project' || route.query.from === 'evidence'
)

/** 若从项目详情进入，路由中会带上项目状态（用于审批中时限制项目经理作废） */
const projectStatusFromRoute = computed(() => String(route.query.projectStatus || '').trim())

/** 草稿可提交、可物理删除；已提交可作废；归档仅由项目申请归档执行，无单条归档 */
const canSubmit = computed(() => !isReadOnlyFromEvidenceModule.value && effectiveStatus.value === 'DRAFT' && (evidence.value?.permissions?.canSubmit !== false))
const canDelete = computed(() => !isReadOnlyFromEvidenceModule.value && effectiveStatus.value === 'DRAFT' && (evidence.value?.permissions?.canSubmit !== false))
const canVoid = computed(() => {
  if (isReadOnlyFromEvidenceModule.value) return false
  // 项目处于待审批状态时，项目经理在详情页不能作废，仅 PMO/系统管理员可操作
  if (projectStatusFromRoute.value === 'pending_approval' && !isPMOOrAdmin.value) return false
  return effectiveStatus.value === 'SUBMITTED' && (evidence.value?.permissions?.canInvalidate === true || evidence.value?.canInvalidate === true)
})

function initFromState() {
  const state = history.state as { evidence?: EvidenceListItem } | undefined
  if (state?.evidence && state.evidence.evidenceId === evidenceId.value) {
    evidence.value = state.evidence
  }
}

async function fetchDetail() {
  if (!evidenceId.value || Number.isNaN(evidenceId.value)) {
    detailLoading.value = false
    return
  }
  detailLoading.value = true
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
  } finally {
    detailLoading.value = false
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
  } catch (e: unknown) {
    showToast(getFriendlyErrorMessage(e, '预览失败'))
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
  if (isDownloading.value) return
  isDownloading.value = true
  closeToast()
  const versionId = evidence.value.latestVersion.versionId
  const filename = evidence.value.latestVersion.originalFilename || 'download'
  const downloadUrl = `${getApiBaseUrl()}/evidence/versions/${versionId}/download`
  try {
    const response = await fetch(downloadUrl, {
      method: 'GET',
      credentials: 'include'
    })
    if (!response.ok) throw new Error('Network response was not ok')
    const blob = await response.blob()
    const objectUrl = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = filename
    link.style.display = 'none'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(objectUrl)
    showSuccessToast('已触发下载，请留意系统通知或相册')
  } catch (e: unknown) {
    console.error('下载失败:', e)
    if (isInAppBrowser()) {
      showToast({
        message: '请点击右上角在浏览器中打开后下载',
        duration: 3000
      })
    } else {
      showFailToast('下载失败，请尝试长按图片保存')
    }
  } finally {
    isDownloading.value = false
  }
}

/** 与 api/http 保持一致，用于 fetch 下载 URL */
function getApiBaseUrl(): string {
  let base = import.meta.env.VITE_API_BASE_URL ?? '/api'
  if (base !== '/api' && !base.endsWith('/api')) base = base.replace(/\/?$/, '') + '/api'
  return base.endsWith('/') ? base.slice(0, -1) : base
}

/** 是否处于微信/企业微信/钉钉等内置浏览器（下载可能受限） */
function isInAppBrowser(): boolean {
  const ua = typeof navigator !== 'undefined' ? navigator.userAgent.toLowerCase() : ''
  return /micromessenger|wxwork|dingtalk|aliapp/i.test(ua)
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
  } catch (e: unknown) {
    showToast(getFriendlyErrorMessage(e, '提交失败'))
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
  } catch (e: unknown) {
    showToast(getFriendlyErrorMessage(e, '删除失败'))
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
  } catch (e: unknown) {
    showToast(getFriendlyErrorMessage(e, '作废失败'))
    return false
  } finally {
    closeToast()
  }
}

/** 详情页点击「标记不符合」：打开弹窗，预填当前草稿 */
function openMarkRejectDialogFromDetail() {
  if (!canMarkReject.value || !evidence.value) return
  markRejectCommentText.value = currentLocalRejectComment.value || ''
  showMarkRejectDialog.value = true
}

/** 详情页「标记不符合」弹窗确认：将原因写入项目级草稿 map（与项目详情页共用） */
function onMarkRejectConfirmFromDetail(action: string): boolean {
  if (action !== 'confirm') {
    showMarkRejectDialog.value = false
    markRejectCommentText.value = ''
    return true
  }
  const e = evidence.value
  if (!e || !fromProjectId.value) {
    showToast('当前页面缺少项目上下文，无法标记不符合')
    return false
  }
  const text = markRejectCommentText.value?.trim()
  if (!text) {
    showToast('请填写不符合原因')
    return false
  }
  localRejectMap.value = {
    ...localRejectMap.value,
    [String(e.evidenceId)]: text
  }
  showMarkRejectDialog.value = false
  markRejectCommentText.value = ''
  showSuccessToast('已标记')
  return true
}

/** 详情页取消当前证据的本地「不符合」标记 */
function clearMarkRejectFromDetail() {
  const e = evidence.value
  if (!e || !fromProjectId.value) return
  const next = { ...localRejectMap.value }
  delete next[String(e.evidenceId)]
  localRejectMap.value = next
  showSuccessToast('已取消标记')
}

onMounted(() => {
  initFromState()
  // 始终拉取详情，以获取备注、上传人展示名、业务类型等完整数据
  fetchDetail()
})
</script>

<style scoped>
.evidence-detail {
  padding: 0 0 28px;
  min-height: 100%;
  background: var(--app-bg, #f5f7fa);
}
.detail-loading {
  padding: 48px 0;
}
/* ---------- 1. 沉浸式图片预览区：约 35% 高度，深色剧场 ---------- */
.evidence-detail__hero {
  min-height: 35vh;
  max-height: 40vh;
  background: #1c1c1e;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  position: relative;
}
.evidence-detail__media-wrap {
  width: 100%;
  height: 100%;
  min-height: 35vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}
.evidence-detail__media-img {
  max-width: 100%;
  max-height: 35vh;
  width: auto;
  height: auto;
  object-fit: contain;
  cursor: pointer;
  display: block;
}
.evidence-detail__media-video {
  max-width: 100%;
  max-height: 35vh;
  display: block;
}
.evidence-detail__media-iframe {
  width: 100%;
  height: 100%;
  min-height: 35vh;
  border: none;
  display: block;
}
.evidence-detail__watermark {
  position: absolute;
  right: 12px;
  bottom: 12px;
  font-size: 10px;
  color: rgba(255, 255, 255, 0.5);
  pointer-events: none;
}
.evidence-detail__placehold,
.evidence-detail__placehold--office {
  padding: 24px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: rgba(255, 255, 255, 0.7);
  width: 100%;
  min-height: 35vh;
}
.evidence-detail__placehold--office {
  color: rgba(255, 255, 255, 0.8);
}
.evidence-detail__placehold-text {
  margin: 0;
  font-size: 14px;
  color: inherit;
}
.btn-download-inline {
  margin-top: 4px;
}

/* ---------- 2. 核心信息卡片：向上偏移，圆角 12px ---------- */
.evidence-detail__container {
  max-width: 56rem;
  margin: 0 auto;
  padding: 0 16px;
}
.evidence-detail__core-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
  margin: 16px 16px 16px;
  padding: 20px 20px 24px;
}
.evidence-detail__container.has-hero .evidence-detail__core-card {
  margin: -16px 16px 16px;
}
.evidence-detail__header {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 12px;
}
.evidence-detail__type-icon {
  width: 48px;
  height: 48px;
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
  justify-content: space-between;
  gap: 10px 12px;
}
.evidence-detail__title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #1a1a1a;
  line-height: 1.35;
  flex: 1 1 auto;
  min-width: 0;
}
.evidence-detail__status-tag {
  flex-shrink: 0;
}
/* 元数据行：上传人、上传时间带图标 */
.evidence-detail__meta-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px 24px;
  margin-bottom: 16px;
  font-size: 12px;
  color: #969799;
}
.evidence-detail__meta-icon {
  margin-right: 4px;
  vertical-align: middle;
}
.evidence-detail__meta-item {
  display: inline-flex;
  align-items: center;
}
/* 元数据 Grid */
.evidence-detail__meta {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px 28px;
  padding: 0;
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
  margin-bottom: 4px;
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

/* ---------- 3. 审核记录时间轴 ---------- */
.evidence-detail__audit-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
  margin: 0 16px 16px;
  padding: 20px;
}
.evidence-detail__audit-title {
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
}
.audit-step-title {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 600;
  color: #323233;
}
.audit-step-desc {
  margin: 0;
  font-size: 12px;
  color: #969799;
  line-height: 1.4;
}
.audit-reject-reason {
  margin-top: 8px;
  padding: 8px 12px;
  background: #FDF2F2;
  border-radius: 4px;
  font-size: 12px;
  color: #ee0a24;
  line-height: 1.4;
}

/* ---------- 额外操作区 ---------- */
.evidence-detail__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: flex-start;
  padding: 0 0 24px;
}
@media (min-width: 768px) {
  .evidence-detail__actions {
    justify-content: flex-end;
  }
}
.evidence-detail__actions .van-button {
  min-width: 0;
  transition: transform 0.2s ease;
}
.evidence-detail__actions .action-btn:hover {
  transform: scale(1.02);
}
.evidence-detail__actions .action-btn:active {
  transform: scale(0.98);
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
  color: #ee4d2d;
  border: 1px solid #ee4d2d;
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
