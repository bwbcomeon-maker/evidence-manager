<template>
  <div class="project-list">
    <div class="content">
      <div class="toolbar">
        <van-button type="primary" size="small" @click="showCreate = true">新建项目</van-button>
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
        </van-list>
      </van-pull-refresh>
    </div>

    <van-popup v-model:show="showCreate" position="bottom" round :style="{ padding: '16px' }">
      <div class="create-form">
        <h3 class="form-title">新建项目</h3>
        <van-form @submit="onCreateSubmit">
          <van-cell-group inset>
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Button, Cell, List, Popup, Field, Form, CellGroup, PullRefresh, Tag } from 'vant'
import { createProject, type ProjectVO } from '@/api/projects'
import { showToast } from 'vant'

interface Project {
  id: number
  code: string
  name: string
  description: string
  status: string
}

const router = useRouter()
const loading = ref(false)
const listLoading = ref(false)
const finished = ref(false)
const projects = ref<Project[]>([])

const showCreate = ref(false)
const createLoading = ref(false)
const createForm = ref({ name: '', description: '' })

// Mock 数据（P0-2 前仍使用；新建的项目会插入到列表前）
const mockProjects: Project[] = [
  {
    id: 1,
    code: 'PROJ-001',
    name: '智慧城市项目',
    description: '智慧城市综合管理平台开发项目',
    status: 'active'
  },
  {
    id: 2,
    code: 'PROJ-002',
    name: '电商平台升级',
    description: '电商平台性能优化与功能升级',
    status: 'active'
  },
  {
    id: 3,
    code: 'PROJ-003',
    name: '移动办公系统',
    description: '企业移动办公解决方案',
    status: 'archived'
  }
]

const loadProjects = () => {
  // 模拟 API 调用
  setTimeout(() => {
    projects.value = [...mockProjects]
    finished.value = true
    listLoading.value = false
    loading.value = false
  }, 500)
}

const onRefresh = () => {
  loadProjects()
}

const onLoad = () => {
  loadProjects()
}

const goToDetail = (id: number) => {
  router.push(`/projects/${id}`)
}

const onCreateSubmit = async () => {
  const name = createForm.value.name?.trim()
  if (!name) {
    showToast('请输入项目名称')
    return
  }
  createLoading.value = true
  try {
    const res = await createProject({ name, description: createForm.value.description?.trim() || undefined })
    if (res.code !== 0) {
      showToast(res.message || '创建失败')
      return
    }
    const data = res.data as ProjectVO
    showCreate.value = false
    createForm.value = { name: '', description: '' }
    // 将新建项目插入列表前，实现“刷新列表”
    projects.value = [
      { id: data.id, code: data.code, name: data.name, description: data.description || '', status: data.status },
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
