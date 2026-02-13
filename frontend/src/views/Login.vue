<template>
  <div class="login-page">
    <van-nav-bar title="登录" />
    <div class="content">
      <van-form @submit="onSubmit">
        <van-cell-group inset>
          <van-field
            v-model="username"
            name="username"
            label="账号"
            placeholder="请输入登录账号"
            :rules="[{ required: true, message: '请输入登录账号' }]"
          />
          <van-field
            v-model="password"
            type="password"
            name="password"
            label="密码"
            placeholder="请输入密码"
            :rules="[{ required: true, message: '请输入密码' }]"
          />
          <van-cell center>
            <van-checkbox v-model="rememberMe" shape="square">记住用户名和密码</van-checkbox>
          </van-cell>
        </van-cell-group>
        <div class="submit-wrap">
          <van-button round block type="primary" native-type="submit" :loading="loading">
            登录
          </van-button>
        </div>
      </van-form>
    </div>

    <!-- 手机测试：右下角悬浮按钮 -->
    <div class="qr-float-btn" @click="openQrPopup">
      <van-icon name="scan" size="20" />
      <span class="qr-float-text">手机测试</span>
    </div>

    <!-- 扫码访问二维码弹层 -->
    <van-popup
      v-model:show="showQrPopup"
      position="center"
      round
      :style="{ width: '280px', padding: '20px' }"
      @closed="qrDataUrl = ''"
    >
      <div class="qr-popup-content">
        <div class="qr-popup-title">扫码用手机访问</div>
        <div class="qr-popup-canvas">
          <img v-if="qrDataUrl" :src="qrDataUrl" alt="访问二维码" class="qr-img" />
          <div v-else class="qr-loading">生成中...</div>
        </div>
        <div class="qr-popup-tip">请使用微信/浏览器扫一扫</div>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import QRCode from 'qrcode'
import { login } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

// ---------- 手机扫码访问（开发用） ----------
const showQrPopup = ref(false)
const qrDataUrl = ref('')

/** 生成二维码的目标 URL：开发环境强制使用本机局域网 IP，避免手机扫到 localhost 无法访问 */
function getQrTargetUrl(): string {
  if (typeof window === 'undefined') return ''
  const port = window.location.port || '3000'
  const host = import.meta.env.VITE_DEV_SERVER_HOST
  const base =
    import.meta.env.DEV && host ? `http://${host}:${port}` : window.location.origin
  return base + window.location.pathname + window.location.search
}

async function openQrPopup() {
  const url = getQrTargetUrl()
  qrDataUrl.value = ''
  showQrPopup.value = true
  try {
    qrDataUrl.value = await QRCode.toDataURL(url, { width: 240, margin: 1 })
  } catch {
    showToast('二维码生成失败')
    showQrPopup.value = false
  }
}

const REMEMBER_KEY = 'evidence_login_remember'

function loadRemembered(): { username: string; password: string } | null {
  try {
    const raw = localStorage.getItem(REMEMBER_KEY)
    if (!raw) return null
    const obj = JSON.parse(raw) as { username?: string; password?: string }
    if (obj && typeof obj.username === 'string' && typeof obj.password === 'string') {
      return { username: obj.username, password: obj.password }
    }
  } catch {
    // ignore
  }
  return null
}

function saveRemembered(u: string, p: string) {
  try {
    localStorage.setItem(REMEMBER_KEY, JSON.stringify({ username: u, password: p }))
  } catch {
    // ignore
  }
}

function clearRemembered() {
  try {
    localStorage.removeItem(REMEMBER_KEY)
  } catch {
    // ignore
  }
}

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const username = ref('')
const password = ref('')
const rememberMe = ref(false)
const loading = ref(false)

onMounted(() => {
  const saved = loadRemembered()
  if (saved) {
    username.value = saved.username
    password.value = saved.password
    rememberMe.value = true
  }
})

const onSubmit = async () => {
  loading.value = true
  try {
    const res = await login({ username: username.value, password: password.value }) as { code: number; message?: string; data?: { id: number; username: string; realName: string; roleCode: string; enabled: boolean } }
    if (res.code === 0) {
      if (rememberMe.value) {
        saveRemembered(username.value, password.value)
      } else {
        clearRemembered()
      }
      if (res.data) auth.setUser(res.data)
      showToast('登录成功')
      const redirect = (route.query.redirect as string) || '/home'
      router.replace(redirect)
    } else {
      showToast(res.message || '登录失败')
    }
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string }; status?: number }; message?: string; code?: string }
    const msg =
      err?.response?.data?.message ??
      (err?.response ? undefined : '网络错误，请确认后端已启动（端口 8081）') ??
      (err?.code === 'ECONNABORTED' ? '请求超时' : err?.message) ??
      '用户名或密码错误'
    showToast(msg)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: #f7f8fa;
}
.content {
  padding: 24px 0;
}
.submit-wrap {
  padding: 24px 16px;
}

/* 手机测试：右下角悬浮按钮 */
.qr-float-btn {
  position: fixed;
  right: 16px;
  bottom: 24px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  border-radius: 24px;
  font-size: 13px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  z-index: 100;
}
.qr-float-btn:active {
  opacity: 0.9;
}
.qr-float-text {
  line-height: 1;
}

/* 二维码弹层 */
.qr-popup-content {
  text-align: center;
}
.qr-popup-title {
  font-size: 16px;
  font-weight: 600;
  color: #323233;
  margin-bottom: 16px;
}
.qr-popup-canvas {
  width: 240px;
  height: 240px;
  margin: 0 auto 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f7f8fa;
  border-radius: 8px;
}
.qr-img {
  width: 240px;
  height: 240px;
  display: block;
}
.qr-loading {
  color: #969799;
  font-size: 14px;
}
.qr-popup-tip {
  font-size: 12px;
  color: #969799;
}
</style>
