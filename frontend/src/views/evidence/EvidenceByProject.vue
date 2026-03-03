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
                <h3 class="project-card-title" :title="project.name">{{ project.name }}</h3>
                <span
                  class="project-card-badge"
                  :class="project.status === 'active' ? 'badge--active' : 'badge--archived'"
                >
                  {{ project.status === 'active' ? '进行中' : '已归档' }}
                </span>
              </div>
              <div v-if="project.description" class="project-desc">{{ project.description }}</div>
              <div v-else-if="project.code" class="project-desc">{{ project.code }}</div>
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
                class="card-action-view card-action-view--small"
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
  padding: 12px 16px 16px;
}

/* 列表底部「没有更多了」紧凑 */
.evidence-by-project :deep(.van-list__finished-text) {
  font-size: 12px;
  color: #969799;
  padding: 6px 0 8px;
}

/* 与项目列表一致的紧凑卡片样式 */
.project-card {
  background: var(--app-card);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-card-shadow);
  padding: 10px 12px;
  margin-bottom: 8px;
  cursor: pointer;
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
.badge--active {
  background: rgba(0, 122, 255, 0.12);
  color: var(--app-primary);
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
.project-card-actions {
  margin-top: 6px;
  padding-top: 6px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  border-top: 1px solid #f5f5f5;
}
.card-action-view {
  display: inline-flex;
  align-items: center;
  justify-content: center;
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
.card-action-view--small {
  height: 28px;
}
.card-action-view:active {
  opacity: 0.8;
}
</style>
