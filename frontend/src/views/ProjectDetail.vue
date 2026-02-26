<template>
  <div class="project-detail">
    <div class="content">
      <!-- 顶部摘要卡片：项目名称、状态胶囊、核心负责人 -->
      <div v-if="project && !projectLoading" class="detail-header-card">
        <h1 class="detail-header-title">{{ project.name }}</h1>
        <div class="detail-header-meta">
          <span class="detail-header-badge" :class="project.status === 'active' ? 'badge--active' : 'badge--archived'">
            {{ project.status === 'active' ? '进行中' : '已归档' }}
          </span>
          <span v-if="project.currentPmDisplayName" class="detail-header-responsible">负责人：{{ project.currentPmDisplayName }}</span>
        </div>
      </div>

      <!-- 分段控制器：详情 / 证据 -->
      <div class="segmented-control">
        <button type="button" class="segmented-item" :class="{ active: activeTab === 0 }" @click="activeTab = 0">基本信息</button>
        <button type="button" class="segmented-item" :class="{ active: activeTab === 1 }" @click="activeTab = 1">证据管理</button>
      </div>

      <van-tabs v-model:active="activeTab" sticky class="tabs-custom-nav">
        <van-tab title="详情">
          <van-loading v-if="projectLoading" class="detail-loading" vertical>加载中...</van-loading>
          <van-empty v-else-if="projectError" :description="projectError" />
          <template v-else-if="project">
            <!-- 信息列表区：左右布局，字段名置灰，数据加深，分割线 -->
            <div class="info-card">
              <div class="info-row">
                <span class="info-label">项目令号</span>
                <span class="info-value">{{ project.code }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">项目名称</span>
                <span class="info-value">{{ project.name }}</span>
              </div>
              <div class="info-row" v-if="project.description">
                <span class="info-label">项目描述</span>
                <span class="info-value">{{ project.description }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">项目状态</span>
                <span class="info-value">
                  <span class="info-badge" :class="project.status === 'active' ? 'badge--active' : 'badge--archived'">
                    {{ project.status === 'active' ? '进行中' : '已归档' }}
                  </span>
                </span>
              </div>
              <div class="info-row">
                <span class="info-label">是否含采购</span>
                <span class="info-value">
                  <van-switch
                    v-if="project.canManageMembers && project.status !== 'archived'"
                    :model-value="project.hasProcurement"
                    size="20"
                    @update:model-value="onHasProcurementChange"
                  />
                  <span v-else>{{ project.hasProcurement ? '是' : '否' }}</span>
                </span>
              </div>
              <div class="info-row">
                <span class="info-label">创建时间</span>
                <span class="info-value">{{ project.createdAt }}</span>
              </div>
            </div>
            <!-- 证据完成度（详情页也展示） -->
            <div v-if="stageProgress" class="stage-progress-header detail-tab-progress">
              <div class="completion-row">
                <span class="completion-label">证据完成度</span>
                <van-progress :percentage="stageProgress.overallCompletionPercent" stroke-width="8" />
                <span class="completion-value">{{ stageProgress.overallCompletionPercent }}%</span>
              </div>
              <div v-if="stageProgress.keyMissing?.length" class="key-missing-row">
                <span class="key-missing-label">关键缺失：</span>
                <span class="key-missing-list">{{ (stageProgress.keyMissing || []).slice(0, 5).join('、') }}</span>
              </div>
              <div class="archive-row">
                <van-button
                  type="primary"
                  :disabled="!stageProgress.canArchive || project?.status === 'archived'"
                  :title="!stageProgress.canArchive ? (stageProgress.archiveBlockReason || '不满足归档条件') : ''"
                  @click="handleArchive"
                >
                  申请归档
                </van-button>
                <span v-if="!stageProgress.canArchive && stageProgress.archiveBlockReason" class="archive-block-tip">
                  {{ stageProgress.archiveBlockReason }}
                </span>
              </div>
            </div>
            <van-loading v-else-if="stageProgressLoading" class="stage-progress-loading" vertical>加载阶段进度...</van-loading>
            <!-- 项目成员列表 -->
          <div class="members-section">
            <div class="members-section-title">项目成员</div>
            <van-loading v-if="membersLoading" class="members-loading" size="20" vertical>加载中...</van-loading>
            <van-cell-group v-else-if="members.length">
              <van-cell
                v-for="m in sortedMembers"
                :key="m.userId"
                :title="m.displayName || m.username || String(m.userId)"
                :label="m.username && (m.displayName || '') !== m.username ? `@${m.username}` : undefined"
              >
                <template #value>
                  <span class="member-role-desc">{{ memberRoleLabel(m.role) }}</span>
                </template>
              </van-cell>
            </van-cell-group>
            <van-empty v-else description="暂无成员" :image-size="60" />
          </div>
          </template>
        </van-tab>

        <!-- 证据 Tab（阶段驱动：完整度 + 阶段折叠 + 模板项证据列表） -->
        <van-tab title="证据">
          <div class="evidence-section">
            <!-- 阶段进度顶部：完整度、关键缺失、申请归档 -->
            <div v-if="stageProgress" class="stage-progress-header">
              <div class="completion-row">
                <span class="completion-label">证据完成度</span>
                <van-progress :percentage="stageProgress.overallCompletionPercent" stroke-width="8" />
                <span class="completion-value">{{ stageProgress.overallCompletionPercent }}%</span>
              </div>
              <div v-if="stageProgress.keyMissing?.length" class="key-missing-row">
                <span class="key-missing-label">关键缺失：</span>
                <span class="key-missing-list">{{ (stageProgress.keyMissing || []).slice(0, 5).join('、') }}</span>
              </div>
              <div class="archive-row">
                <van-button
                  type="primary"
                  :disabled="!stageProgress.canArchive || project?.status === 'archived'"
                  :title="!stageProgress.canArchive ? (stageProgress.archiveBlockReason || '不满足归档条件') : ''"
                  @click="handleArchive"
                >
                  申请归档
                </van-button>
                <span v-if="!stageProgress.canArchive && stageProgress.archiveBlockReason" class="archive-block-tip">
                  {{ stageProgress.archiveBlockReason }}
                </span>
              </div>
            </div>
            <van-loading v-else-if="stageProgressLoading" class="stage-progress-loading" vertical>加载阶段进度...</van-loading>

            <!-- 阶段折叠清单 -->
            <van-collapse v-if="stageProgress?.stages?.length" v-model="expandedStages" class="stage-collapse">
              <van-collapse-item
                v-for="s in stageProgress.stages"
                :key="s.stageCode"
                :name="s.stageCode"
              >
                <template #title>
                  <div class="stage-title-row">
                    <span class="stage-name">{{ s.stageName || s.stageCode }}</span>
                    <span class="stage-count">{{ (s.displayCompletedCount ?? s.completedCount) }}/{{ (s.displayItemCount ?? s.itemCount) }}</span>
                    <van-tag :type="healthTagType(s.healthStatus)">{{ healthStatusText(s.healthStatus) }}</van-tag>
                    <van-tag v-if="s.stageCompleted" type="success">已完成</van-tag>
                  </div>
                </template>
                <!-- 模板项平铺卡片 -->
                <div class="stage-items-flat">
                  <div
                    v-for="(item, idx) in (s.items || [])"
                    :key="item.evidenceTypeCode + '-' + idx"
                    class="evidence-card"
                  >
                    <!-- 卡片标题行：大点击区域列表项，右侧状态透出 + 箭头 -->
                    <div class="evidence-card-header">
                      <div class="evidence-card-title">
                        <span class="card-name">{{ item.groupDisplayName || item.displayName }}</span>
                        <van-tag v-if="item.isRequired || item.required" type="danger" size="mini" class="card-required">必填</van-tag>
                        <van-tag v-else type="default" size="mini">选填</van-tag>
                      </div>
                      <div class="evidence-card-status">
                        <span class="card-status-text" :class="{ 'status--pending': (item.minCount ?? 1) > 0 && (item.uploadCount ?? item.currentCount) === 0 }">
                          {{ (item.minCount ?? 1) === 0 ? `选填（已 ${item.uploadCount ?? item.currentCount} 份）` : `已 ${item.uploadCount ?? item.currentCount} / 需 ${item.minCount ?? 1} 份` }}
                        </span>
                        <van-icon name="arrow" class="card-arrow" />
                      </div>
                    </div>
                    <!-- 3列网格：缩略图/文件图标 + 上传入口 -->
                    <div class="evidence-card-grid">
                      <van-loading v-if="isItemLoading(s.stageCode, item.evidenceTypeCode)" size="20" />
                      <template v-else>
                        <div
                          v-for="ev in getItemEvidences(s.stageCode, item.evidenceTypeCode)"
                          :key="ev.evidenceId"
                          class="grid-item"
                          @click="goToEvidenceDetail(ev.evidenceId)"
                        >
                          <!-- 图片缩略图：圆角、cover，右上角删除图标 -->
                          <div v-if="isImageType(ev.contentType) && ev.latestVersion" class="grid-thumb" @click.stop="goToEvidenceDetail(ev.evidenceId)">
                            <img :src="`/api/evidence/versions/${ev.latestVersion.versionId}/download`" :alt="ev.title" />
                            <div class="grid-thumb-overlay">
                              <van-tag :type="evidenceListStatusTagType(ev)" size="mini">{{ evidenceListStatusText(ev) }}</van-tag>
                            </div>
                            <button type="button" class="grid-thumb-delete" aria-label="删除" @click.stop="onDeleteEvidence(ev, s, item)"><van-icon name="delete-o" /></button>
                          </div>
                          <!-- 非图片：文档类列表式展示，左侧图标、中间文件名、右侧删除 -->
                          <div v-else class="grid-file" :style="{ background: getFileIconConfig(getEvidenceFileName(ev)).bg }" @click.stop="goToEvidenceDetail(ev.evidenceId)">
                            <span class="grid-file-type-badge" :style="{ background: getFileIconConfig(getEvidenceFileName(ev)).color }">
                              {{ getFileIconConfig(getEvidenceFileName(ev)).label }}
                            </span>
                            <van-icon
                              :name="getFileIconConfig(getEvidenceFileName(ev)).icon"
                              size="28"
                              :color="getFileIconConfig(getEvidenceFileName(ev)).color"
                            />
                            <span class="grid-file-name">{{ ev.latestVersion?.originalFilename || ev.title }}</span>
                            <van-tag :type="evidenceListStatusTagType(ev)" size="mini">{{ evidenceListStatusText(ev) }}</van-tag>
                            <button type="button" class="grid-file-delete" aria-label="删除" @click.stop="onDeleteEvidence(ev, s, item)"><van-icon name="delete-o" /></button>
                          </div>
                        </div>
                        <!-- 上传入口：虚线框 + 号 -->
                        <div
                          v-if="canUpload && s.stageId"
                          class="grid-item grid-upload-btn"
                          @click="openUploadForItem(s, item)"
                        >
                          <van-icon name="plus" size="24" color="var(--van-gray-5)" />
                          <span class="grid-upload-text">上传</span>
                        </div>
                      </template>
                    </div>
                  </div>
                </div>
              </van-collapse-item>
            </van-collapse>
            <van-empty v-else-if="stageProgress && !stageProgressLoading" description="暂无阶段配置" :image-size="60" />
          </div>
        </van-tab>
      </van-tabs>

      <!-- 成员管理入口：仅在「详情」Tab 显示，证据 Tab 不显示 -->
      <div v-if="project?.canManageMembers && activeTab === 0" class="member-entry-wrap">
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
          <van-cell v-if="uploadContext" :title="'上传至'" :value="uploadContext.displayName" class="upload-context-cell" />
          <van-cell v-else title="提示" class="upload-hint-cell">
            <template #value>
              <span class="upload-hint-text">请先在下方向某证据类型（如「启动现场照片」）点击展开，再点击该类型下的「上传」按钮</span>
            </template>
          </van-cell>
          <van-field
            v-model="uploadForm.name"
            name="name"
            label="证据标题"
            placeholder="请输入证据标题"
            :rules="[{ required: true, message: '请输入证据标题' }]"
          />
          <van-field
            v-if="!uploadContext"
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
              :before-read="onUploaderBeforeRead"
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
              v-if="showUploadResultDelete"
              type="danger"
              plain
              block
              :loading="deleteLoading"
              @click="handleUploadResultDelete"
            >
              删除
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

    <!-- 业务类型选择器（支持鼠标滚轮滚动） -->
    <van-popup v-model:show="showBizTypePicker" position="bottom">
      <div class="picker-wheel-wrap" @wheel.prevent="onBizTypePickerWheel">
        <van-picker
          :model-value="[uploadForm.type]"
          :columns="bizTypePickerOptions"
          @confirm="onBizTypeConfirm"
          @cancel="showBizTypePicker = false"
        />
      </div>
    </van-popup>

    <!-- 归档失败弹窗（400 时展示 archiveBlockReason / keyMissing / blockedByStages / blockedByRequiredItems） -->
    <van-dialog v-model:show="showArchiveBlockDialog" title="无法归档" :show-confirm-button="true" confirm-button-text="知道了">
      <div class="archive-block-content">
        <p v-if="archiveBlockMessage" class="archive-block-reason">{{ archiveBlockMessage }}</p>
        <p v-if="archiveBlockData?.keyMissing?.length" class="archive-block-list">
          <strong>关键缺失：</strong>{{ archiveBlockData.keyMissing.join('、') }}
        </p>
        <p v-if="archiveBlockData?.blockedByStages?.length" class="archive-block-list">
          <strong>未完成阶段：</strong>{{ archiveBlockData.blockedByStages.join('、') }}
        </p>
      </div>
    </van-dialog>

    <!-- 申请归档前：存在草稿证据时确认弹窗 -->
    <van-dialog
      v-model:show="showDraftConfirmDialog"
      title="确认归档"
      show-cancel-button
      cancel-button-text="取消"
      confirm-button-text="确认归档"
      :before-close="onDraftArchiveConfirm"
    >
      <div class="draft-confirm-content">
        <p class="draft-confirm-tip">以下材料处于草稿状态，请确认是否需要调整。如不调整，草稿状态的材料将随项目一并归档（自动转为已归档状态）。</p>
        <ul v-if="draftListForArchive.length" class="draft-confirm-list">
          <li v-for="d in draftListForArchive" :key="d.evidenceId">
            {{ draftItemPathLabel(d) }}：{{ d.title || '未命名' }}
          </li>
        </ul>
        <p class="draft-confirm-ask">确认继续归档？</p>
      </div>
    </van-dialog>
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
  deleteEvidence,
  invalidateEvidence,
  type EvidenceListItem
} from '@/api/evidence'
import {
  getProjectDetail,
  getProjectMembers,
  getStageProgress,
  archiveProject,
  updateProject,
  getStructuredErrorData,
  type ProjectMemberVO,
  type StageProgressVO,
  type StageVO,
  type StageItemVO,
  type ArchiveBlockVO
} from '@/api/projects'
import { getEvidencesByStageType } from '@/api/evidence'
import { useAuthStore } from '@/stores/auth'
import { showConfirmDialog } from 'vant'
import { getEffectiveEvidenceStatus, mapStatusToText, statusTagType as evidenceStatusTagType } from '@/utils/evidenceStatus'
import { validateFileSize, isImageFile } from '@/utils/uploadFileLimit'
import { compressImageIfNeeded } from '@/utils/imageCompress'

interface Project {
  id: number
  code: string
  name: string
  description: string
  status: string
  createdAt: string
  /** 是否含采购（项目启动阶段「项目前期产品比测报告」勾选后为必填） */
  hasProcurement?: boolean
  permissions?: { canUpload?: boolean; canInvalidate?: boolean; canManageMembers?: boolean }
  canInvalidate?: boolean
  canUpload?: boolean
  canManageMembers?: boolean
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

// 阶段进度（stage-progress 唯一事实源）
const stageProgress = ref<StageProgressVO | null>(null)
const stageProgressLoading = ref(false)
const expandedStages = ref<string[]>([])
const openedStageCode = ref<string | null>(null)
const openedEvidenceTypeCode = ref<string | null>(null)
const evidenceByTypeList = ref<EvidenceListItem[]>([])
const evidenceByTypeLoading = ref(false)
/** 平铺模式：每个模板项的证据列表缓存 key = "stageCode:evidenceTypeCode" */
const evidenceByItemMap = ref<Record<string, EvidenceListItem[]>>({})
const evidenceByItemLoading = ref<Record<string, boolean>>({})
// 归档失败弹窗（400 结构化 data）
const showArchiveBlockDialog = ref(false)
const archiveBlockMessage = ref('')
const archiveBlockData = ref<ArchiveBlockVO | null>(null)
// 申请归档前草稿确认：存在草稿时展示列表，用户确认后再执行归档
const showDraftConfirmDialog = ref(false)
const draftListForArchive = ref<EvidenceListItem[]>([])

/** 上传上下文：从某阶段某模板项点击「上传」时带入，用于提交时带 stageId + evidenceTypeCode */
const uploadContext = ref<{ stageId: number; evidenceTypeCode: string; displayName: string } | null>(null)

// 项目成员列表（详情 Tab 展示）
const members = ref<ProjectMemberVO[]>([])
const membersLoading = ref(false)
const memberRoleLabels: Record<string, string> = {
  owner: '负责人',
  editor: '编辑',
  viewer: '查看'
}
/** 角色排序权重：负责人 > 编辑 > 查看 */
const memberRoleOrder: Record<string, number> = {
  owner: 0,
  editor: 1,
  viewer: 2
}
/** 按负责人、编辑、查看顺序排列的成员列表 */
const sortedMembers = computed(() => {
  return [...members.value].sort(
    (a, b) => (memberRoleOrder[a.role] ?? 99) - (memberRoleOrder[b.role] ?? 99)
  )
})
function memberRoleLabel(role: string): string {
  return memberRoleLabels[role] || role
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
const deleteLoading = ref(false)
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
const showUploadResultDelete = computed(() => getEffectiveEvidenceStatus(uploadResult.value) === 'DRAFT')
const showUploadResultInvalidate = computed(() => {
  return getEffectiveEvidenceStatus(uploadResult.value) === 'SUBMITTED' && project.value?.canInvalidate === true
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

/** 从某阶段某模板项的证据列表点击「上传」：带上阶段与类型，打开上传弹窗 */
function openUploadForStage(stage: StageVO) {
  const code = openedEvidenceTypeCode.value
  if (!code || stage.stageId == null) {
    showToast('无法获取阶段或类型信息')
    return
  }
  uploadContext.value = {
    stageId: stage.stageId,
    evidenceTypeCode: code,
    displayName: getOpenedItemDisplayName(stage)
  }
  openUploadDialog()
}

// 关闭上传弹窗并重置
function closeUploadDialog() {
  showUploadDialog.value = false
  uploadPhase.value = 'form'
  uploadResult.value = null
  uploadContext.value = null
  resetUploadForm()
}

// 跳转证据详情
function goToEvidenceDetail(id: number) {
  router.push({ path: `/evidence/detail/${id}`, query: { fromProject: String(projectId.value) } })
}

/** 网格内删除/作废证据：草稿物理删除，已提交则作废；刷新当前类别列表 */
async function onDeleteEvidence(ev: EvidenceListItem, stage?: StageVO, evidenceItem?: StageItemVO) {
  const id = ev.evidenceId
  const status = getEffectiveEvidenceStatus(ev)
  try {
    if (status === 'DRAFT') {
      await showConfirmDialog({ title: '确认删除', message: '删除后不可恢复，确定继续？' })
      const res = (await deleteEvidence(id)) as { code: number; message?: string }
      if (res?.code === 0) {
        showToast('已删除')
        if (stage?.stageCode && evidenceItem?.evidenceTypeCode) loadEvidenceForItem(stage.stageCode, evidenceItem.evidenceTypeCode)
        loadStageProgress()
      } else {
        showToast(res?.message || '删除失败')
      }
    } else {
      await showConfirmDialog({ title: '确认作废', message: '作废后不可恢复，确定继续？' })
      const res = (await invalidateEvidence(id, '用户从项目证据列表作废')) as { code: number; message?: string }
      if (res?.code === 0) {
        showToast('已作废')
        if (stage?.stageCode && evidenceItem?.evidenceTypeCode) loadEvidenceForItem(stage.stageCode, evidenceItem.evidenceTypeCode)
        loadStageProgress()
      } else {
        showToast(res?.message || '作废失败')
      }
    }
  } catch {
    // 用户取消
  }
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

/** 切换「是否含采购」后请求后端并刷新 */
async function onHasProcurementChange(value: boolean) {
  if (!projectId.value || !project.value) return
  try {
    const res = await updateProject(projectId.value, { hasProcurement: value })
    if (res?.code === 0 && res.data) {
      project.value.hasProcurement = res.data.hasProcurement ?? false
      showSuccessToast(value ? '已设为含采购' : '已设为不含采购')
      loadStageProgress()
    } else {
      showToast(res?.message || '更新失败')
    }
  } catch (e: any) {
    showToast(e?.message || '更新失败')
  }
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
        hasProcurement: p.hasProcurement ?? false,
        permissions: p.permissions,
        canInvalidate: p.canInvalidate ?? false,
        canManageMembers: p.canManageMembers ?? false,
        canUpload: p.canUpload ?? p.permissions?.canUpload ?? false,
        currentPmUserId: p.currentPmUserId,
        currentPmDisplayName: p.currentPmDisplayName
      }
      loadMembers()
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

// 加载项目成员列表（详情页展示）
const loadMembers = async () => {
  if (!projectId.value) return
  membersLoading.value = true
  try {
    const res = await getProjectMembers(projectId.value)
    if (res.code === 0 && res.data) members.value = res.data
    else members.value = []
  } catch {
    members.value = []
  } finally {
    membersLoading.value = false
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

/** 业务类型选择器滚轮：向上滚上一项，向下滚下一项 */
function onBizTypePickerWheel(e: WheelEvent) {
  const opts = bizTypePickerOptions
  const idx = opts.findIndex((c) => c.value === uploadForm.value.type)
  const cur = idx < 0 ? 0 : idx
  const next = e.deltaY > 0 ? cur + 1 : cur - 1
  const newIdx = Math.max(0, Math.min(next, opts.length - 1))
  if (newIdx === cur) return
  uploadForm.value.type = opts[newIdx].value
}

/** 选择文件前校验大小：图片 5MB、文档 50MB */
function onUploaderBeforeRead(file: File | File[]): boolean {
  const f = Array.isArray(file) ? file[0] : file
  if (!f) return true
  const r = validateFileSize(f)
  if (!r.ok) {
    showToast(r.message)
    return false
  }
  return true
}

// 上传证据（阶段驱动：必须从某模板项「上传」带入 stageId + evidenceTypeCode）
// 支持：图片前端轻度压缩 + 真实上传进度展示
const handleUpload = async () => {
  if (!uploadContext.value) {
    showToast('请先点击下方某证据类型（如「启动现场照片」）旁的「上传」按钮')
    return
  }
  if (!uploadForm.value.name.trim()) {
    showToast('请输入证据标题')
    return
  }

  if (uploadFileList.value.length === 0) {
    showToast('请选择要上传的文件')
    return
  }

  let file = uploadFileList.value[0].file as File | undefined
  if (!file || !(file instanceof File)) {
    showToast('文件无效')
    return
  }
  const sizeCheck = validateFileSize(file)
  if (!sizeCheck.ok) {
    showToast(sizeCheck.message)
    return
  }

  uploading.value = true
  try {
    // 仅对图片进行前端轻度压缩，PDF/Word 等直接上传
    if (isImageFile(file)) {
      showLoadingToast({ message: '图片压缩中...', forbidClick: true, duration: 0 })
      file = await compressImageIfNeeded(file)
      closeToast()
    }

    showLoadingToast({ message: '正在上传 0%...', forbidClick: true, duration: 0 })

    const formData = new FormData()
    formData.append('name', uploadForm.value.name)
    formData.append('stageId', String(uploadContext.value.stageId))
    formData.append('evidenceTypeCode', uploadContext.value.evidenceTypeCode)
    if (uploadForm.value.remark) {
      formData.append('remark', uploadForm.value.remark)
    }
    formData.append('file', file)

    const response = (await uploadEvidence(projectId.value, formData, {
      onUploadProgress(percent) {
        showLoadingToast({
          message: percent > 0 ? `正在上传 ${percent}%...` : '正在上传...',
          forbidClick: true,
          duration: 0
        })
      }
    })) as {
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
      refreshCurrentItemEvidences()
      loadStageProgress()
    } else {
      showToast(response.message || '上传失败')
    }
  } catch (error: any) {
    closeToast()
    console.error('Upload error:', error)
    showToast(error?.message || '上传失败')
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
      // 刷新证据 Tab 中当前模板项列表与阶段进度，使「草稿」及时变为「已提交」
      refreshCurrentItemEvidences()
      loadStageProgress()
    } else {
      showToast(res?.message || '提交失败')
    }
  } catch (e: any) {
    showToast(e?.message || '提交失败')
  } finally {
    submitLoading.value = false
  }
}

// 阶段2：草稿物理删除
async function handleUploadResultDelete() {
  if (!uploadResult.value || getEffectiveEvidenceStatus(uploadResult.value) !== 'DRAFT') return
  try {
    await showConfirmDialog({ title: '确认删除', message: '删除后不可恢复，是否继续？' })
  } catch {
    return
  }
  const id = uploadResult.value.evidenceId
  deleteLoading.value = true
  try {
    const res = (await deleteEvidence(id)) as { code: number; message?: string }
    if (res?.code === 0) {
      showSuccessToast('已删除')
      closeUploadDialog()
      onRefresh()
      refreshCurrentItemEvidences()
      loadStageProgress()
    } else {
      showToast(res?.message || '删除失败')
    }
  } catch (e: any) {
    showToast(e?.message || '删除失败')
  } finally {
    deleteLoading.value = false
  }
}

// 阶段2：作废（先弹出填写原因，仅已提交可作废）
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
      refreshCurrentItemEvidences()
      loadStageProgress()
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

// 阶段进度加载
async function loadStageProgress() {
  if (!projectId.value) return
  stageProgressLoading.value = true
  try {
    const res = await getStageProgress(projectId.value)
    if (res?.code === 0 && res.data) {
      stageProgress.value = res.data
      loadAllItemEvidences()
    } else {
      stageProgress.value = null
    }
  } catch {
    stageProgress.value = null
  } finally {
    stageProgressLoading.value = false
  }
}

function healthTagType(health: string): 'success' | 'warning' | 'default' {
  if (health === 'COMPLETE') return 'success'
  if (health === 'PARTIAL') return 'warning'
  return 'default'
}

/** 阶段健康状态中文展示 */
function healthStatusText(health: string): string {
  if (health === 'COMPLETE') return '已完成'
  if (health === 'PARTIAL') return '进行中'
  if (health === 'NOT_STARTED') return '未开始'
  return health || '—'
}

function getOpenedItemDisplayName(stage: StageVO): string {
  const code = openedEvidenceTypeCode.value
  if (!code || !stage.items) return ''
  const item = stage.items.find((i) => i.evidenceTypeCode === code)
  return item ? (item.groupDisplayName || item.displayName || code) : code
}

function toggleEvidenceList(stageCode: string, item: StageItemVO) {
  const code = item.evidenceTypeCode
  if (!code) return
  if (openedStageCode.value === stageCode && openedEvidenceTypeCode.value === code) {
    openedStageCode.value = null
    openedEvidenceTypeCode.value = null
    evidenceByTypeList.value = []
    return
  }
  openedStageCode.value = stageCode
  openedEvidenceTypeCode.value = code
  loadEvidenceByType()
}

async function loadEvidenceByType() {
  if (!projectId.value || !openedStageCode.value || !openedEvidenceTypeCode.value) return
  evidenceByTypeLoading.value = true
  try {
    const res = await getEvidencesByStageType(projectId.value, openedStageCode.value, openedEvidenceTypeCode.value)
    if (res?.code === 0 && Array.isArray(res.data)) {
      evidenceByTypeList.value = res.data
    } else {
      evidenceByTypeList.value = []
    }
  } catch {
    evidenceByTypeList.value = []
  } finally {
    evidenceByTypeLoading.value = false
  }
}

/** 平铺模式：为某模板项加载证据列表 */
async function loadEvidenceForItem(stageCode: string, evidenceTypeCode: string) {
  if (!projectId.value || !stageCode || !evidenceTypeCode) return
  const mapKey = `${stageCode}:${evidenceTypeCode}`
  evidenceByItemLoading.value[mapKey] = true
  try {
    const res = await getEvidencesByStageType(projectId.value, stageCode, evidenceTypeCode)
    if (res?.code === 0 && Array.isArray(res.data)) {
      evidenceByItemMap.value[mapKey] = res.data
    } else {
      evidenceByItemMap.value[mapKey] = []
    }
  } catch {
    evidenceByItemMap.value[mapKey] = []
  } finally {
    evidenceByItemLoading.value[mapKey] = false
  }
}

/** 阶段进度加载完成后，自动加载所有模板项的证据列表 */
function loadAllItemEvidences() {
  if (!stageProgress.value?.stages) return
  for (const s of stageProgress.value.stages) {
    for (const item of (s.items || [])) {
      if (item.evidenceTypeCode) {
        loadEvidenceForItem(s.stageCode, item.evidenceTypeCode)
      }
    }
  }
}

/** 上传成功后刷新对应模板项 */
function refreshCurrentItemEvidences() {
  if (uploadContext.value) {
    const stageCode = stageProgress.value?.stages?.find(s => s.stageId === uploadContext.value?.stageId)?.stageCode
    if (stageCode && uploadContext.value.evidenceTypeCode) {
      loadEvidenceForItem(stageCode, uploadContext.value.evidenceTypeCode)
    }
  }
}

function getItemEvidences(stageCode: string, evidenceTypeCode: string): EvidenceListItem[] {
  return evidenceByItemMap.value[`${stageCode}:${evidenceTypeCode}`] || []
}
function isItemLoading(stageCode: string, evidenceTypeCode: string): boolean {
  return !!evidenceByItemLoading.value[`${stageCode}:${evidenceTypeCode}`]
}

/** 判断是否为图片类型 */
function isImageType(contentType?: string): boolean {
  return !!contentType && contentType.startsWith('image/')
}

/** 文件类型图标配置 */
interface FileIconConfig {
  icon: string      // Vant icon name
  label: string     // 右上角文字标签（如 PDF、DOC）
  color: string     // 图标颜色
  bg: string        // 卡片背景色
}

function getFileIconConfig(fileName?: string): FileIconConfig {
  const ext = (fileName || '').split('.').pop()?.toLowerCase() || ''
  switch (ext) {
    case 'pdf':
      return { icon: 'description', label: 'PDF', color: '#ee4d2d', bg: '#fff1f0' }
    case 'doc':
    case 'docx':
      return { icon: 'description', label: 'DOC', color: '#1989fa', bg: '#e8f4ff' }
    case 'xls':
    case 'xlsx':
    case 'csv':
      return { icon: 'bar-chart-o', label: 'XLS', color: '#07c160', bg: '#eefbf3' }
    case 'ppt':
    case 'pptx':
      return { icon: 'photo-o', label: 'PPT', color: '#ff976a', bg: '#fff7e8' }
    case 'zip':
    case 'rar':
    case '7z':
    case 'tar':
    case 'gz':
      return { icon: 'gift-o', label: 'ZIP', color: '#faad14', bg: '#fffbe6' }
    case 'txt':
    case 'md':
      return { icon: 'notes-o', label: 'TXT', color: '#969799', bg: '#f7f8fa' }
    default:
      return { icon: 'description', label: ext.toUpperCase() || '?', color: '#969799', bg: '#f7f8fa' }
  }
}

function getEvidenceFileName(ev: EvidenceListItem): string {
  return ev.latestVersion?.originalFilename || ev.title || ''
}

/** 从某阶段某模板项直接点上传 */
function openUploadForItem(stage: StageVO, item: StageItemVO) {
  if (!item.evidenceTypeCode || stage.stageId == null) {
    showToast('无法获取阶段或类型信息')
    return
  }
  uploadContext.value = {
    stageId: stage.stageId,
    evidenceTypeCode: item.evidenceTypeCode,
    displayName: item.groupDisplayName || item.displayName || item.evidenceTypeCode
  }
  openUploadDialog()
}

/** 实际执行归档请求（供 handleArchive 与草稿确认弹窗确认后调用） */
async function doArchive(): Promise<boolean> {
  if (!projectId.value) return false
  try {
    const res = await archiveProject(projectId.value)
    if (res?.code === 0) {
      showSuccessToast('归档成功')
      loadProject()
      loadStageProgress()
      return true
    }
    if (res?.code === 400 && res.data) {
      archiveBlockMessage.value = (res as { message?: string }).message ?? '不满足归档条件'
      archiveBlockData.value = res.data as ArchiveBlockVO
      showArchiveBlockDialog.value = true
    } else {
      showToast((res as { message?: string })?.message ?? '归档失败')
    }
    return false
  } catch (err: unknown) {
    const structured = getStructuredErrorData(err as { response?: { data?: { code?: number; data?: unknown; message?: string } } })
    if (structured?.data) {
      archiveBlockMessage.value = structured.message
      archiveBlockData.value = structured.data as ArchiveBlockVO
      showArchiveBlockDialog.value = true
    } else {
      showToast((err as Error)?.message ?? '归档失败')
    }
    return false
  }
}

/** 申请归档：若有草稿则先弹窗列出草稿并确认，确认后再归档；无草稿则直接归档 */
async function handleArchive() {
  if (!projectId.value || !stageProgress.value?.canArchive || project.value?.status === 'archived') return
  try {
    const draftRes = await getEvidenceList(projectId.value, { evidenceStatus: 'DRAFT' })
    const drafts = (draftRes?.data ?? []) as EvidenceListItem[]
    if (drafts.length > 0) {
      draftListForArchive.value = drafts
      showDraftConfirmDialog.value = true
      return
    }
    await doArchive()
  } catch {
    showToast('获取证据列表失败')
  }
}

/** 草稿项展示路径：大阶段 > 子阶段（便于定位） */
function draftItemPathLabel(d: EvidenceListItem): string {
  const stage = d.stageName || d.stageCode || (d.stageId != null ? `阶段${d.stageId}` : '')
  const typeName = d.evidenceTypeDisplayName || d.evidenceTypeCode || ''
  if (stage && typeName) return `${stage} > ${typeName}`
  if (stage) return stage
  if (typeName) return typeName
  return '未分类'
}

/** 草稿确认弹窗：用户点击「确认归档」时执行归档并关闭弹窗 */
async function onDraftArchiveConfirm(action: string): Promise<boolean> {
  if (action !== 'confirm') return true
  const ok = await doArchive()
  if (ok) {
    draftListForArchive.value = []
    return true
  }
  return false
}

// 监听Tab切换
watch(activeTab, (newVal) => {
  if (newVal === 1) {
    loadStageProgress()
    if (evidenceList.value.length === 0) loadEvidenceList()
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
  loadStageProgress()
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
  background: var(--bg-body);
}

.content {
  padding: 0 16px 24px;
}

/* ---------- 顶部摘要卡片 ---------- */
.detail-header-card {
  background: var(--bg-card);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-card-shadow);
  padding: 20px 16px;
  margin-bottom: 12px;
}
.detail-header-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-main);
  margin: 0 0 12px 0;
  line-height: 1.3;
}
.detail-header-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.detail-header-badge {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 500;
}
.detail-header-badge.badge--active {
  background: rgba(0, 122, 255, 0.12);
  color: var(--primary-color);
}
.detail-header-badge.badge--archived {
  background: #ebedf0;
  color: var(--app-text-secondary);
}
.detail-header-responsible {
  font-size: 13px;
  color: var(--app-text-secondary);
}

/* ---------- 分段控制器 ---------- */
.segmented-control {
  display: flex;
  background: var(--bg-card);
  border-radius: var(--app-card-radius);
  padding: 4px;
  margin-bottom: 12px;
  box-shadow: var(--app-card-shadow);
}
.segmented-item {
  flex: 1;
  min-height: 44px;
  border: none;
  background: transparent;
  font-size: 15px;
  color: var(--app-text-secondary);
  position: relative;
  border-radius: 10px;
  transition: color 0.2s, font-weight 0.2s;
}
.segmented-item.active {
  color: var(--primary-color);
  font-weight: 600;
  background: rgba(0, 122, 255, 0.08);
}
.segmented-item.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 24px;
  height: 3px;
  border-radius: 2px;
  background: var(--primary-color);
}

/* 隐藏默认 Tab 栏，仅保留内容 */
.tabs-custom-nav :deep(.van-tabs__nav) {
  display: none;
}
.tabs-custom-nav :deep(.van-tabs__content) {
  margin-top: 0;
}

/* ---------- 信息列表区（左右布局、分割线） ---------- */
.info-card {
  background: var(--bg-card);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-card-shadow);
  overflow: hidden;
  margin-bottom: 16px;
}
.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  padding: 0 16px;
  border-bottom: 1px solid #ebedf0;
}
.info-row:last-child {
  border-bottom: none;
}
.info-label {
  font-size: 14px;
  color: var(--app-text-secondary);
  flex-shrink: 0;
  margin-right: 12px;
}
.info-value {
  font-size: 14px;
  color: var(--text-main);
  text-align: right;
  word-break: break-all;
}
.info-badge {
  padding: 2px 10px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
}
.info-badge.badge--active {
  background: rgba(0, 122, 255, 0.12);
  color: var(--primary-color);
}
.info-badge.badge--archived {
  background: #ebedf0;
  color: var(--app-text-secondary);
}

.members-section {
  margin-top: 16px;
  padding: 0 16px 16px;
  text-align: center;
}
.members-section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--van-text-color);
  margin-bottom: 8px;
  text-align: center;
}
.members-loading {
  padding: 16px 0;
}

/* 成员角色以描述文字展示，避免像按钮 */
.member-role-desc {
  font-size: 14px;
  color: var(--van-text-color);
}

.evidence-section {
  padding: 0;
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

.picker-wheel-wrap {
  touch-action: pan-y;
}

/* 阶段进度顶部（证据 Tab + 详情 Tab 共用） */
.stage-progress-header {
  padding: 12px 16px;
  background: var(--bg-card);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-card-shadow);
  margin: 0 0 12px 0;
}
.detail-tab-progress {
  margin-top: 16px;
}
.completion-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.completion-label {
  font-size: 14px;
  color: var(--van-gray-7);
  min-width: 72px;
}
.completion-row .van-progress {
  flex: 1;
}
.completion-value {
  font-size: 14px;
  font-weight: 500;
  min-width: 36px;
}
.key-missing-row {
  font-size: 12px;
  color: var(--van-gray-6);
  margin-bottom: 6px;
}
.key-missing-list {
  word-break: break-all;
}
.archive-row {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.archive-block-tip {
  font-size: 12px;
  color: var(--van-gray-6);
  flex: 1;
}
.stage-progress-loading {
  padding: 24px;
}

/* 阶段折叠 */
.stage-collapse {
  margin: 0 0 16px 0;
}
.stage-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.stage-name {
  font-weight: 500;
}
.stage-count {
  font-size: 13px;
  color: var(--van-gray-6);
}
/* 平铺卡片模式 */
.stage-items-flat {
  padding: 4px 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.evidence-card {
  background: var(--bg-card);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-card-shadow);
  padding: 14px 16px;
  border: none;
}
.evidence-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  min-height: 44px;
}
.evidence-card-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  flex: 1;
  min-width: 0;
}
.card-name {
  color: var(--text-main);
}
.card-required {
  flex-shrink: 0;
}
.evidence-card-status {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.card-status-text {
  font-size: 13px;
  color: var(--app-text-secondary);
}
.card-status-text.status--pending {
  color: #ee0a24;
}
.card-arrow {
  color: var(--app-text-secondary);
  font-size: 14px;
}
.card-count {
  font-size: 13px;
  color: var(--app-text-secondary);
}
/* 固定 112px 方块 + flex-wrap 自动排列 */
.evidence-card-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.grid-item {
  position: relative;
  width: 112px;
  height: 112px;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--van-gray-1);
  border: 1px solid var(--van-gray-2);
  flex-shrink: 0;
}
.grid-thumb {
  width: 100%;
  height: 100%;
  position: relative;
}
.grid-thumb {
  border-radius: 10px;
  overflow: hidden;
}
.grid-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.grid-thumb-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 2px 4px;
  background: linear-gradient(transparent, rgba(0,0,0,0.4));
  display: flex;
  justify-content: flex-end;
}
.grid-thumb-delete {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
  cursor: pointer;
}
.grid-thumb-delete:active {
  background: rgba(0, 0, 0, 0.7);
}
.grid-file {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 6px;
  width: 100%;
  height: 100%;
  border-radius: 6px;
  transition: background 0.2s;
}
.grid-file-type-badge {
  position: absolute;
  top: 4px;
  right: 4px;
  color: #fff;
  font-size: 9px;
  font-weight: 600;
  line-height: 1;
  padding: 2px 4px;
  border-radius: 3px;
  letter-spacing: 0.5px;
}
.grid-file-name {
  font-size: 10px;
  color: var(--text-main);
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
  display: block;
  margin-top: 2px;
  font-weight: 500;
}
.grid-file-delete {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.4);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
  cursor: pointer;
}
.grid-file-delete:active {
  background: rgba(0, 0, 0, 0.6);
}
/* 上传入口：虚线框 */
.grid-upload-btn {
  border: 2px dashed var(--van-gray-4);
  background: transparent;
  flex-direction: column;
  gap: 4px;
  transition: border-color 0.2s, background 0.2s;
}
.grid-upload-btn:active {
  border-color: var(--van-primary-color);
  background: rgba(25, 137, 250, 0.06);
}
.grid-upload-text {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.upload-context-cell {
  background: var(--van-gray-1);
}
.upload-hint-cell .upload-hint-text {
  font-size: 12px;
  color: var(--van-gray-6);
}
.archive-block-content {
  padding: 16px;
  font-size: 14px;
}
.archive-block-reason {
  margin-bottom: 8px;
  color: var(--van-gray-8);
}
.archive-block-list {
  margin: 6px 0;
  font-size: 13px;
  color: var(--van-gray-7);
}
.draft-confirm-content {
  padding: 16px;
  font-size: 14px;
}
.draft-confirm-tip {
  margin-bottom: 12px;
  color: var(--van-gray-8);
  line-height: 1.5;
}
.draft-confirm-list {
  margin: 8px 0;
  padding-left: 20px;
  color: var(--van-gray-7);
  font-size: 13px;
  max-height: 160px;
  overflow-y: auto;
}
.draft-confirm-list li {
  margin: 4px 0;
}
.draft-confirm-ask {
  margin-top: 12px;
  color: var(--van-gray-8);
}
</style>
