import { createRouter, createWebHistory } from 'vue-router'
import ProjectList from '@/views/ProjectList.vue'
import ProjectDetail from '@/views/ProjectDetail.vue'
import EvidenceUpload from '@/views/EvidenceUpload.vue'
import EvidenceList from '@/views/EvidenceList.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/projects'
    },
    {
      path: '/projects',
      name: 'ProjectList',
      component: ProjectList
    },
    {
      path: '/projects/:id',
      name: 'ProjectDetail',
      component: ProjectDetail
    },
    {
      path: '/projects/:id/upload',
      name: 'EvidenceUpload',
      component: EvidenceUpload
    },
    {
      path: '/projects/:id/evidences',
      name: 'EvidenceList',
      component: EvidenceList
    }
  ]
})

export default router
