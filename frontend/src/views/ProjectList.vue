<template>
  <div class="project-list">
    <div class="content">
      <div class="toolbar">
        <van-button type="primary" size="small" @click="showCreate = true">新建项目</van-button>
        <van-button v-if="canImportProjects" type="primary" size="small" plain @click="showImport = true">批量导入</van-button>
      </div>
      <van-pull-refresh v-model="loading" @refresh="onRefresh">
        <van-list
          v-model:loading="listLoading"
          :finished="finished"
          finished-text="没有更多了"
          @load="onLoad"
        >
          <van-cell
            v-for="project in projects"
            :key="project.id"
            :title="project.name"
            :label="project.description"
            is-link
            @click="goToDetail(project.id)"
          >
            <template #value>
              <van-tag :type="project.status === 'active' ? 'success' : 'default'">
                {{ project.status === 'active' ? '进行中' : '已归档' }}
              </van-tag>
            </template>
          </van-cell>
          <van-empty v-if="!loading && !listLoading && listError" :description="listError" />
          <van-empty v-else-if="!loading && !listLoading && projects.length === 0" description="暂无项目" />
        </van-list>
      </van-pull-refresh>
    </div>

    <van-popup v-model:show="showCreate" position="bottom" round :style="{ padding: '16px' }">
      <div class="create-form">
        <h3 class="form-title">新建项目</h3>
        <van-form @submit="onCreateSubmit">
          <van-cell-group inset>
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
          </van-cell-group>
          <div class="form-actions">
            <van-button block type="primary" native-type="submit" :loading="createLoading">创建</van-button>
            <van-button block plain class="mt" @click="showCreate = false">取消</van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <van-popup v-model:show="showImport" position="bottom" round :style="{ padding: '16px', maxHeight: '80vh' }">
      <div class="import-form">
        <h3 class="form-title">批量导入项目</h3>
        <p class="import-tip">仅 SYSTEM_ADMIN / PMO 可导入。模板列：项目令号、项目名称、项目描述。</p>
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
import { Button, Cell, List, Popup, Field, Form, CellGroup, PullRefresh, Tag } from 'vant'
import { createProject, getProjects, importProjects, getProjectImportTemplateUrl, type ProjectVO, type ProjectImportResult } from '@/api/projects'
import { useAuthStore } from '@/stores/auth'
import { showToast } from 'vant'

interface Project {
  id: number
  code: string
  name: string
  description: string
  status: string
}

const router = useRouter()
const auth = useAuthStore()
const canImportProjects = computed(() => {
  const code = auth.currentUser?.roleCode
  return code === 'SYSTEM_ADMIN' || code === 'PMO'
})
const importTemplateUrl = getProjectImportTemplateUrl()

const loading = ref(false)
const listLoading = ref(false)
const finished = ref(false)
const projects = ref<Project[]>([])
const listError = ref('')

const showCreate = ref(false)
const createLoading = ref(false)
const createForm = ref({ code: '', name: '', description: '' })

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
    if (res.code === 0 && Array.isArray(res.data)) {
      projects.value = res.data.map((p: ProjectVO) => ({
        id: p.id,
        code: p.code,
        name: p.name,
        description: p.description ?? '',
        status: p.status
      }))
    } else {
      listError.value = res.message || '加载失败'
    }
  } catch (e: any) {
    listError.value = e?.message || '加载失败'
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
      description: createForm.value.description?.trim() || undefined
    })
    if (res.code !== 0) {
      showToast(res.message || '创建失败')
      return
    }
    const data = res.data as ProjectVO
    showCreate.value = false
    createForm.value = { code: '', name: '', description: '' }
    // 将新建项目插入列表前并刷新（真实列表来自 API，此处仅本地追加一次）
    projects.value = [
      { id: data.id, code: data.code, name: data.name, description: data.description ?? '', status: data.status },
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
}

.content {
  padding: 16px;
}

.toolbar {
  margin-bottom: 12px;
}

.create-form .form-title {
  margin: 0 0 16px;
  font-size: 16px;
}

.form-actions {
  margin-top: 16px;
}

.form-actions .mt {
  margin-top: 8px;
}
</style>
