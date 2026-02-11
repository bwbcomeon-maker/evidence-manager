<template>
  <div class="batch-assign-page">
    <van-nav-bar
      title="批量分配项目"
      left-arrow
      left-text="返回"
      fixed
      placeholder
      @click-left="onBack"
    />
    <van-loading v-if="loading" class="loading" vertical>加载中...</van-loading>
    <template v-else-if="!canBatchAssign">
      <van-empty description="仅系统管理员或 PMO 可使用此功能" />
    </template>
    <template v-else>
      <van-cell-group inset>
        <van-field
          v-model="form.userLabel"
          readonly
          label="选择用户"
          placeholder="请选择要分配的用户"
          @click="showUserPicker = true"
        />
        <van-field
          v-model="form.roleLabel"
          readonly
          label="角色"
          placeholder="请选择角色"
          @click="showRolePicker = true"
        />
      </van-cell-group>

      <div class="section-title">选择项目（可多选）</div>
      <van-checkbox-group v-model="selectedProjectIds">
        <van-cell-group inset>
          <van-cell
            v-for="p in projects"
            :key="p.id"
            clickable
            :title="p.name"
            :label="p.code"
            @click="toggleProject(p.id)"
          >
            <template #right-icon>
              <van-checkbox :name="p.id" />
            </template>
          </van-cell>
        </van-cell-group>
      </van-checkbox-group>
      <van-empty v-if="projects.length === 0" description="暂无项目" />

      <div class="submit-wrap">
        <van-button
          type="primary"
          block
          :loading="submitting"
          :disabled="form.userId == null || selectedProjectIds.length === 0"
          @click="onSubmit"
        >
          确认分配
        </van-button>
      </div>
    </template>

    <van-popup v-model:show="showUserPicker" position="bottom" :style="{ height: '50vh' }">
      <div class="picker-header">
        <span class="picker-cancel" @click="showUserPicker = false">取消</span>
        <span class="picker-title">选择用户</span>
        <span class="picker-placeholder"></span>
      </div>
      <div class="picker-list">
        <van-cell
          v-for="u in userList"
          :key="u.id"
          :title="`${u.displayName || u.username} (${u.username})`"
          :class="{ 'picker-cell--active': form.userId === u.id }"
          clickable
          @click="onUserSelect(u)"
        />
        <van-empty v-if="userList.length === 0" description="暂无用户" />
      </div>
    </van-popup>
    <van-popup v-model:show="showRolePicker" position="bottom" :style="{ height: '40vh' }">
      <div class="picker-header">
        <span class="picker-cancel" @click="showRolePicker = false">取消</span>
        <span class="picker-title">选择角色</span>
        <span class="picker-placeholder"></span>
      </div>
      <div class="picker-list picker-list--role">
        <van-cell
          v-for="item in roleOptions"
          :key="item.value"
          :title="item.text"
          :class="{ 'picker-cell--active': form.role === item.value }"
          clickable
          @click="onRoleSelect(item)"
        />
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getProjects, batchAssignUserToProjects, type ProjectVO } from '@/api/projects'
import { getUsers, type AuthUserSimpleVO } from '@/api/users'
import { showToast } from 'vant'

const router = useRouter()
const auth = useAuthStore()

const canBatchAssign = computed(() => {
  const code = auth.currentUser?.roleCode
  return code === 'SYSTEM_ADMIN' || code === 'PMO'
})

const loading = ref(true)
const submitting = ref(false)
const projects = ref<ProjectVO[]>([])
const userList = ref<AuthUserSimpleVO[]>([])
const selectedProjectIds = ref<number[]>([])
const showUserPicker = ref(false)
const showRolePicker = ref(false)

const roleOptions = [
  { text: '负责人', value: 'owner' as const },
  { text: '编辑', value: 'editor' as const },
  { text: '查看', value: 'viewer' as const }
]

const form = ref<{
  userId?: number
  userLabel: string
  role: 'owner' | 'editor' | 'viewer'
  roleLabel: string
}>({
  userLabel: '',
  role: 'editor',
  roleLabel: '编辑'
})

function toggleProject(id: number) {
  const idx = selectedProjectIds.value.indexOf(id)
  if (idx >= 0) {
    selectedProjectIds.value = selectedProjectIds.value.filter(x => x !== id)
  } else {
    selectedProjectIds.value = [...selectedProjectIds.value, id]
  }
}

function onBack() {
  router.replace('/home')
}

function onUserSelect(u: AuthUserSimpleVO) {
  form.value.userId = u.id
  form.value.userLabel = `${u.displayName || u.username} (${u.username})`
  showUserPicker.value = false
}

function onRoleSelect(item: { text: string; value: 'owner' | 'editor' | 'viewer' }) {
  form.value.role = item.value
  form.value.roleLabel = item.text
  showRolePicker.value = false
}

async function loadData() {
  loading.value = true
  try {
    const [projRes, userRes] = await Promise.all([getProjects(), getUsers()])
    if (projRes.code === 0 && Array.isArray(projRes.data)) {
      projects.value = projRes.data
    } else {
      projects.value = []
    }
    if (userRes.code === 0 && Array.isArray(userRes.data)) {
      userList.value = userRes.data
    } else {
      userList.value = []
    }
  } catch {
    projects.value = []
    userList.value = []
  } finally {
    loading.value = false
  }
}

async function onSubmit() {
  if (form.value.userId == null) {
    showToast('请选择用户')
    return
  }
  if (selectedProjectIds.value.length === 0) {
    showToast('请至少选择一个项目')
    return
  }
  submitting.value = true
  try {
    const res = await batchAssignUserToProjects({
      userId: form.value.userId!,
      projectIds: selectedProjectIds.value,
      role: form.value.role
    })
    if (res.code === 0 && res.data) {
      const d = res.data
      if (d.failCount === 0) {
        showToast(`已成功将用户加入 ${d.successCount} 个项目`)
        router.replace('/home')
      } else {
        showToast(`成功 ${d.successCount} 个，失败 ${d.failCount} 个`)
        if (d.errors?.length) {
          setTimeout(() => showToast(d.errors!.slice(0, 3).join('；'), { duration: 3000 }), 500)
        }
      }
    } else {
      showToast(res.message || '分配失败')
    }
  } catch (e: any) {
    showToast(e?.response?.data?.message || e?.message || '分配失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.batch-assign-page {
  min-height: 100vh;
  padding-bottom: 32px;
}
.loading {
  padding: 48px 0;
}
.section-title {
  padding: 16px 16px 8px;
  font-size: 14px;
  color: var(--van-gray-7);
  font-weight: 500;
}
.submit-wrap {
  padding: 24px 16px;
}

.picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--van-cell-border-color);
  font-size: 14px;
}
.picker-title {
  font-weight: 500;
}
.picker-cancel {
  color: var(--van-gray-6);
  cursor: pointer;
}
.picker-placeholder {
  width: 48px;
}
.picker-list {
  overflow-y: auto;
  max-height: calc(50vh - 48px);
  -webkit-overflow-scrolling: touch;
}
.picker-cell--active {
  color: var(--van-primary-color);
  font-weight: 500;
}
.picker-list--role {
  max-height: calc(40vh - 48px);
}
</style>
