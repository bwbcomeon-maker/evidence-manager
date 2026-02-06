import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import Home from '@/views/Home.vue'
import ProjectList from '@/views/ProjectList.vue'
import ProjectDetail from '@/views/ProjectDetail.vue'
import ProjectMembers from '@/views/ProjectMembers.vue'
import EvidenceUpload from '@/views/EvidenceUpload.vue'
import EvidenceList from '@/views/EvidenceList.vue'
import EvidenceHome from '@/views/evidence/EvidenceHome.vue'
import EvidenceByProject from '@/views/evidence/EvidenceByProject.vue'
import MyEvidenceList from '@/views/evidence/MyEvidenceList.vue'
import RecentEvidenceList from '@/views/evidence/RecentEvidenceList.vue'
import VoidedEvidenceList from '@/views/evidence/VoidedEvidenceList.vue'
import EvidenceTypeList from '@/views/evidence/EvidenceTypeList.vue'
import EvidenceDetail from '@/views/evidence/EvidenceDetail.vue'
import Me from '@/views/Me.vue'
import Login from '@/views/Login.vue'
import AdminUsers from '@/views/AdminUsers.vue'
import { useAuthStore } from '@/stores/auth'
import { showToast } from 'vant'

/** V1：作废证据/审计入口 = SYSTEM_ADMIN + AUDITOR；PROJECT_AUDITOR 短期兼容（迁移后为 AUDITOR） */
const VOIDED_EVIDENCE_ROLES = ['SYSTEM_ADMIN', 'AUDITOR', 'PROJECT_AUDITOR']

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: Login,
      meta: { public: true }
    },
    {
      path: '/',
      component: MainLayout,
      redirect: '/home',
      children: [
        {
          path: 'home',
          name: 'Home',
          component: Home,
          meta: { title: '首页', showTabbar: true, showBack: false }
        },
        {
          path: 'projects',
          name: 'ProjectList',
          component: ProjectList,
          meta: { title: '项目', showTabbar: true, showBack: false }
        },
        {
          path: 'evidence',
          name: 'Evidence',
          component: EvidenceHome,
          meta: { title: '证据管理', showTabbar: true, showBack: false }
        },
        {
          path: 'evidence/my',
          name: 'MyEvidenceList',
          component: MyEvidenceList,
          meta: { title: '我上传的证据', showTabbar: true, showBack: true }
        },
        {
          path: 'evidence/recent',
          name: 'RecentEvidenceList',
          component: RecentEvidenceList,
          meta: { title: '最近上传的证据', showTabbar: true, showBack: true }
        },
        {
          path: 'evidence/voided',
          name: 'VoidedEvidenceList',
          component: VoidedEvidenceList,
          meta: { title: '作废证据', showTabbar: true, showBack: true }
        },
        {
          path: 'evidence/type',
          name: 'EvidenceTypeList',
          component: EvidenceTypeList,
          meta: { title: '按文件类型查看', showTabbar: true, showBack: true }
        },
        {
          path: 'evidence/by-project',
          name: 'EvidenceByProject',
          component: EvidenceByProject,
          meta: { title: '按项目查看证据', showTabbar: true, showBack: true }
        },
        {
          path: 'evidence/detail/:id',
          name: 'EvidenceDetail',
          component: EvidenceDetail,
          meta: { title: '证据详情', showTabbar: false, showBack: true }
        },
        {
          path: 'me',
          name: 'Me',
          component: Me,
          meta: { title: '我的', showTabbar: true, showBack: false }
        },
        {
          path: 'projects/:id',
          name: 'ProjectDetail',
          component: ProjectDetail,
          meta: { title: '项目详情', showTabbar: false, showBack: true }
        },
        {
          path: 'projects/:id/members',
          name: 'ProjectMembers',
          component: ProjectMembers,
          meta: { title: '成员管理', showTabbar: false, showBack: true }
        },
        {
          path: 'projects/:id/upload',
          name: 'EvidenceUpload',
          component: EvidenceUpload,
          meta: { title: '上传证据', showTabbar: false, showBack: true }
        },
        {
          path: 'projects/:id/evidences',
          name: 'EvidenceList',
          component: EvidenceList,
          meta: { title: '证据列表', showTabbar: false, showBack: true }
        },
        {
          path: 'admin/users',
          name: 'AdminUsers',
          component: AdminUsers,
          meta: { title: '用户管理', showTabbar: false, showBack: true, requiresAuth: true }
        }
      ]
    }
  ]
})

// 路由守卫：登录校验、作废证据页权限、meta.requiresAuth
router.beforeEach(async (to) => {
  if (to.meta.public) return true

  const auth = useAuthStore()

  // 作废证据页：需登录 + 需角色 SYSTEM_ADMIN / AUDITOR（PROJECT_AUDITOR 短期兼容）
  if (to.path === '/evidence/voided') {
    const user = await auth.fetchMe()
    if (!user) return { path: '/login', query: { redirect: to.fullPath } }
    if (!VOIDED_EVIDENCE_ROLES.includes(user.roleCode)) {
      showToast('无权限访问')
      return { path: '/evidence', replace: true }
    }
    return true
  }

  if (to.meta.requiresAuth) {
    const user = await auth.fetchMe()
    if (user) return true
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  return true
})

export default router
