<template>
  <div class="project-detail">
    <van-nav-bar
      title="项目详情"
      left-arrow
      @click-left="$router.back()"
      fixed
    />
    <div class="content">
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

      <div class="actions">
        <van-button
          type="primary"
          block
          @click="goToUpload"
          style="margin-bottom: 12px"
        >
          上传证据
        </van-button>
        <van-button
          type="default"
          block
          @click="goToEvidenceList"
        >
          查看证据列表
        </van-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NavBar, Cell, CellGroup, Button, Tag } from 'vant'

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
const projectId = route.params.id as string
const project = ref<Project | null>(null)

// Mock 数据
const mockProject: Project = {
  id: Number(projectId),
  code: 'PROJ-001',
  name: '智慧城市项目',
  description: '智慧城市综合管理平台开发项目，包括数据采集、分析、可视化等功能模块。',
  status: 'active',
  createdAt: '2024-01-15 10:30:00'
}

const loadProject = () => {
  // 模拟 API 调用
  setTimeout(() => {
    project.value = mockProject
  }, 300)
}

const goToUpload = () => {
  router.push(`/projects/${projectId}/upload`)
}

const goToEvidenceList = () => {
  router.push(`/projects/${projectId}/evidences`)
}

onMounted(() => {
  loadProject()
})
</script>

<style scoped>
.project-detail {
  min-height: 100vh;
  padding-top: 46px;
}

.content {
  padding: 16px;
}

.actions {
  margin-top: 24px;
}
</style>
