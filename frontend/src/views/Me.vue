<template>
  <div class="me-page">
    <van-cell-group inset>
      <van-cell v-if="auth.currentUser" :title="auth.currentUser.realName || auth.currentUser.username" :label="`@${auth.currentUser.username} · ${roleLabel(auth.currentUser.roleCode)}`" />
      <van-cell v-if="auth.isAdmin" title="用户管理" is-link to="/admin/users" />
      <van-cell title="修改密码" is-link @click="showChangePwd = true" />
      <van-cell title="退出登录" is-link @click="onLogout" />
    </van-cell-group>

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

const router = useRouter()
const auth = useAuthStore()
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
  } catch (e: any) {
    const data = e?.response?.data
    const msg = (typeof data === 'object' && data?.message) || (typeof data === 'string' && data) || e?.message || ''
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
  } catch (e: any) {
    const data = e?.response?.data
    const msg =
      (typeof data === 'object' && data?.message) ||
      (typeof data === 'string' && data) ||
      e?.message ||
      ''
    const isNotFound =
      e?.response?.status === 404 || (typeof msg === 'string' && msg.includes('No static resource'))
    showToast(
      isNotFound ? '修改密码接口不可用，请确认后端已重新编译并启动' : getChangePwdFriendlyMessage(msg || '修改失败')
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
  padding: 16px 0;
  min-height: 100vh;
}

.pwd-toggle-icon {
  padding: 0 4px;
  cursor: pointer;
  font-size: 18px;
  color: var(--van-gray-6);
}
</style>
