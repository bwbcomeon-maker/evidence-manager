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
          <van-cell
            v-for="project in projects"
            :key="project.id"
            :title="project.name"
            :label="project.description || project.code"
            is-link
            @click="goToProjectEvidences(project.id)"
          >
            <template #value>
              <van-tag :type="project.status === 'active' ? 'success' : 'default'">
                {{ project.status === 'active' ? '进行中' : '已归档' }}
              </van-tag>
            </template>
          </van-cell>
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
  router.push(`/projects/${projectId}/evidences`)
}

onMounted(() => {
  loadProjects()
})
</script>

<style scoped>
.evidence-by-project {
  min-height: 100%;
}
.content {
  padding: 16px;
}
</style>
