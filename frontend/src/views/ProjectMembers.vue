<template>
  <div class="project-members">
    <van-nav-bar
      :title="`成员管理 - ${projectName}`"
      left-arrow
      @click-left="router.back()"
    />
    <van-loading v-if="loading" class="loading" vertical>加载中...</van-loading>
    <template v-else>
      <div class="current-pm" v-if="currentPmDisplayName">
        当前项目经理：{{ currentPmDisplayName }}
      </div>
      <div class="toolbar">
        <van-button
          v-if="canManageMembers"
          type="primary"
          size="small"
          icon="plus"
          @click="openAddDialog"
        >
          添加成员
        </van-button>
      </div>
      <van-cell-group v-if="members.length">
        <van-cell
          v-for="m in members"
          :key="m.userId"
          :title="m.displayName || m.username || m.userId"
          :label="m.username"
        >
          <template #value>
            <span class="role-readonly">{{ roleDisplayName(m.role) }}</span>
          </template>
          <template #right-icon>
            <div class="cell-actions">
              <van-button
                v-if="canManageMembers && !m.isCurrentUser"
                size="mini"
                type="primary"
                plain
                @click.stop="openEditDialog(m)"
              >
                编辑
              </van-button>
              <van-button
                v-if="canManageMembers && !m.isCurrentUser"
                size="mini"
                type="danger"
                plain
                @click.stop="removeMember(m)"
              >
                移除
              </van-button>
            </div>
          </template>
        </van-cell>
      </van-cell-group>
      <van-empty v-else description="暂无成员，请添加" />
    </template>

    <!-- 添加成员弹窗 -->
    <van-dialog
      v-model:show="showAddDialog"
      title="添加成员"
      show-cancel-button
      :before-close="onAddConfirm"
    >
      <van-field
        v-model="addForm.userLabel"
        readonly
        label="用户"
        placeholder="选择用户"
        @click="showUserPicker = true"
      />
      <van-field
        v-model="addForm.roleLabel"
        readonly
        label="角色"
        placeholder="选择角色"
        @click="showRolePicker = true"
      />
    </van-dialog>
    <van-popup v-model:show="showUserPicker" position="bottom">
      <van-picker
        :columns="userPickerColumns"
        @confirm="onUserConfirm"
        @cancel="showUserPicker = false"
      />
    </van-popup>
    <van-popup v-model:show="showRolePicker" position="bottom">
      <van-picker
        :columns="rolePickerColumns"
        @confirm="onRoleConfirm"
        @cancel="showRolePicker = false"
      />
    </van-popup>

    <!-- 编辑成员角色弹窗 -->
    <van-dialog
      v-model:show="showEditDialog"
      title="编辑成员角色"
      show-cancel-button
      :before-close="onEditConfirm"
    >
      <van-field
        v-model="editForm.userLabel"
        readonly
        label="用户"
      />
      <van-field
        v-model="editForm.roleLabel"
        readonly
        label="角色"
        placeholder="选择角色"
        @click="showEditRolePicker = true"
      />
    </van-dialog>
    <van-popup v-model:show="showEditRolePicker" position="bottom">
      <van-picker
        :columns="rolePickerColumns"
        @confirm="onEditRoleConfirm"
        @cancel="showEditRolePicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NavBar, Cell, CellGroup, Button, Empty, Loading, Field, Dialog, Popup, Picker, showToast, showConfirmDialog } from 'vant'

/** 统一以中性文案提示（不采用报错样式） */
function showTip(message: string) {
  showToast({ message, type: 'text' })
}
import { getProjectDetail, getProjectMembers, addOrUpdateProjectMember, removeProjectMember, type ProjectMemberVO } from '@/api/projects'
import { getUsers, type AuthUserSimpleVO } from '@/api/users'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => Number(route.params.id))

const projectName = ref('')
const canManageMembers = ref(false)
const members = ref<ProjectMemberVO[]>([])
const loading = ref(true)
const showAddDialog = ref(false)
const showUserPicker = ref(false)
const showRolePicker = ref(false)
const showEditDialog = ref(false)
const showEditRolePicker = ref(false)

const editForm = ref({
  userId: '',
  userLabel: '',
  role: 'editor' as 'owner' | 'editor' | 'viewer',
  roleLabel: '编辑'
})

const roleDisplayMap: Record<string, string> = {
  owner: '负责人',
  editor: '编辑',
  viewer: '查看'
}

/** 当前项目经理展示名（成员中 role=owner 的 displayName） */
const currentPmDisplayName = computed(() => {
  const owner = members.value.find(m => m.role === 'owner')
  return owner ? (owner.displayName || owner.username || owner.userId) : ''
})

function roleDisplayName(role: string): string {
  return roleDisplayMap[role] || role
}

const rolePickerColumns = [
  { text: '负责人', value: 'owner' },
  { text: '编辑', value: 'editor' },
  { text: '查看', value: 'viewer' }
]

const addForm = ref({
  userId: '',
  userLabel: '',
  role: 'editor' as 'owner' | 'editor' | 'viewer',
  roleLabel: '编辑'
})

const userList = ref<AuthUserSimpleVO[]>([])
const userPickerColumns = computed(() =>
  userList.value.map(u => ({ text: `${u.displayName || u.username} (${u.username})`, value: u.id }))
)

async function loadProject() {
  try {
    const res = await getProjectDetail(projectId.value)
    if (res.code === 0 && res.data) {
      projectName.value = res.data.name || '项目'
      canManageMembers.value = res.data.canManageMembers === true
    }
  } catch {
    projectName.value = '项目'
    canManageMembers.value = false
  }
}

async function loadMembers() {
  loading.value = true
  try {
    const res = await getProjectMembers(projectId.value)
    if (res.code === 0 && res.data) members.value = res.data
    else members.value = []
  } catch (e: any) {
    showTip(e?.message || '加载失败')
    members.value = []
  } finally {
    loading.value = false
  }
}

async function loadUsers() {
  try {
    const res = await getUsers()
    if (res.code === 0 && res.data) userList.value = res.data
    else userList.value = []
  } catch {
    userList.value = []
  }
}

function openAddDialog() {
  addForm.value = { userId: '', userLabel: '', role: 'editor', roleLabel: '编辑' }
  showAddDialog.value = true
  if (userList.value.length === 0) loadUsers()
}

function openEditDialog(m: ProjectMemberVO) {
  editForm.value = {
    userId: m.userId,
    userLabel: m.displayName || m.username || m.userId,
    role: m.role as 'owner' | 'editor' | 'viewer',
    roleLabel: roleDisplayName(m.role)
  }
  showEditDialog.value = true
}

function onEditRoleConfirm({ selectedOptions }: { selectedOptions: { text: string; value: string }[] }) {
  const o = selectedOptions[0]
  if (o) {
    editForm.value.role = o.value as 'owner' | 'editor' | 'viewer'
    editForm.value.roleLabel = o.text
  }
  showEditRolePicker.value = false
}

async function onEditConfirm(action: string): Promise<boolean> {
  if (action !== 'confirm') return true
  try {
    const res = await addOrUpdateProjectMember(projectId.value, {
      userId: editForm.value.userId,
      role: editForm.value.role
    })
    if (res.code === 0) {
      showToast('已更新')
      await loadMembers()
      return true
    }
    showTip(toFriendlyTip(res.message) || '更新失败')
    return false
  } catch (e: any) {
    showTip(toFriendlyTip(getResponseMessage(e)) || '更新失败')
    return false
  }
}

function onUserConfirm({ selectedOptions }: { selectedOptions: { text: string; value: string }[] }) {
  const o = selectedOptions[0]
  if (o) {
    addForm.value.userId = o.value
    addForm.value.userLabel = o.text
  }
  showUserPicker.value = false
}

function onRoleConfirm({ selectedOptions }: { selectedOptions: { text: string; value: string }[] }) {
  const o = selectedOptions[0]
  if (o) {
    addForm.value.role = o.value as 'owner' | 'editor' | 'viewer'
    addForm.value.roleLabel = o.text
  }
  showRolePicker.value = false
}

async function onAddConfirm(action: string): Promise<boolean> {
  if (action !== 'confirm') return true
  if (!addForm.value.userId) {
    showTip('请选择用户')
    return false
  }
  try {
    const res = await addOrUpdateProjectMember(projectId.value, {
      userId: Number(addForm.value.userId),
      role: addForm.value.role
    })
    if (res.code === 0) {
      showToast('已添加')
      await loadMembers()
      return true
    }
    showTip(toFriendlyTip(res.message) || '添加失败')
    return false
  } catch (e: any) {
    showTip(toFriendlyTip(getResponseMessage(e)) || '添加失败')
    return false
  }
}

function getResponseMessage(e: any): string {
  return (e?.response?.data?.message ?? e?.message ?? '') as string
}

function removeMember(m: ProjectMemberVO) {
  showConfirmDialog({ title: '确认移除', message: `确定将 ${m.displayName || m.username} 移出项目？` })
    .then(async () => {
      try {
        const res = await removeProjectMember(projectId.value, m.userId)
        if (res.code === 0) {
          showToast('已移除')
          await loadMembers()
        } else {
          showTip(toFriendlyTip(res.message) || '无法移除')
        }
      } catch (e: any) {
        showTip(toFriendlyTip(getResponseMessage(e)) || '无法移除')
      }
    })
    .catch(() => {})
}

/** 将后端业务规则文案转为友好提示，避免像报错 */
function toFriendlyTip(msg: string | undefined): string {
  if (!msg) return ''
  if (msg.includes('不能移除项目创建人') || msg.includes('至少保留一名 owner')) {
    return '项目至少需保留一名负责人，无法移出创建人'
  }
  if (msg.includes('用户不存在')) return '所选用户不存在，请重新选择'
  if (msg.includes('不能修改自己')) return '不能修改自己的成员信息'
  return msg
}

onMounted(() => {
  loadProject()
  loadMembers()
})
</script>

<style scoped>
.project-members {
  min-height: 100vh;
  padding-bottom: 24px;
}
.loading {
  padding: 48px 0;
}
.toolbar {
  padding: 12px 16px;
}
.cell-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.role-readonly {
  color: var(--van-text-color);
  font-size: 14px;
}

.van-cell {
  align-items: center;
}
</style>
