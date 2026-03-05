<template>
  <div class="project-page-container">
    <div class="unified-header">
      <van-search
        v-model="searchKeyword"
        placeholder="请输入项目名称或项目编号"
        shape="round"
        background="transparent"
        input-align="left"
        class="flex-search"
        @update:model-value="onSearchInput"
        @search="fetchProjectList"
      />
      <van-dropdown-menu class="flex-filter">
        <van-dropdown-item v-model="projectStatus" title="状态" :options="statusOptions" @change="fetchProjectList" />
      </van-dropdown-menu>
    </div>

    <!-- 待办看板：项目列表顶部工作台 -->
    <div
      v-if="todoSummary && (todoSummary.returnedCount > 0 || todoSummary.pendingApprovalCount > 0)"
      class="todo-board"
    >
      <!-- 被退回待整改 -->
      <button
        v-if="todoSummary.returnedCount > 0"
        type="button"
        class="todo-card todo-card--danger"
        :class="{ 'todo-card--active': activeTodoFilter === 'returned' }"
        @click="handleTodoCardClick('returned')"
      >
        <span class="todo-card-title">被退回待整改</span>
        <span class="todo-card-count">{{ todoSummary.returnedCount }}</span>
      </button>

      <!-- 待我审批 -->
      <button
        v-if="todoSummary.pendingApprovalCount > 0"
        type="button"
        class="todo-card todo-card--primary"
        :class="{ 'todo-card--active': activeTodoFilter === 'pending_approval' }"
        @click="handleTodoCardClick('pending_approval')"
      >
        <span class="todo-card-title">{{ pendingApprovalCardTitle }}</span>
        <span class="todo-card-count">{{ todoSummary.pendingApprovalCount }}</span>
      </button>
    </div>

    <!-- 操作栏 -->
    <div class="action-bar">
      <div class="action-bar-scroll">
        <van-button
          v-if="canCreateProject"
          type="primary"
          size="small"
          class="action-btn action-btn--primary"
          @click="showCreate = true"
        >
          新建项目
        </van-button>
        <van-button
          v-if="canImportProjects"
          plain
          size="small"
          class="action-btn"
          @click="showImport = true"
        >
          批量导入
        </van-button>
        <van-button
          v-if="canBatchAssign"
          plain
          size="small"
          class="action-btn"
          @click="go('/batch-assign-projects')"
        >
          批量分配项目
        </van-button>
      </div>
    </div>

    <div class="project-list-wrapper">
        <van-pull-refresh v-model="loading" @refresh="onRefresh">
          <van-list
            v-model:loading="listLoading"
            :finished="finished"
            finished-text="没有更多了"
            @load="onLoad"
          >
            <div
              v-for="project in projects"
              :key="project.id"
              class="project-card"
              :class="{ 'project-card--pending-approval': isPMOOrAdmin && project.status === 'pending_approval' }"
              @click="goToDetail(project.id)"
            >
              <div class="project-card-body">
                <div class="project-card-header">
                  <h3 class="project-card-title" :title="project.name">{{ project.name }}</h3>
                  <span class="project-card-badge-wrap">
                    <span class="project-card-badge" :class="projectStatusBadgeClass(project.status)">
                      {{ projectStatusText(project.status) }}
                    </span>
                    <van-badge
                      v-if="isPMOOrAdmin && project.status === 'pending_approval'"
                      dot
                      class="project-card-pending-dot"
                    />
                  </span>
                </div>
                <div v-if="project.description" class="project-desc">{{ project.description }}</div>
                <div class="project-meta-row">
                  <span class="project-pm" :class="project.currentPmDisplayName ? 'project-pm-assigned' : 'project-pm-unassigned'">
                    项目经理：{{ project.currentPmDisplayName || '未分配' }}
                  </span>
                  <span v-if="project.createdByDisplayName" class="project-creator">创建人：{{ project.createdByDisplayName }}</span>
                </div>
              </div>
              <div class="project-card-actions">
                <button
                  type="button"
                  class="card-action-upload card-action-upload--small"
                  @click.stop="goToEvidenceTab(project)"
                >
                  <span class="card-action-upload-icon">📤</span>
                  <span>上传证据</span>
                </button>
              </div>
            </div>
            <van-empty v-if="!loading && !listLoading && listError" :description="listError" />
            <van-empty v-else-if="!loading && !listLoading && projects.length === 0" description="暂无项目" />
          </van-list>
        </van-pull-refresh>
    </div>

    <van-popup v-model:show="showCreate" position="bottom" round :style="{ maxHeight: '88vh', padding: '0' }">
      <div class="create-form">
        <div class="create-form-scroll">
          <h3 class="form-title">新建项目</h3>
          <van-form id="create-project-form" @submit="onCreateSubmit">
            <div class="form-card-group">
              <span class="form-group-label">基础信息</span>
              <van-cell-group inset class="form-card-style">
                <van-field
                  v-model="createForm.code"
                  name="code"
                  label="项目令号"
                  placeholder="请输入项目令号"
                  :rules="[{ required: true, message: '请输入项目令号' }]"
                />
                <van-field
                  v-model="createForm.name"
                  name="name"
                  label="项目名称"
                  placeholder="请输入项目名称"
                  :rules="[{ required: true, message: '请输入项目名称' }]"
                />
                <van-field
                  v-model="createForm.description"
                  name="description"
                  label="项目描述"
                  type="textarea"
                  placeholder="选填"
                  rows="2"
                />
                <van-field name="hasProcurement" label="是否含采购">
                  <template #input>
                    <van-switch v-model="createForm.hasProcurement" size="22" />
                  </template>
                </van-field>
              </van-cell-group>
            </div>
          </van-form>
        </div>
        <div class="fixed-submit-wrap">
          <van-button type="primary" native-type="submit" form="create-project-form" :loading="createLoading" block>创建项目</van-button>
          <van-button block plain class="mt" @click="showCreate = false">取消</van-button>
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="showImport" position="bottom" round :style="{ padding: '16px', maxHeight: '80vh' }">
      <div class="import-form">
        <h3 class="form-title">批量导入项目</h3>
        <p class="import-tip">仅 系统管理员 / PMO 可导入。模板列：项目令号、项目名称、项目描述。</p>
        <a :href="importTemplateUrl" target="_blank" rel="noopener" class="download-link">下载模板</a>
        <van-field name="file" label="选择文件">
          <template #input>
            <input type="file" accept=".xlsx,.xls" @change="onImportFileChange" />
          </template>
        </van-field>
        <van-button block type="primary" :loading="importLoading" :disabled="!importFile" @click="onImportSubmit">上传导入</van-button>
        <div v-if="importResult" class="import-result">
          <p>共 {{ importResult.total }} 行，成功 {{ importResult.successCount }}，失败 {{ importResult.failCount }}</p>
          <div v-if="importResult.details?.length" class="import-details">
            <div v-for="d in importResult.details" :key="d.row" class="detail-row" :class="{ fail: !d.success }">
              第{{ d.row }}行 {{ d.code }} {{ d.success ? '✓' : '✗' }} {{ d.message }}
            </div>
          </div>
        </div>
        <van-button block plain class="mt" @click="showImport = false">关闭</van-button>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Badge, Button, Cell, List, Popup, Field, Form, CellGroup, PullRefresh, Tag, Switch, Search, DropdownMenu, DropdownItem } from 'vant'
import { createProject, getProjects, importProjects, getProjectImportTemplateUrl, getProjectTodoSummary, type ProjectVO, type ProjectImportResult, type ProjectTodoSummaryVO } from '@/api/projects'
import { useAuthStore } from '@/stores/auth'
import { showToast } from 'vant'
import { getFriendlyErrorMessage } from '@/utils/errorMessage'

interface Project {
  id: number
  code: string
  name: string
  description: string
  status: string
  currentPmDisplayName?: string
}

const router = useRouter()
const auth = useAuthStore()
/** 仅管理员、PMO 可新建项目 */
const canCreateProject = computed(() => {
  const code = auth.currentUser?.roleCode
  return code === 'SYSTEM_ADMIN' || code === 'PMO'
})
const canImportProjects = computed(() => {
  const code = auth.currentUser?.roleCode
  return code === 'SYSTEM_ADMIN' || code === 'PMO'
})
/** 批量分配项目（从原首页迁移至项目页顶部） */
const canBatchAssign = computed(() => {
  const code = auth.currentUser?.roleCode
  return code === 'SYSTEM_ADMIN' || code === 'PMO'
})
/** PMO 或系统管理员（待审批项目需在列表显眼提示） */
const isPMOOrAdmin = computed(() => {
  const code = auth.currentUser?.roleCode
  return code === 'PMO' || code === 'SYSTEM_ADMIN'
})

/** 顶部待办看板中「待审批」卡片标题：PMO/管理员显示“待我审批”，项目经理等其它角色显示“审批中” */
const pendingApprovalCardTitle = computed(() => {
  return isPMOOrAdmin.value ? '待我审批' : '审批中'
})

/** 项目状态展示文案（与详情页一致） */
function projectStatusText(status: string): string {
  if (status === 'active') return '进行中'
  if (status === 'pending_approval') return '待审批'
  if (status === 'returned') return '已退回'
  if (status === 'archived') return '已归档'
  return status || '—'
}

/** 项目状态标签样式类：active 蓝 / pending_approval 橙 / returned 红 / archived 灰 */
function projectStatusBadgeClass(status: string): string {
  if (status === 'active') return 'badge--active'
  if (status === 'pending_approval') return 'badge--pending'
  if (status === 'returned') return 'badge--returned'
  if (status === 'archived') return 'badge--archived'
  return 'badge--archived'
}

function go(path: string) {
  router.push(path)
}
const importTemplateUrl = getProjectImportTemplateUrl()

const loading = ref(false)
const listLoading = ref(false)
const finished = ref(false)
const projects = ref<Project[]>([])
const listError = ref('')

// 待办看板：汇总数据与激活状态
const todoSummary = ref<ProjectTodoSummaryVO | null>(null)
const todoLoading = ref(false)
const activeTodoFilter = ref<'returned' | 'pending_approval' | null>(null)

// 搜索与筛选（统一由 fetchProjectList 请求）
const searchKeyword = ref('')
const projectStatus = ref<string | number>('')
const statusOptions = [
  { text: '全部状态', value: '' },
  { text: '进行中', value: 'active' },
  { text: '待审批', value: 'pending_approval' },
  { text: '已退回', value: 'returned' },
  { text: '已归档', value: 'archived' }
]

const SEARCH_DEBOUNCE_MS = 500
let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null
function onSearchInput() {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(() => {
    searchDebounceTimer = null
    fetchProjectList()
  }, SEARCH_DEBOUNCE_MS)
}

/**
 * 统一查询入口：搜索关键词、状态筛选、回车搜索、下拉切换状态 均调用此方法。
 * 当前为 Mock 逻辑：打印参数后请求 getProjects，再按 keyword/status 做前端过滤。
 * 若后端支持 query 参数，可改为 getProjects({ keyword: searchKeyword.value, status: projectStatus.value })。
 */
async function fetchProjectList() {
  const keyword = searchKeyword.value.trim()
  const status = projectStatus.value === '' ? '' : String(projectStatus.value)
  // 用户手动通过下拉修改状态、与当前激活的待办过滤不一致时，清空卡片高亮
  if (activeTodoFilter.value && status !== activeTodoFilter.value) {
    activeTodoFilter.value = null
  }

  // Mock：代表发送给后端的参数
  console.log('[fetchProjectList] params:', { searchKeyword: keyword, projectStatus: status })

  listError.value = ''
  listLoading.value = true
  loading.value = true
  try {
    const res = await getProjects()
    const raw = res?.data
    const list = Array.isArray(raw) ? raw : []
    if (res?.code !== 0) {
      listError.value = res?.message || '加载失败'
      projects.value = []
      return
    }
    let mapped: Project[] = list.map((p: ProjectVO) => ({
      id: p.id ?? 0,
      code: p.code ?? '',
      name: p.name ?? '',
      description: p.description ?? '',
      status: p.status ?? 'active',
      currentPmDisplayName: p.currentPmDisplayName ?? undefined
    }))
    if (keyword) {
      const k = keyword.toLowerCase()
      mapped = mapped.filter((p) => (p.name || '').toLowerCase().includes(k) || (p.code || '').toLowerCase().includes(k))
    }
    if (status) {
      mapped = mapped.filter((p) => (p.status ?? '') === status)
    }
    projects.value = mapped
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : (e as { message?: string })?.message
    listError.value = msg || '加载失败'
    projects.value = []
  } finally {
    finished.value = true
    listLoading.value = false
    loading.value = false
  }
}

const showCreate = ref(false)
const createLoading = ref(false)
const createForm = ref({ code: '', name: '', description: '', hasProcurement: true })

const showImport = ref(false)
const importFile = ref<File | null>(null)
const importLoading = ref(false)
const importResult = ref<ProjectImportResult | null>(null)
function onImportFileChange(e: Event) {
  const target = e.target as HTMLInputElement
  importFile.value = target.files?.[0] ?? null
  importResult.value = null
}
async function onImportSubmit() {
  if (!importFile.value) return
  importLoading.value = true
  importResult.value = null
  try {
    const res = await importProjects(importFile.value)
    if (res.code === 0 && res.data) {
      importResult.value = res.data
      showToast(res.data.failCount === 0 ? '导入完成' : `成功 ${res.data.successCount}，失败 ${res.data.failCount}`)
      if (res.data.successCount > 0) loadProjects()
    } else {
      showToast(res.message || '导入失败')
    }
  } catch (e: unknown) {
    showToast(getFriendlyErrorMessage(e, '导入失败'))
  } finally {
    importLoading.value = false
  }
}

const loadProjects = () => {
  finished.value = false
  // 列表与待办看板一起刷新
  Promise.all([fetchProjectList(), fetchTodoSummary()]).catch(() => {})
}

const onRefresh = () => {
  finished.value = false
  Promise.all([fetchProjectList(), fetchTodoSummary()]).catch(() => {})
}

const onLoad = () => {
  if (projects.value.length === 0 && !listError.value) {
    fetchProjectList()
  } else {
    finished.value = true
    listLoading.value = false
  }
}

const goToDetail = (id: number) => {
  router.push(`/projects/${id}`)
}

const goToEvidenceTab = (project: Project) => {
  router.push({ path: `/projects/${project.id}`, query: { tab: 'evidence' } })
}

const onCreateSubmit = async () => {
  const code = createForm.value.code?.trim()
  const name = createForm.value.name?.trim()
  if (!code) {
    showToast('请输入项目令号')
    return
  }
  if (!name) {
    showToast('请输入项目名称')
    return
  }
  createLoading.value = true
  try {
    const res = await createProject({
      code,
      name,
      description: createForm.value.description?.trim() || undefined,
      hasProcurement: createForm.value.hasProcurement
    })
    if (res?.code !== 0) {
      showToast(res?.message || (res?.code === 403 ? '仅管理员或 PMO 可创建项目' : '创建失败'))
      return
    }
    const data = res?.data as ProjectVO | undefined
    if (!data?.id) {
      showToast('创建成功，请刷新列表')
      showCreate.value = false
      loadProjects()
      return
    }
    showCreate.value = false
    createForm.value = { code: '', name: '', description: '', hasProcurement: true }
    projects.value = [
      { id: data.id, code: data.code ?? code, name: data.name ?? name, description: data.description ?? '', status: data.status ?? 'active', currentPmDisplayName: data.currentPmDisplayName },
      ...projects.value
    ]
    showToast('创建成功')
    router.push(`/projects/${data.id}`)
  } catch (e: unknown) {
    showToast(getFriendlyErrorMessage(e, '创建失败'))
  } finally {
    createLoading.value = false
  }
}

onMounted(() => {
  loadProjects()
})

/** 拉取待办汇总 */
async function fetchTodoSummary() {
  todoLoading.value = true
  try {
    const res = await getProjectTodoSummary()
    if (res.code === 0 && res.data) {
      todoSummary.value = res.data
    } else {
      todoSummary.value = { returnedCount: 0, pendingApprovalCount: 0 }
    }
  } catch (e) {
    console.warn('fetchTodoSummary failed', e)
    todoSummary.value = { returnedCount: 0, pendingApprovalCount: 0 }
  } finally {
    todoLoading.value = false
  }
}

/** 点击待办卡片：切换过滤 */
function handleTodoCardClick(status: 'returned' | 'pending_approval') {
  if (activeTodoFilter.value === status) {
    // 再次点击：取消过滤，回到全部状态
    activeTodoFilter.value = null
    projectStatus.value = ''
  } else {
    activeTodoFilter.value = status
    projectStatus.value = status
  }
  fetchProjectList()
}
</script>

<style scoped>
/* 页面：浅灰底色；搜索区+卡片：纯白。顶部仅留 6px 与导航栏间距，对齐证据管理页紧凑感 */
.project-page-container {
  min-height: 100vh;
  background: #eef0f3;
  padding: 6px 16px 16px;
}

.action-bar {
  margin-top: 0;
  margin-bottom: 8px;
  -webkit-overflow-scrolling: touch;
}
.action-bar-scroll {
  display: flex;
  align-items: center;
  gap: 10px;
  overflow-x: auto;
  padding: 2px 0;
  min-height: var(--app-tap-min-height);
}
.action-bar-scroll::-webkit-scrollbar {
  display: none;
}
.action-btn {
  flex-shrink: 0;
  min-height: 40px;
  padding: 0 16px;
}
.action-btn--primary {
  background: var(--app-primary) !important;
  border-color: var(--app-primary) !important;
}

/* 搜索+筛选：同一行、紧凑、纯白背景，底部无外边距（强制覆盖） */
.unified-header {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fff;
  border-radius: 12px;
  padding: 8px 16px;
  margin-top: 0;
  margin-bottom: 0 !important;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}
.flex-search {
  flex: 1;
  min-width: 0;
  padding: 0;
}
.flex-search :deep(.van-search) {
  padding: 0;
  background: transparent;
}
.flex-search :deep(.van-search__content) {
  background: #f5f6f8;
  border: none;
  border-radius: 20px;
  min-height: 36px;
}
.flex-search :deep(.van-field__control) {
  text-align: left;
}
.flex-filter {
  flex-shrink: 0;
}
.flex-filter :deep(.van-dropdown-menu__bar) {
  height: 36px;
  min-height: 36px;
  background: #f5f6f8;
  border: none;
  border-radius: 20px;
  box-shadow: none;
}
.flex-filter :deep(.van-dropdown-menu__title--active) {
  color: var(--van-primary-color);
}

/* 待办看板：项目列表顶部工作台 */
.todo-board {
  margin: 8px 0;
  display: flex;
  gap: 8px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.todo-board::-webkit-scrollbar {
  display: none;
}

.todo-card {
  flex-shrink: 0;
  min-width: 150px;
  padding: 8px 12px;
  border-radius: 10px;
  border: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: #fff;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
  opacity: 0.9;
  transition: opacity 0.15s ease, box-shadow 0.15s ease, transform 0.1s ease;
}

.todo-card--danger {
  background: #ee0a24;
}

.todo-card--primary {
  background: #1989fa;
}

.todo-card--active {
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.75),
              0 3px 8px rgba(0, 0, 0, 0.2);
  opacity: 1;
  transform: translateY(-1px);
}

.todo-card:active {
  opacity: 0.85;
  transform: translateY(0);
}

.todo-card-title {
  font-weight: 500;
}

.todo-card-count {
  font-size: 18px;
  font-weight: 700;
}

/* 暴力消除 Vant 底层幽灵留白 */
.project-list-wrapper {
  /* 强行把整个列表区域向上平移，抵消掉 Vant 自带的顽固留白 */
  margin-top: -16px !important;
  position: relative;
  z-index: 1; /* 确保上移后不会被顶部的背景遮挡 */
}

/* 列表首项无上边距，防止与搜索区间隙被撑大 */
.project-page-container :deep(.van-list > div:first-child) {
  margin-top: 0 !important;
}

/* 列表底部「没有更多了」紧凑 */
.project-page-container :deep(.van-list__finished-text) {
  font-size: 12px;
  color: #969799;
  padding: 6px 0 8px;
}

/* 项目卡片：白底与页面浅灰对比，无多余边框；首卡无上边距防叠加 */
.project-card {
  background: #fff;
  border-radius: var(--app-card-radius, 8px);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
  padding: 10px 12px;
  margin-bottom: 8px;
  cursor: pointer;
}
.project-card:first-child {
  margin-top: 0 !important;
}
.project-card:active {
  opacity: 0.96;
}
.project-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 0;
}
.project-card-title {
  flex: 1;
  min-width: 0;
  font-size: 15px;
  font-weight: 600;
  color: #323233;
  margin: 0;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.project-card-badge {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 500;
}
.project-card-badge-wrap {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.project-card-pending-dot {
  margin-left: 2px;
}
.project-card--pending-approval {
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06), 0 0 0 2px rgba(237, 106, 12, 0.35);
}
.badge--active {
  background: rgba(0, 122, 255, 0.12);
  color: var(--app-primary);
}
.badge--pending {
  background: #fff7e8;
  color: #ed6a0c;
}
.badge--returned {
  background: #fff1f0;
  color: #ee0a24;
}
.badge--archived {
  background: #ebedf0;
  color: #969799;
}
.project-desc {
  margin-top: 4px;
  margin-bottom: 2px;
  font-size: 13px;
  color: var(--app-text-secondary, #8E8E93);
  line-height: 1.35;
}
.project-meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  align-items: center;
  margin-top: 2px;
}
.project-pm,
.project-creator {
  font-size: 11px;
  color: var(--app-text-secondary, #8E8E93);
}
.project-pm-assigned,
.project-pm-unassigned {
  color: var(--app-text-secondary, #8E8E93);
}

/* 卡片主体与底部操作区：弱化分隔，压缩间距 */
.project-card-body {
  /* 主体内容区 */
}
.project-card-actions {
  margin-top: 6px;
  padding-top: 6px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}
.card-action-upload {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-width: 0;
  padding: 0 10px;
  font-size: 13px;
  color: var(--primary-color);
  background: transparent;
  border: 1px solid var(--primary-color);
  border-radius: 6px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.card-action-upload--small {
  height: 28px;
}
.card-action-upload:active {
  opacity: 0.8;
}
.card-action-upload-icon {
  font-size: 14px;
  line-height: 1;
}

.create-form {
  display: flex;
  flex-direction: column;
  max-height: 88vh;
  background: var(--bg-body);
}
.create-form-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 16px 16px 0;
}
.create-form .form-title {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-main);
}
.form-card-group {
  margin-bottom: 16px;
}
.form-group-label {
  display: block;
  font-size: 13px;
  color: var(--app-text-secondary);
  margin-bottom: 8px;
  padding-left: 4px;
}
.create-form .form-card-style {
  border-radius: var(--app-card-radius);
  overflow: hidden;
  box-shadow: var(--app-card-shadow);
}
.create-form .fixed-submit-wrap {
  flex-shrink: 0;
  padding-top: 8px;
}
.create-form .fixed-submit-wrap .mt {
  margin-top: 8px;
}
</style>
