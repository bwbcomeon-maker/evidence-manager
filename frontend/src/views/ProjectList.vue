<template>
  <div class="project-list">
    <van-nav-bar title="项目列表" fixed />
    <div class="content">
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { NavBar, Cell, List, PullRefresh, Tag } from 'vant'

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

// Mock 数据
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
    projects.value = mockProjects
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

onMounted(() => {
  loadProjects()
})
</script>

<style scoped>
.project-list {
  min-height: 100vh;
  padding-top: 46px;
}

.content {
  padding: 16px;
}
</style>
