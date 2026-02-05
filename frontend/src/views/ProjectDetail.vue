<template>
  <div class="project-detail">
    <div class="content">
      <van-tabs v-model:active="activeTab" sticky>
        <!-- 详情 Tab -->
        <van-tab title="详情">
          <van-loading v-if="projectLoading" class="detail-loading" vertical>加载中...</van-loading>
          <van-empty v-else-if="projectError" :description="projectError" />
          <van-cell-group v-else-if="project">
            <van-cell title="项目令号" :value="project.code" />
            <van-cell title="项目名称" :value="project.name" />
            <van-cell title="项目描述" :value="project.description" />
            <van-cell title="项目状态">
              <template #value>
                <van-tag :type="project.status === 'active' ? 'success' : 'default'">
                  {{ project.status === 'active' ? '进行中' : '已归档' }}
                </van-tag>
              </template>
            </van-cell>
            <van-cell title="创建时间" :value="project.createdAt" />
          </van-cell-group>
        </van-tab>

        <!-- 证据 Tab -->
        <van-tab title="证据">
          <div class="evidence-section">
            <!-- 筛选栏 -->
            <van-dropdown-menu>
              <van-dropdown-item v-model="filterBizType" :options="bizTypeOptions" @change="onFilterChange" />
              <van-dropdown-item v-model="filterContentType" :options="contentTypeOptions" @change="onFilterChange" />
            </van-dropdown-menu>

            <!-- 上传按钮：仅 canUpload 时显示（V1 只读后端 permissions） -->
            <div v-if="canUpload" class="upload-btn-wrapper">
              <van-button type="primary" icon="plus" @click="openUploadDialog">
                上传证据
              </van-button>
            </div>

            <!-- 证据列表 -->
            <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
              <van-list
                v-model:loading="loading"
                :finished="finished"
                finished-text="没有更多了"
                @load="onLoad"
              >
                <van-cell
                  v-for="evidence in evidenceList"
                  :key="evidence.evidenceId"
                  class="evidence-list-cell"
                  @click="goToEvidenceDetail(evidence.evidenceId)"
                >
                  <template #default>
                    <div class="evidence-cell-content">
                      <div class="evidence-cell-main">
                        <div class="evidence-cell-title">{{ evidence.title }}</div>
                        <div class="evidence-cell-label">{{ getEvidenceLabel(evidence) }}</div>
                      </div>
                      <div class="cell-actions">
                        <van-tag :type="evidenceListStatusTagType(evidence)">
                          {{ evidenceListStatusText(evidence) }}
                        </van-tag>
                        <van-button
                          size="mini"
                          type="primary"
                          icon="eye-o"
                          class="preview-btn"
                          aria-label="预览"
                          @click.stop="handlePreview(evidence)"
                        />
                        <van-button
                          size="mini"
                          type="primary"
                          icon="down"
                          class="download-btn"
                          @click.stop="handleDownload(evidence)"
                        >
                          下载
                        </van-button>
                        <van-icon name="arrow" class="cell-arrow" />
                      </div>
                    </div>
                  </template>
                </van-cell>
                <van-empty v-if="!loading && evidenceList.length === 0" description="暂无证据" />
              </van-list>
            </van-pull-refresh>
          </div>
        </van-tab>
      </van-tabs>

      <!-- 成员管理入口：底部居中按钮，仅在有权限时显示 -->
      <div v-if="project?.canManageMembers" class="member-entry-wrap">
        <van-button type="primary" class="member-entry-btn" @click="goToMembers">
          成员管理
        </van-button>
      </div>
    </div>

    <!-- 上传弹窗：两阶段（表单 -> 结果与操作） -->
    <van-dialog
      v-model:show="showUploadDialog"
      :title="uploadPhase === 'form' ? '上传证据' : '上传结果'"
      :show-cancel-button="uploadPhase === 'form'"
      :show-confirm-button="uploadPhase === 'result'"
      confirm-button-text="关闭"
      @confirm="closeUploadDialog"
      @cancel="resetUploadForm"
    >
      <!-- 阶段1：表单 -->
      <template v-if="uploadPhase === 'form'">
        <van-cell-group inset>
          <van-field
            v-model="uploadForm.name"
            name="name"
            label="证据标题"
            placeholder="请输入证据标题"
            :rules="[{ required: true, message: '请输入证据标题' }]"
          />
          <van-field
            :model-value="bizTypeMap[uploadForm.type] || '其他'"
            name="type"
            label="业务类型"
            placeholder="请选择业务类型"
            is-link
            readonly
            @click="showBizTypePicker = true"
          />
          <van-field
            v-model="uploadForm.remark"
            name="remark"
            label="备注"
            type="textarea"
            placeholder="请输入备注（可选）"
            rows="3"
          />
          <div class="upload-file-section">
            <van-uploader
              v-model="uploadFileList"
              :max-count="1"
              accept="*/*"
            >
              <van-button icon="plus" type="primary">选择文件</van-button>
            </van-uploader>
          </div>
        </van-cell-group>
        <div class="upload-dialog-footer-inner">
          <van-button type="primary" block :loading="uploading" @click="handleUpload">
            确认上传
          </van-button>
        </div>
      </template>

      <!-- 阶段2：结果与操作 -->
      <template v-else>
        <div class="upload-result-block">
          <van-cell-group inset>
            <van-cell title="证据标题" :value="uploadResult?.title" />
            <van-cell title="文件名" :value="uploadResult?.fileName || '—'" />
            <van-cell title="上传时间" :value="uploadResult?.createdAt || '—'" />
            <van-cell title="当前状态">
              <template #value>
                <van-tag :type="uploadResultStatusTagType">
                  {{ uploadResultStatusText }}
                </van-tag>
              </template>
            </van-cell>
          </van-cell-group>
          <div class="upload-result-actions">
            <van-button
              v-if="showUploadResultSubmit"
              type="primary"
              block
              :loading="submitLoading"
              @click="handleUploadResultSubmit"
            >
              提交
            </van-button>
            <van-button
              type="primary"
              plain
              block
              @click="goToUploadResultDetail"
            >
              查看详情
            </van-button>
            <van-button
              v-if="showUploadResultInvalidate"
              type="danger"
              plain
              block
              :loading="invalidateLoading"
              @click="handleUploadResultInvalidate"
            >
              作废
            </van-button>
            <van-button plain block @click="continueUpload">
              继续上传
            </van-button>
          </div>
        </div>
      </template>
    </van-dialog>

    <!-- 作废原因弹窗（项目页上传结果内作废） -->
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
        class="invalidate-reason-field"
      />
    </van-dialog>

    <!-- 业务类型选择器 -->
    <van-popup v-model:show="showBizTypePicker" position="bottom">
      <van-picker
        :columns="bizTypePickerOptions"
        @confirm="onBizTypeConfirm"
        @cancel="showBizTypePicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NavBar,
  Cell,
  CellGroup,
  Button,
  Tag,
  Tabs,
  Tab,
  DropdownMenu,
  DropdownItem,
  List,
  PullRefresh,
  Empty,
  Loading,
  Dialog,
  Form,
  Field,
  Uploader,
  Popup,
  Picker,
  showToast,
  showLoadingToast,
  showSuccessToast,
  closeToast
} from 'vant'
import type { UploaderFileListItem } from 'vant'
import {
  getEvidenceList,
  uploadEvidence,
  downloadVersionFile,
  submitEvidence,
  invalidateEvidence,
  type EvidenceListItem
} from '@/api/evidence'
import { getProjectDetail } from '@/api/projects'
import { useAuthStore } from '@/stores/auth'
import { showConfirmDialog } from 'vant'
import { getEffectiveEvidenceStatus, mapStatusToText, statusTagType as evidenceStatusTagType } from '@/utils/evidenceStatus'

interface Project {
  id: number
  code: string
  name: string
  description: string
  status: string
  createdAt: string
  permissions?: { canUpload?: boolean; canInvalidate?: boolean; canManageMembers?: boolean }
  canInvalidate?: boolean
  canUpload?: boolean
  currentPmUserId?: string
  currentPmDisplayName?: string
}

const route = useRoute()
const router = useRouter()
const projectId = computed(() => Number(route.params.id))
const project = ref<Project | null>(null)
const projectLoading = ref(false)
const projectError = ref('')
const activeTab = ref(0)

// 证据列表相关
const evidenceList = ref<EvidenceListItem[]>([])
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)

// 筛选相关
const filterBizType = ref('')
const filterContentType = ref('')
const bizTypeOptions = [
  { text: '全部类型', value: '' },
  { text: '方案', value: 'PLAN' },
  { text: '报告', value: 'REPORT' },
  { text: '纪要', value: 'MINUTES' },
  { text: '测试', value: 'TEST' },
  { text: '验收', value: 'ACCEPTANCE' },
  { text: '其他', value: 'OTHER' }
]
const contentTypeOptions = [
  { text: '全部格式', value: '' },
  { text: 'PDF', value: 'application/pdf' },
  { text: 'Word', value: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' },
  { text: 'Excel', value: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' },
  { text: 'PNG图片', value: 'image/png' },
  { text: 'JPEG图片', value: 'image/jpeg' }
]

// 上传相关
const showUploadDialog = ref(false)
const uploadPhase = ref<'form' | 'result'>('form')
const uploadResult = ref<{
  evidenceId: number
  evidenceStatus: string
  title: string
  fileName?: string
  createdAt?: string
} | null>(null)
const uploading = ref(false)
const submitLoading = ref(false)
const invalidateLoading = ref(false)
const uploadForm = ref({
  name: '',
  type: 'OTHER',
  remark: ''
})
const uploadFileList = ref<UploaderFileListItem[]>([])
const showBizTypePicker = ref(false)
const showInvalidateReasonDialog = ref(false)
const invalidateReasonText = ref('')
const pendingInvalidateEvidenceId = ref<number | null>(null)
const auth = useAuthStore()

/** V1：上传按钮仅在后端返回 canUpload 时显示 */
const canUpload = computed(() => project.value?.permissions?.canUpload === true || project.value?.canUpload === true)

// 上传结果：状态展示（与列表/详情统一用 evidenceStatus 优先）
const uploadResultStatusText = computed(() => mapStatusToText(getEffectiveEvidenceStatus(uploadResult.value)))
const uploadResultStatusTagType = computed(() => evidenceStatusTagType(getEffectiveEvidenceStatus(uploadResult.value)))
const showUploadResultSubmit = computed(
  () => getEffectiveEvidenceStatus(uploadResult.value) === 'DRAFT'
)
const showUploadResultInvalidate = computed(() => {
  const s = getEffectiveEvidenceStatus(uploadResult.value)
  if (!s || s === 'ARCHIVED' || s === 'INVALID') return false
  return project.value?.canInvalidate === true
})
const bizTypePickerOptions = [
  { text: '方案', value: 'PLAN' },
  { text: '报告', value: 'REPORT' },
  { text: '纪要', value: 'MINUTES' },
  { text: '测试', value: 'TEST' },
  { text: '验收', value: 'ACCEPTANCE' },
  { text: '其他', value: 'OTHER' }
]

// 业务类型映射
const bizTypeMap: Record<string, string> = {
  PLAN: '方案',
  REPORT: '报告',
  MINUTES: '纪要',
  TEST: '测试',
  ACCEPTANCE: '验收',
  OTHER: '其他'
}

// 列表项状态展示：统一用 getEffectiveEvidenceStatus（evidenceStatus 优先）
function evidenceListStatusText(evidence: EvidenceListItem) {
  return mapStatusToText(getEffectiveEvidenceStatus(evidence))
}
function evidenceListStatusTagType(evidence: EvidenceListItem) {
  return evidenceStatusTagType(getEffectiveEvidenceStatus(evidence))
}

// 打开上传弹窗（重置为阶段1）
function openUploadDialog() {
  uploadPhase.value = 'form'
  uploadResult.value = null
  resetUploadForm()
  showUploadDialog.value = true
}

// 关闭上传弹窗并重置
function closeUploadDialog() {
  showUploadDialog.value = false
  uploadPhase.value = 'form'
  uploadResult.value = null
  resetUploadForm()
}

// 跳转证据详情
function goToEvidenceDetail(id: number) {
  router.push({ path: `/evidence/detail/${id}`, query: { fromProject: String(projectId.value) } })
}

function goToMembers() {
  router.push({ path: `/projects/${projectId.value}/members` })
}

// 文件类型映射
const getFileTypeText = (contentType: string) => {
  if (contentType?.includes('pdf')) return 'PDF'
  if (contentType?.includes('wordprocessingml')) return 'Word'
  if (contentType?.includes('spreadsheetml')) return 'Excel'
  if (contentType?.includes('image')) return '图片'
  return '文件'
}

// 加载项目信息（真实 API）
const loadProject = async () => {
  projectError.value = ''
  projectLoading.value = true
  try {
    const res = await getProjectDetail(projectId.value)
    if (res.code === 0 && res.data) {
      const p = res.data
      project.value = {
        id: p.id,
        code: p.code,
        name: p.name,
        description: p.description ?? '',
        status: p.status,
        createdAt: p.createdAt ?? '',
        permissions: p.permissions,
        canInvalidate: p.canInvalidate ?? false,
        canManageMembers: p.canManageMembers ?? false,
        canUpload: p.canUpload ?? p.permissions?.canUpload ?? false,
        currentPmUserId: p.currentPmUserId,
        currentPmDisplayName: p.currentPmDisplayName
      }
    } else {
      projectError.value = res.message || '加载失败'
    }
  } catch (e: any) {
    projectError.value = e?.message || '加载失败'
    if (e?.response?.data?.code === 403) {
      projectError.value = '无权限访问该项目'
    }
  } finally {
    projectLoading.value = false
  }
}

// 加载证据列表
const loadEvidenceList = async () => {
  if (loading.value) return
  
  loading.value = true
  try {
    const params: any = {}
    if (filterBizType.value) {
      params.bizType = filterBizType.value
    }
    if (filterContentType.value) {
      // 如果是图片筛选，需要特殊处理（后端可能需要精确匹配）
      params.contentType = filterContentType.value
    }
    
    const response = await getEvidenceList(projectId.value, params)
    if (response.code === 0) {
      evidenceList.value = response.data || []
      finished.value = true
    } else {
      showToast(response.message || '加载失败')
    }
  } catch (error: any) {
    console.error('Load evidence list error:', error)
    showToast(error.message || '加载失败')
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

// 下拉刷新
const onRefresh = () => {
  finished.value = false
  evidenceList.value = []
  loadEvidenceList()
}

// 上拉加载
const onLoad = () => {
  loadEvidenceList()
}

// 筛选变化
const onFilterChange = () => {
  finished.value = false
  evidenceList.value = []
  loadEvidenceList()
}

// 获取证据标签
const getEvidenceLabel = (evidence: EvidenceListItem) => {
  const parts: string[] = []
  parts.push(`类型: ${bizTypeMap[evidence.bizType] || evidence.bizType}`)
  parts.push(`格式: ${getFileTypeText(evidence.contentType)}`)
  if (evidence.latestVersion) {
    parts.push(`版本: v${evidence.latestVersion.versionNo}`)
  }
  parts.push(`更新: ${formatDate(evidence.updatedAt)}`)
  return parts.join(' | ')
}

// 格式化日期
const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return `${date.getMonth() + 1}-${date.getDate()} ${date.getHours()}:${String(date.getMinutes()).padStart(2, '0')}`
}

// 下载文件
const handleDownload = async (evidence: EvidenceListItem) => {
  if (!evidence.latestVersion) {
    showToast('暂无版本文件')
    return
  }

  try {
    showLoadingToast({ message: '下载中...', forbidClick: true, duration: 0 })
    const blob = await downloadVersionFile(evidence.latestVersion.versionId)
    
    // 创建下载链接
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = evidence.latestVersion.originalFilename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    
    showSuccessToast('下载成功')
  } catch (error: any) {
    console.error('Download error:', error)
    showToast(error.message || '下载失败')
  } finally {
    closeToast()
  }
}

// 预览文件（支持 PDF / 图片 / 文本；其余类型提示下载）
const handlePreview = async (evidence: EvidenceListItem) => {
  if (!evidence.latestVersion) {
    showToast('暂无版本文件')
    return
  }

  const ct = (evidence.contentType || '').toLowerCase()
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
    const blob = await downloadVersionFile(evidence.latestVersion.versionId)
    const url = window.URL.createObjectURL(blob)

    // 打开新窗口预览（部分浏览器会在新标签内渲染 PDF/图片/文本）
    window.open(url, '_blank', 'noopener,noreferrer')

    // 延迟释放，避免新窗口尚未读取完成就 revoke
    setTimeout(() => window.URL.revokeObjectURL(url), 60_000)
  } catch (error: any) {
    console.error('Preview error:', error)
    showToast(error.message || '预览失败')
  } finally {
    closeToast()
  }
}

// 业务类型选择确认
const onBizTypeConfirm = ({ selectedOptions }: any) => {
  if (selectedOptions && selectedOptions.length > 0) {
    uploadForm.value.type = selectedOptions[0].value
  }
  showBizTypePicker.value = false
}

// 上传证据（成功后切到阶段2，不关闭弹窗）
const handleUpload = async () => {
  if (!uploadForm.value.name.trim()) {
    showToast('请输入证据标题')
    return
  }

  if (uploadFileList.value.length === 0) {
    showToast('请选择要上传的文件')
    return
  }

  const file = uploadFileList.value[0].file
  if (!file) {
    showToast('文件无效')
    return
  }

  uploading.value = true
  try {
    showLoadingToast({ message: '上传中...', forbidClick: true, duration: 0 })

    const formData = new FormData()
    formData.append('name', uploadForm.value.name)
    formData.append('type', uploadForm.value.type)
    if (uploadForm.value.remark) {
      formData.append('remark', uploadForm.value.remark)
    }
    formData.append('file', file)

    const response = (await uploadEvidence(projectId.value, formData)) as {
      code: number
      message?: string
      data?: {
        id: number
        evidenceStatus?: string
        title?: string
        createdAt?: string
      }
    }
    closeToast()

    if (response.code === 0 && response.data) {
      const data = response.data
      const status = data.evidenceStatus ?? 'DRAFT'
      uploadResult.value = {
        evidenceId: data.id,
        evidenceStatus: status,
        title: data.title ?? uploadForm.value.name,
        fileName: file instanceof File ? file.name : undefined,
        createdAt: data.createdAt ? formatDate(data.createdAt) : undefined
      }
      uploadPhase.value = 'result'
      showSuccessToast('上传成功')
      onRefresh()
    } else {
      showToast(response.message || '上传失败')
    }
  } catch (error: any) {
    closeToast()
    console.error('Upload error:', error)
    showToast(error.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

// 阶段2：提交
async function handleUploadResultSubmit() {
  if (!uploadResult.value || getEffectiveEvidenceStatus(uploadResult.value) !== 'DRAFT') return
  try {
    await showConfirmDialog({
      title: '确认提交',
      message: '提交后将进入管理流程，是否继续？'
    })
  } catch {
    return
  }
  submitLoading.value = true
  try {
    const res = (await submitEvidence(uploadResult.value.evidenceId)) as { code: number; message?: string }
    if (res?.code === 0) {
      uploadResult.value = { ...uploadResult.value!, evidenceStatus: 'SUBMITTED' }
      showSuccessToast('已提交')
      onRefresh()
    } else {
      showToast(res?.message || '提交失败')
    }
  } catch (e: any) {
    showToast(e?.message || '提交失败')
  } finally {
    submitLoading.value = false
  }
}

// 阶段2：作废（先弹出填写原因）
function handleUploadResultInvalidate() {
  if (!uploadResult.value) return
  pendingInvalidateEvidenceId.value = uploadResult.value.evidenceId
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
  const id = pendingInvalidateEvidenceId.value
  if (id == null) return true
  invalidateLoading.value = true
  try {
    const res = (await invalidateEvidence(id, reason)) as { code: number; message?: string }
    if (res?.code === 0) {
      if (uploadResult.value?.evidenceId === id) {
        uploadResult.value = { ...uploadResult.value!, evidenceStatus: 'INVALID' }
      }
      showSuccessToast('已作废')
      onRefresh()
      pendingInvalidateEvidenceId.value = null
      invalidateReasonText.value = ''
      return true
    }
    showToast(res?.message || '作废失败')
    return false
  } catch (e: any) {
    showToast(e?.message || '作废失败')
    return false
  } finally {
    invalidateLoading.value = false
  }
}

// 阶段2：查看详情（关闭弹窗并跳转）
function goToUploadResultDetail() {
  if (uploadResult.value) {
    closeUploadDialog()
    router.push({ path: `/evidence/detail/${uploadResult.value.evidenceId}`, query: { fromProject: String(projectId.value) } })
  }
}

// 阶段2：继续上传（回到阶段1）
function continueUpload() {
  uploadPhase.value = 'form'
  uploadResult.value = null
  resetUploadForm()
}

// 重置上传表单
const resetUploadForm = () => {
  uploadForm.value = {
    name: '',
    type: 'OTHER',
    remark: ''
  }
  uploadFileList.value = []
}

// 监听Tab切换
watch(activeTab, (newVal) => {
  if (newVal === 1 && evidenceList.value.length === 0) {
    loadEvidenceList()
  }
})

// 路由 projectId 变化时重新加载详情
watch(() => route.params.id, () => {
  if (projectId.value) loadProject()
}, { immediate: false })

// 从证据详情返回时带 ?tab=evidence，需激活证据 Tab
watch(() => route.query.tab, (tab) => {
  if (tab === 'evidence') {
    activeTab.value = 1
    if (evidenceList.value.length === 0) loadEvidenceList()
  }
}, { immediate: true })

onMounted(() => {
  loadProject()
  if (route.query.tab === 'evidence') {
    activeTab.value = 1
  }
  if (activeTab.value === 1) {
    loadEvidenceList()
  }
})
</script>

<style scoped>
.project-detail {
  min-height: 100vh;
}

.content {
  padding: 0;
}

.evidence-section {
  padding: 16px;
}

.upload-btn-wrapper {
  margin: 16px 0;
  text-align: right;
}

.upload-file-section {
  padding: 16px;
  margin: 16px 0;
  background: #f7f8fa;
  border-radius: 8px;
}

/* 证据列表 cell：自定义整行布局，右侧操作区紧贴右边缘无留白 */
.evidence-cell-content {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  gap: 8px;
  min-height: 0;
}

.evidence-cell-main {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.evidence-cell-title {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.evidence-cell-label {
  margin-top: 2px;
  font-size: 12px;
  color: var(--van-gray-6);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* value 区域占满剩余宽度，内容区左撑满，避免整块被挤到右侧 */
.evidence-list-cell :deep(.van-cell__value) {
  flex: 1;
  min-width: 0;
  display: flex;
  justify-content: stretch;
  text-align: left;
}

.cell-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.cell-actions .preview-btn {
  min-width: 36px;
  height: 28px;
  padding: 0 10px;
  border: none;
  border-radius: 4px;
  background: var(--van-primary-color);
}

.cell-actions .preview-btn :deep(.van-icon) {
  font-size: 16px;
  color: #fff;
}

.cell-arrow {
  color: var(--van-gray-5);
  font-size: 16px;
}

/* 窄屏：下载按钮仅显示图标，避免挤断行 */
@media (max-width: 360px) {
  .evidence-list-cell .cell-actions .download-btn :deep(.van-button__text) {
    display: none;
  }
}

.upload-dialog-footer-inner {
  padding: 12px 16px 16px;
}

.upload-result-block {
  padding-bottom: 8px;
}

.upload-result-actions {
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-loading {
  padding: 24px;
  display: flex;
  justify-content: center;
}

/* 成员管理入口：底部居中按钮 */
.member-entry-wrap {
  padding: 24px 16px 32px;
  display: flex;
  justify-content: center;
}

.member-entry-btn {
  min-width: 160px;
}
</style>
