<template>
  <div class="project-list">
    <div class="content">
      <!-- 顶部操作区：横向滑动按钮组，主按钮突出 -->
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
            @click="goToDetail(project.id)"
          >
            <div class="project-card-header">
              <h3 class="project-card-title">{{ project.name }}</h3>
              <span class="project-card-badge" :class="project.status === 'active' ? 'badge--active' : 'badge--archived'">
                {{ project.status === 'active' ? '进行中' : '已归档' }}
              </span>
            </div>
            <div v-if="project.description" class="project-desc">{{ project.description }}</div>
            <div class="project-pm" :class="project.currentPmDisplayName ? 'project-pm-assigned' : 'project-pm-unassigned'">
              项目经理：{{ project.currentPmDisplayName || '未分配' }}
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
import { Button, Cell, List, Popup, Field, Form, CellGroup, PullRefresh, Tag, Switch } from 'vant'
import { createProject, getProjects, importProjects, getProjectImportTemplateUrl, type ProjectVO, type ProjectImportResult } from '@/api/projects'
import { useAuthStore } from '@/stores/auth'
import { showToast } from 'vant'

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
function go(path: string) {
  router.push(path)
}
const importTemplateUrl = getProjectImportTemplateUrl()

const loading = ref(false)
const listLoading = ref(false)
const finished = ref(false)
const projects = ref<Project[]>([])
const listError = ref('')

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
  } catch (e: any) {
    showToast(e?.message || '导入失败')
  } finally {
    importLoading.value = false
  }
}

const loadProjects = async () => {
  listError.value = ''
  listLoading.value = true
  loading.value = true
  try {
    const res = await getProjects()
    const raw = res?.data
    const list = Array.isArray(raw) ? raw : []
    if (res?.code === 0) {
      projects.value = list.map((p: ProjectVO) => ({
        id: p.id ?? 0,
        code: p.code ?? '',
        name: p.name ?? '',
        description: p.description ?? '',
        status: p.status ?? 'active',
        currentPmDisplayName: p.currentPmDisplayName ?? undefined
      }))
    } else {
      listError.value = res?.message || '加载失败'
    }
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

const onRefresh = () => {
  finished.value = false
  loadProjects()
}

const onLoad = () => {
  if (projects.value.length === 0 && !listError.value) {
    loadProjects()
  } else {
    finished.value = true
    listLoading.value = false
  }
}

const goToDetail = (id: number) => {
  router.push(`/projects/${id}`)
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
  } catch (e) {
    showToast('创建失败')
  } finally {
    createLoading.value = false
  }
}

onMounted(() => {
  loadProjects()
})
</script>

<style scoped>
.project-list {
  min-height: 100vh;
  background: var(--app-bg);
}

.content {
  padding: 16px;
}

/* 顶部操作区：横向滑动按钮组 */
.action-bar {
  margin-bottom: 16px;
  -webkit-overflow-scrolling: touch;
}
.action-bar-scroll {
  display: flex;
  align-items: center;
  gap: 10px;
  overflow-x: auto;
  padding: 4px 0;
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

/* 项目卡片列表 */
.project-card {
  background: var(--app-card);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-card-shadow);
  padding: 16px;
  margin-bottom: 12px;
  min-height: var(--app-tap-min-height);
  cursor: pointer;
}
.project-card:active {
  opacity: 0.96;
}
.project-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.project-card-title {
  flex: 1;
  font-size: 17px;
  font-weight: 600;
  color: #323233;
  margin: 0;
  line-height: 1.35;
}
.project-card-badge {
  flex-shrink: 0;
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}
.badge--active {
  background: rgba(0, 122, 255, 0.12);
  color: var(--app-primary);
}
.badge--archived {
  background: #ebedf0;
  color: #969799;
}
.project-desc {
  margin-top: 8px;
  margin-bottom: 4px;
  font-size: 14px;
  color: var(--app-text-secondary, #8E8E93);
  line-height: 1.4;
}
.project-pm {
  font-size: 12px;
  color: var(--app-text-secondary, #8E8E93);
}
.project-pm-assigned,
.project-pm-unassigned {
  color: var(--app-text-secondary, #8E8E93);
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
