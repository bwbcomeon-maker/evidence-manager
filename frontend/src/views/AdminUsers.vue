<template>
  <div class="admin-users">
    <div class="toolbar">
      <van-search
        v-model="keyword"
        placeholder="搜索账号/姓名/手机/邮箱"
        shape="round"
        @search="onSearch"
      />
      <van-dropdown-menu>
        <van-dropdown-item v-model="filterRoleCode" :options="roleOptions" @change="onFilter" />
        <van-dropdown-item v-model="filterEnabled" :options="enabledOptions" @change="onFilter" />
      </van-dropdown-menu>
    </div>
    <div class="list-wrap">
      <van-button type="primary" block class="add-btn" @click="openAdd">新增用户</van-button>
      <van-list
        v-model:loading="listLoading"
        :finished="finished"
        finished-text="没有更多了"
        @load="onLoad"
      >
        <van-cell
          v-for="u in list"
          :key="u.id"
          :title="u.username"
          :label="`${u.realName || '-'} · ${roleLabel(u.roleCode)} · ${u.enabled ? '启用' : '禁用'}`"
        >
          <template #right-icon>
            <van-switch
              :model-value="u.enabled"
              size="22"
              @update:model-value="(v: boolean) => toggleEnable(u, v)"
            />
          </template>
          <template #value>
            <div class="cell-actions">
              <van-button size="small" type="primary" plain @click="openEdit(u)">编辑</van-button>
              <van-button size="small" @click="openResetPwd(u)">重置密码</van-button>
              <van-button size="small" type="danger" plain @click="confirmDelete(u)">删除</van-button>
            </div>
          </template>
        </van-cell>
      </van-list>
    </div>

    <!-- 新增/编辑弹窗 -->
    <van-popup
      v-model:show="showFormPopup"
      position="bottom"
      round
      :style="{ height: '70%' }"
    >
      <van-nav-bar
        :title="editingId ? '编辑用户' : '新增用户'"
        left-arrow
        @click-left="showFormPopup = false"
      />
      <van-form @submit="onFormSubmit" class="form-body">
        <van-cell-group inset>
          <van-field
            v-model="form.username"
            name="username"
            label="登录账号"
            placeholder="请输入"
            :rules="[{ required: true, message: '请输入登录账号' }]"
            :disabled="!!editingId"
          />
          <van-field
            v-if="!editingId"
            v-model="form.password"
            type="password"
            name="password"
            label="密码"
            placeholder="不填则默认 Init@12345"
          />
          <van-field v-model="form.realName" name="realName" label="姓名" placeholder="请输入" />
          <van-field v-model="form.phone" name="phone" label="手机号" placeholder="请输入" />
          <van-field v-model="form.email" name="email" label="邮箱" placeholder="请输入" />
          <van-field
            v-model="form.roleCode"
            is-link
            readonly
            name="roleCode"
            label="角色"
            placeholder="请选择"
            :rules="[{ required: true, message: '请选择角色' }]"
            @click="showRolePicker = true"
          />
          <van-field name="enabled" label="启用">
            <template #input>
              <van-switch v-model="form.enabled" />
            </template>
          </van-field>
        </van-cell-group>
        <div class="form-footer">
          <van-button round block type="primary" native-type="submit" :loading="formLoading">
            保存
          </van-button>
        </div>
      </van-form>
      <van-popup v-model:show="showRolePicker" position="bottom">
        <van-picker
          :columns="roleOptions"
          @confirm="onRoleConfirm"
          @cancel="showRolePicker = false"
        />
      </van-popup>
    </van-popup>

    <!-- 重置密码结果弹窗 -->
    <van-dialog
      v-model:show="showPwdDialog"
      title="新密码（请复制保存，仅显示一次）"
      show-cancel-button
      confirm-button-text="复制"
      @confirm="copyNewPassword"
    >
      <div class="pwd-display">{{ newPassword }}</div>
    </van-dialog>

    <!-- 删除确认 -->
    <van-dialog
      v-model:show="showDeleteConfirm"
      title="确认删除"
      message="确定要删除该用户吗？删除后不可恢复。"
      show-cancel-button
      @confirm="doDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { showToast } from 'vant'
import {
  getAdminUserPage,
  createAdminUser,
  updateAdminUser,
  setAdminUserEnabled,
  resetAdminUserPassword,
  deleteAdminUser,
  ROLE_OPTIONS,
  type AdminUserItem,
  type CreateUserBody,
  type UpdateUserBody
} from '@/api/adminUsers'

const keyword = ref('')
const filterRoleCode = ref('')
const filterEnabled = ref('')
const roleOptions = [
  { text: '全部角色', value: '' },
  ...ROLE_OPTIONS.map((o) => ({ text: o.text, value: o.value }))
]
const enabledOptions = [
  { text: '全部状态', value: '' },
  { text: '启用', value: 'true' },
  { text: '禁用', value: 'false' }
]

const list = ref<AdminUserItem[]>([])
const listLoading = ref(false)
const finished = ref(false)
const page = ref(1)
const pageSize = 10

const showFormPopup = ref(false)
const formLoading = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<CreateUserBody & { realName?: string; phone?: string; email?: string }>({
  username: '',
  password: '',
  realName: '',
  phone: '',
  email: '',
  roleCode: '',
  enabled: true
})
const showRolePicker = ref(false)

const showPwdDialog = ref(false)
const newPassword = ref('')
const resetPwdUserId = ref(0)

const showDeleteConfirm = ref(false)
const deleteTarget = ref<AdminUserItem | null>(null)

function roleLabel(code: string) {
  return ROLE_OPTIONS.find((o) => o.value === code)?.text ?? code
}

function onSearch() {
  loadFirstPage()
}

function onFilter() {
  loadFirstPage()
}

async function onLoad() {
  await loadList()
}

async function loadList() {
  if (listLoading.value) return
  listLoading.value = true
  try {
    const enabledParam = filterEnabled.value === '' ? undefined : filterEnabled.value === 'true'
    const res = await getAdminUserPage({
      page: page.value,
      pageSize,
      keyword: keyword.value || undefined,
      roleCode: filterRoleCode.value || undefined,
      enabled: enabledParam
    }) as { code: number; data?: { records: AdminUserItem[]; total: number } }
    if (res.code !== 0 || !res.data) {
      finished.value = true
      return
    }
    const { records, total } = res.data
    if (page.value === 1) list.value = records
    else list.value.push(...records)
    if (list.value.length >= total) finished.value = true
    page.value++
  } catch {
    finished.value = true
  } finally {
    listLoading.value = false
  }
}
function loadFirstPage() {
  page.value = 1
  list.value = []
  finished.value = false
  loadList()
}

function openAdd() {
  editingId.value = null
  form.username = ''
  form.password = ''
  form.realName = ''
  form.phone = ''
  form.email = ''
  form.roleCode = ''
  form.enabled = true
  showFormPopup.value = true
}

function openEdit(u: AdminUserItem) {
  editingId.value = u.id
  form.username = u.username
  form.realName = u.realName ?? ''
  form.phone = u.phone ?? ''
  form.email = u.email ?? ''
  form.roleCode = u.roleCode
  form.enabled = u.enabled
  showFormPopup.value = true
}

function onRoleConfirm({ selectedOptions }: { selectedOptions: { value: string }[] }) {
  if (selectedOptions?.[0]) form.roleCode = selectedOptions[0].value
  showRolePicker.value = false
}

async function onFormSubmit() {
  formLoading.value = true
  try {
    if (editingId.value) {
      const body: UpdateUserBody = {
        realName: form.realName,
        phone: form.phone,
        email: form.email,
        roleCode: form.roleCode,
        enabled: form.enabled
      }
      await updateAdminUser(editingId.value, body)
      showToast('保存成功')
    } else {
      await createAdminUser({
        username: form.username,
        password: form.password || undefined,
        realName: form.realName,
        phone: form.phone,
        email: form.email,
        roleCode: form.roleCode,
        enabled: form.enabled
      })
      showToast('新增成功')
    }
    showFormPopup.value = false
    loadFirstPage()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message
      ?? (e as Error)?.message
      ?? '操作失败'
    showToast(msg)
  } finally {
    formLoading.value = false
  }
}

async function toggleEnable(u: AdminUserItem, enabled: boolean) {
  try {
    await setAdminUserEnabled(u.id, enabled)
    u.enabled = enabled
    showToast(enabled ? '已启用' : '已禁用')
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? '操作失败'
    showToast(msg)
  }
}

async function openResetPwd(u: AdminUserItem) {
  try {
    const res = await resetAdminUserPassword(u.id) as { code: number; data?: { newPassword: string } }
    if (res.code === 0 && res.data?.newPassword) {
      newPassword.value = res.data.newPassword
      resetPwdUserId.value = u.id
      showPwdDialog.value = true
    } else {
      showToast('重置失败')
    }
  } catch {
    showToast('重置失败')
  }
}

function copyNewPassword() {
  if (!newPassword.value) return
  navigator.clipboard.writeText(newPassword.value).then(() => {
    showToast('已复制到剪贴板')
    showPwdDialog.value = false
  }).catch(() => {
    showToast('复制失败')
  })
}

function confirmDelete(u: AdminUserItem) {
  deleteTarget.value = u
  showDeleteConfirm.value = true
}

async function doDelete() {
  if (!deleteTarget.value) return
  try {
    await deleteAdminUser(deleteTarget.value.id)
    showToast('已删除')
    deleteTarget.value = null
    loadFirstPage()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? '删除失败'
    showToast(msg)
  }
}

onMounted(() => {
  loadFirstPage()
})
</script>

<style scoped>
.admin-users {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 24px;
}
.toolbar {
  background: #fff;
}
.list-wrap {
  padding: 12px;
}
.add-btn {
  margin-bottom: 12px;
}
.cell-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.form-body {
  padding: 16px 0 32px;
}
.form-footer {
  padding: 16px;
}
.pwd-display {
  padding: 16px;
  word-break: break-all;
  font-family: monospace;
  font-size: 14px;
}
</style>
