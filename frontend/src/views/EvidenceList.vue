<template>
  <div class="evidence-list">
    <van-nav-bar
      title="证据列表"
      left-arrow
      @click-left="$router.back()"
      fixed
    />
    <div class="content">
      <van-pull-refresh v-model="loading" @refresh="onRefresh">
        <van-list
          v-model:loading="listLoading"
          :finished="finished"
          finished-text="没有更多了"
          @load="onLoad"
        >
          <van-cell
            v-for="evidence in evidences"
            :key="evidence.id"
            :title="evidence.title"
            :label="evidence.note || '无说明'"
            is-link
            @click="viewEvidence(evidence)"
          >
            <template #value>
              <div class="evidence-meta">
                <van-tag :type="getStatusType(evidence.status)">
                  {{ getStatusText(evidence.status) }}
                </van-tag>
                <div class="file-size">{{ formatSize(evidence.sizeBytes) }}</div>
              </div>
            </template>
          </van-cell>
        </van-list>
      </van-pull-refresh>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { NavBar, Cell, List, PullRefresh, Tag } from 'vant'

interface Evidence {
  id: number
  title: string
  note: string
  status: string
  sizeBytes: number
  createdAt: string
}

const route = useRoute()
const projectId = route.params.id as string
const loading = ref(false)
const listLoading = ref(false)
const finished = ref(false)
const evidences = ref<Evidence[]>([])

// Mock 数据
const mockEvidences: Evidence[] = [
  {
    id: 1,
    title: '项目需求文档.pdf',
    note: '项目初期需求分析文档',
    status: 'active',
    sizeBytes: 2048576,
    createdAt: '2024-01-20 14:30:00'
  },
  {
    id: 2,
    title: '系统架构设计图.png',
    note: '系统整体架构设计',
    status: 'active',
    sizeBytes: 1024000,
    createdAt: '2024-01-22 09:15:00'
  },
  {
    id: 3,
    title: '测试报告.docx',
    note: '功能测试报告',
    status: 'archived',
    sizeBytes: 512000,
    createdAt: '2024-01-25 16:45:00'
  }
]

const loadEvidences = () => {
  // 模拟 API 调用
  setTimeout(() => {
    evidences.value = mockEvidences
    finished.value = true
    listLoading.value = false
    loading.value = false
  }, 500)
}

const onRefresh = () => {
  loadEvidences()
}

const onLoad = () => {
  loadEvidences()
}

const getStatusType = (status: string) => {
  const map: Record<string, string> = {
    active: 'success',
    invalid: 'danger',
    archived: 'default'
  }
  return map[status] || 'default'
}

const getStatusText = (status: string) => {
  const map: Record<string, string> = {
    active: '有效',
    invalid: '无效',
    archived: '已归档'
  }
  return map[status] || status
}

const formatSize = (bytes: number) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
}

const viewEvidence = (evidence: Evidence) => {
  console.log('View evidence:', evidence)
  // 可以跳转到证据详情页或预览
}

onMounted(() => {
  loadEvidences()
})
</script>

<style scoped>
.evidence-list {
  min-height: 100vh;
  padding-top: 46px;
}

.content {
  padding: 16px;
}

.evidence-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.file-size {
  font-size: 12px;
  color: #969799;
}
</style>
