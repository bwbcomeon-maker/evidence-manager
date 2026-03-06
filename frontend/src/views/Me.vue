<template>
  <div class="me-page">
    <!-- 用户信息卡片 -->
    <div class="me-card" v-if="auth.currentUser">
      <div class="me-user-row">
        <span class="me-user-name">{{ auth.currentUser.realName || auth.currentUser.username }}</span>
        <span class="me-user-meta">@{{ auth.currentUser.username }} · {{ roleLabel(auth.currentUser.roleCode) }}</span>
      </div>
    </div>

    <!-- 分组列表：白色圆角卡片，左侧图标 + 右侧箭头 + 底部分割线 -->
    <div class="me-card me-card--list">
      <van-cell
        v-if="auth.isAdmin"
        title="用户管理"
        icon="manager-o"
        is-link
        class="me-cell"
        @click="goToAdminUsers"
      />
      <van-cell
        title="修改密码"
        icon="lock"
        is-link
        class="me-cell"
        @click="showChangePwd = true"
      />
      <van-cell
        title="退出登录"
        icon="warning-o"
        is-link
        class="me-cell me-cell--last"
        @click="onLogout"
      />
    </div>

    <van-dialog
      v-model:show="showChangePwd"
      title="修改密码"
      show-cancel-button
      :before-close="onChangePwdConfirm"
    >
      <van-cell-group inset>
        <van-field
          v-model="changePwdForm.oldPassword"
          :type="showOldPwd ? 'text' : 'password'"
          label="原密码"
          placeholder="请输入原密码"
          clearable
        >
          <template #right-icon>
            <van-icon
              :name="showOldPwd ? 'closed-eye' : 'eye-o'"
              class="pwd-toggle-icon"
              @click="showOldPwd = !showOldPwd"
            />
          </template>
        </van-field>
        <van-field
          v-model="changePwdForm.newPassword"
          :type="showNewPwd ? 'text' : 'password'"
          label="新密码"
          placeholder="请输入新密码"
          clearable
        >
          <template #right-icon>
            <van-icon
              :name="showNewPwd ? 'closed-eye' : 'eye-o'"
              class="pwd-toggle-icon"
              @click="showNewPwd = !showNewPwd"
            />
          </template>
        </van-field>
        <van-field
          v-model="changePwdForm.confirmPassword"
          :type="showConfirmPwd ? 'text' : 'password'"
          label="确认新密码"
          placeholder="请再次输入新密码"
          clearable
        >
          <template #right-icon>
            <van-icon
              :name="showConfirmPwd ? 'closed-eye' : 'eye-o'"
              class="pwd-toggle-icon"
              @click="showConfirmPwd = !showConfirmPwd"
            />
          </template>
        </van-field>
      </van-cell-group>
    </van-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { changePassword, verifyPassword } from '@/api/auth'
import { showToast } from 'vant'
import { getFriendlyErrorMessage } from '@/utils/errorMessage'

const router = useRouter()
const auth = useAuthStore()

/** 进入用户管理：带时间戳 push，避免历史栈中旧实例被复用 */
function goToAdminUsers() {
  router.push({ path: '/admin/users', query: { _t: String(Date.now()) } })
}
const showChangePwd = ref(false)
const showOldPwd = ref(false)
const showNewPwd = ref(false)
const showConfirmPwd = ref(false)
const changePwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const roleLabels: Record<string, string> = {
  SYSTEM_ADMIN: '系统管理员',
  PMO: 'PMO（治理）',
  AUDITOR: '审计',
  USER: '普通用户'
}

function roleLabel(code: string) {
  return roleLabels[code] ?? code
}

function resetChangePwdForm() {
  changePwdForm.oldPassword = ''
  changePwdForm.newPassword = ''
  changePwdForm.confirmPassword = ''
  showOldPwd.value = false
  showNewPwd.value = false
  showConfirmPwd.value = false
}

/** 修改密码接口返回/抛出的常见错误文案 → 对用户展示的提示 */
function getChangePwdFriendlyMessage(msg: string | undefined): string {
  if (!msg) return '修改失败'
  if (msg.includes('原始密码不正确') || msg.includes('原密码错误') || msg.includes('password')) return '原始密码不正确，不允许修改'
  if (msg.includes('未登录')) return '登录已过期，请重新登录'
  return msg
}

async function onChangePwdConfirm(action: string): Promise<boolean> {
  if (action !== 'confirm') {
    resetChangePwdForm()
    return true
  }
  const { oldPassword, newPassword, confirmPassword } = changePwdForm
  const oldPwd = oldPassword?.trim() ?? ''
  const newPwd = newPassword?.trim() ?? ''
  const confirmPwd = confirmPassword?.trim() ?? ''

  if (!oldPwd) {
    showToast('请输入原密码')
    return false
  }
  if (!newPwd) {
    showToast('请输入新密码')
    return false
  }
  if (newPwd !== confirmPwd) {
    showToast('新密码与确认新密码不一致，请重新输入')
    return false
  }
  if (newPwd === oldPwd) {
    showToast('新密码不能与原密码相同，请重新设置')
    return false
  }
  if (newPwd.length < 6) {
    showToast('新密码至少 6 位')
    return false
  }
  try {
    // 第一步：先验证原密码是否正确，不正确则不允许修改
    const verifyRes = await verifyPassword({ password: oldPwd })
    if (verifyRes.code !== 0) {
      showToast(getChangePwdFriendlyMessage(verifyRes.message) || '原始密码不正确，不允许修改')
      return false
    }
  } catch (e: unknown) {
    const msg = getFriendlyErrorMessage(e, '')
    showToast(getChangePwdFriendlyMessage(msg) || '原始密码不正确，不允许修改')
    return false
  }
  try {
    const res = await changePassword({ oldPassword: oldPwd, newPassword: newPwd })
    if (res.code === 0) {
      showToast('密码已修改，请重新登录')
      resetChangePwdForm()
      showChangePwd.value = false
      await auth.logout()
      router.replace('/login')
      return true
    }
    showToast(getChangePwdFriendlyMessage(res.message))
    return false
  } catch (e: unknown) {
    const err = e as { response?: { status?: number } }
    const isNotFound = err?.response?.status === 404
    const msg = getFriendlyErrorMessage(e, '修改失败')
    showToast(
      isNotFound ? '修改密码接口不可用，请确认后端已重新编译并启动' : getChangePwdFriendlyMessage(msg)
    )
    return false
  }
}

async function onLogout() {
  await auth.logout()
  router.replace('/login')
}
</script>

<style scoped>
.me-page {
  padding: 16px;
  min-height: 100vh;
  background: var(--app-bg);
}

.me-card {
  background: var(--app-card);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-card-shadow);
  margin-bottom: 12px;
  overflow: hidden;
}

.me-user-row {
  padding: 16px;
  min-height: var(--app-tap-min-height);
}
.me-user-name {
  display: block;
  font-size: 18px;
  font-weight: 600;
  color: #323233;
}
.me-user-meta {
  display: block;
  font-size: 13px;
  color: #969799;
  margin-top: 4px;
}

.me-card--list :deep(.van-cell) {
  min-height: var(--app-tap-min-height);
  padding: 0 16px;
  display: flex;
  align-items: center;
}
.me-card--list :deep(.van-cell__left-icon),
.me-card--list :deep(.van-cell__title),
.me-card--list :deep(.van-cell__value),
.me-card--list :deep(.van-cell__right-icon) {
  display: flex;
  align-items: center;
}
.me-card--list :deep(.van-cell::after) {
  border-bottom: 1px solid #ebedf0;
  left: 16px;
  right: 0;
}
.me-cell.me-cell--last :deep(.van-cell::after),
.me-card--list :deep(.van-cell:last-child::after) {
  display: none;
}
.me-card--list :deep(.van-cell__left-icon) {
  margin-right: 12px;
  color: var(--app-primary);
}
.me-card--list :deep(.van-cell__right-icon) {
  color: #c8c9cc;
}

.me-todo-badge {
  margin-left: 4px;
}

.pwd-toggle-icon {
  padding: 0 4px;
  cursor: pointer;
  font-size: 18px;
  color: var(--van-gray-6);
}
</style>
