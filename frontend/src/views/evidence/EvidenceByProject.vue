<template>
  <div class="evidence-by-project">
    <div class="content">
      <van-pull-refresh v-model="loading" @refresh="loadProjects">
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
            @click="goToProjectEvidences(project.id)"
          >
            <div class="project-card-body">
              <div class="project-card-header">
                <h3 class="project-card-title">{{ project.name }}</h3>
                <span
                  class="project-card-badge"
                  :class="project.status === 'active' ? 'badge--active' : 'badge--archived'"
                >
                  {{ project.status === 'active' ? '进行中' : '已归档' }}
                </span>
              </div>
              <div v-if="project.description" class="project-desc">{{ project.description }}</div>
              <div v-else-if="project.code" class="project-desc">{{ project.code }}</div>
              <div class="project-pm" :class="project.currentPmDisplayName ? 'project-pm-assigned' : 'project-pm-unassigned'">
                项目经理：{{ project.currentPmDisplayName || '未分配' }}
              </div>
            </div>
            <div class="project-card-actions">
              <button
                type="button"
                class="card-action-view"
                @click.stop="goToProjectEvidences(project.id)"
              >
                查看证据
              </button>
            </div>
          </div>
          <van-empty v-if="!loading && !listLoading && listError" :description="listError" />
          <van-empty v-else-if="!loading && !listLoading && projects.length === 0" description="暂无项目，请先在项目中创建" />
        </van-list>
      </van-pull-refresh>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getProjects, type ProjectVO } from '@/api/projects'

const router = useRouter()
const loading = ref(false)
const listLoading = ref(false)
const finished = ref(false)
const projects = ref<ProjectVO[]>([])
const listError = ref('')

const loadProjects = async () => {
  listError.value = ''
  listLoading.value = true
  loading.value = true
  try {
    const res = await getProjects()
    if (res.code === 0 && Array.isArray(res.data)) {
      projects.value = res.data
    } else {
      listError.value = res.message || '加载失败'
    }
  } catch (e: unknown) {
    listError.value = (e as Error)?.message || '加载失败'
    projects.value = []
  } finally {
    finished.value = true
    listLoading.value = false
    loading.value = false
  }
}

function onLoad() {
  if (projects.value.length === 0 && !listError.value) {
    loadProjects()
  } else {
    finished.value = true
    listLoading.value = false
  }
}

function goToProjectEvidences(projectId: number) {
  router.push({ path: `/projects/${projectId}`, query: { tab: 'evidence', from: 'evidence-by-project' } })
}

onMounted(() => {
  loadProjects()
})
</script>

<style scoped>
.evidence-by-project {
  min-height: 100%;
  background: var(--app-bg);
}
.content {
  padding: 16px;
}

/* 与项目列表一致的卡片样式（图 1 标准 UI） */
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
.project-card-actions {
  border-top: 1px solid #f0f0f0;
  margin-top: 12px;
  padding-top: 12px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}
.card-action-view {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 34px;
  padding: 0 14px;
  font-size: 14px;
  color: var(--primary-color);
  background: transparent;
  border: 1px solid var(--primary-color);
  border-radius: 8px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.card-action-view:active {
  opacity: 0.8;
}
</style>
