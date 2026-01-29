<template>
  <div class="project-detail">
    <van-nav-bar
      title="项目详情"
      left-arrow
      @click-left="$router.back()"
      fixed
    />
    <div class="content">
      <van-tabs v-model:active="activeTab" sticky>
        <!-- 详情 Tab -->
        <van-tab title="详情">
          <van-cell-group v-if="project">
            <van-cell title="项目编号" :value="project.code" />
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

            <!-- 上传按钮 -->
            <div class="upload-btn-wrapper">
              <van-button type="primary" icon="plus" @click="showUploadDialog = true">
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
                  :title="evidence.title"
                  :label="getEvidenceLabel(evidence)"
                  is-link
                >
                  <template #value>
                    <div class="cell-actions">
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
                        @click.stop="handleDownload(evidence)"
                      >
                        下载
                      </van-button>
                    </div>
                  </template>
                </van-cell>
                <van-empty v-if="!loading && evidenceList.length === 0" description="暂无证据" />
              </van-list>
            </van-pull-refresh>
          </div>
        </van-tab>
      </van-tabs>
    </div>

    <!-- 上传 Dialog -->
    <van-dialog
      v-model:show="showUploadDialog"
      title="上传证据"
      show-cancel-button
      @confirm="handleUpload"
      @cancel="resetUploadForm"
    >
      <van-form @submit="handleUpload">
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
      </van-form>
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
import { ref, onMounted, watch } from 'vue'
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
import { getEvidenceList, uploadEvidence, downloadVersionFile, type EvidenceListItem } from '@/api/evidence'

interface Project {
  id: number
  code: string
  name: string
  description: string
  status: string
  createdAt: string
}

const route = useRoute()
const router = useRouter()
const projectId = Number(route.params.id)
const project = ref<Project | null>(null)
const activeTab = ref(0)

// Mock 项目数据
const mockProject: Project = {
  id: projectId,
  code: 'PROJ-001',
  name: '智慧城市项目',
  description: '智慧城市综合管理平台开发项目，包括数据采集、分析、可视化等功能模块。',
  status: 'active',
  createdAt: '2024-01-15 10:30:00'
}

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
const uploadForm = ref({
  name: '',
  type: 'OTHER',
  remark: ''
})
const uploadFileList = ref<UploaderFileListItem[]>([])
const showBizTypePicker = ref(false)
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

// 文件类型映射
const getFileTypeText = (contentType: string) => {
  if (contentType?.includes('pdf')) return 'PDF'
  if (contentType?.includes('wordprocessingml')) return 'Word'
  if (contentType?.includes('spreadsheetml')) return 'Excel'
  if (contentType?.includes('image')) return '图片'
  return '文件'
}

// 加载项目信息
const loadProject = () => {
  setTimeout(() => {
    project.value = mockProject
  }, 300)
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
    
    const response = await getEvidenceList(projectId, params)
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

// 上传证据
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

  try {
    showLoadingToast({ message: '上传中...', forbidClick: true, duration: 0 })
    
    const formData = new FormData()
    formData.append('name', uploadForm.value.name)
    formData.append('type', uploadForm.value.type)
    if (uploadForm.value.remark) {
      formData.append('remark', uploadForm.value.remark)
    }
    formData.append('file', file)

    const response = await uploadEvidence(projectId, formData)
    
    if (response.code === 0) {
      showSuccessToast('上传成功')
      showUploadDialog.value = false
      resetUploadForm()
      // 刷新列表
      onRefresh()
    } else {
      showToast(response.message || '上传失败')
    }
  } catch (error: any) {
    console.error('Upload error:', error)
    showToast(error.message || '上传失败')
  } finally {
    closeToast()
  }
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

onMounted(() => {
  loadProject()
  // 如果直接进入证据Tab，加载证据列表
  if (activeTab.value === 1) {
    loadEvidenceList()
  }
})
</script>

<style scoped>
.project-detail {
  min-height: 100vh;
  padding-top: 46px;
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

.cell-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  align-items: center;
}

.cell-actions .preview-btn {
  min-width: 36px;
  height: 28px;
  padding: 0 10px;
  border: none;
  border-radius: 14px;
  background: linear-gradient(135deg, #1989fa 0%, #0d6efd 50%, #0a58ca 100%);
  box-shadow: 0 2px 8px rgba(25, 137, 250, 0.35);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.cell-actions .preview-btn :deep(.van-icon) {
  font-size: 16px;
  color: #fff;
  text-shadow: 0 1px 1px rgba(0, 0, 0, 0.15);
}

.cell-actions .preview-btn:active {
  transform: scale(0.96);
  box-shadow: 0 1px 4px rgba(25, 137, 250, 0.4);
}
</style>
