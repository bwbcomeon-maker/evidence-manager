<template>
  <div class="login-page">
    <div class="login-layout">
      <!-- 插画区域：盾牌保护文档 -->
      <div class="illustration-wrap">
        <div class="illustration-inner" aria-hidden="true">
          <svg class="login-illustration" viewBox="0 0 280 200" xmlns="http://www.w3.org/2000/svg">
            <defs>
              <linearGradient id="shieldGrad" x1="0%" y1="0%" x2="0%" y2="100%">
                <stop offset="0%" style="stop-color:#1989fa" />
                <stop offset="100%" style="stop-color:#0d6efd" />
              </linearGradient>
              <linearGradient id="docGrad" x1="0%" y1="0%" x2="100%" y2="0%">
                <stop offset="0%" style="stop-color:#e8f4ff" />
                <stop offset="100%" style="stop-color:#d0e8ff" />
              </linearGradient>
            </defs>
            <!-- 背景装饰圆 -->
            <circle cx="240" cy="50" r="60" fill="rgba(25,137,250,0.06)" />
            <circle cx="40" cy="160" r="50" fill="rgba(7,193,96,0.06)" />
            <!-- 一叠文档 -->
            <g transform="translate(80, 100)">
              <rect x="0" y="0" width="64" height="80" rx="4" fill="url(#docGrad)" stroke="rgba(25,137,250,0.2)" stroke-width="1" />
              <rect x="8" y="-6" width="64" height="80" rx="4" fill="url(#docGrad)" stroke="rgba(25,137,250,0.25)" stroke-width="1" />
              <rect x="16" y="-12" width="64" height="80" rx="4" fill="#fff" stroke="rgba(25,137,250,0.3)" stroke-width="1" />
              <line x1="28" y1="28" x2="68" y2="28" stroke="#1989fa" stroke-width="1.5" stroke-opacity="0.6" />
              <line x1="28" y1="38" x2="58" y2="38" stroke="#1989fa" stroke-width="1" stroke-opacity="0.4" />
              <line x1="28" y1="46" x2="62" y2="46" stroke="#1989fa" stroke-width="1" stroke-opacity="0.4" />
            </g>
            <!-- 盾牌 -->
            <g transform="translate(130, 40)">
              <path d="M60 0 L120 8 L120 70 Q120 110 60 130 Q0 110 0 70 L0 8 Z" fill="url(#shieldGrad)" stroke="rgba(255,255,255,0.4)" stroke-width="2" />
              <path d="M60 22 L100 28 L100 68 Q100 92 60 106 Q20 92 20 68 L20 28 Z" fill="rgba(255,255,255,0.25)" />
              <path d="M52 50 L68 50 L68 66 L52 66 Z" fill="#fff" opacity="0.9" />
              <path d="M58 56 L62 56 L62 60 L58 60 Z" fill="#1989fa" opacity="0.8" />
            </g>
            <!-- 对勾/保护符号 -->
            <path d="M168 78 L176 86 L192 66" stroke="#07c160" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" fill="none" opacity="0.95" />
          </svg>
        </div>
      </div>

      <!-- 登录卡片区域 -->
      <section class="login-section">
        <div class="login-card">
          <h1 class="login-card-title">登录</h1>
          <van-form @submit="onSubmit">
            <div class="login-fields">
              <van-field
                v-model="username"
                name="username"
                placeholder="请输入登录账号"
                :rules="[{ required: true, message: '请输入登录账号' }]"
                class="login-field login-field--rounded"
                left-icon="user-o"
              />
              <van-field
                v-model="password"
                :type="passwordVisible ? 'text' : 'password'"
                name="password"
                placeholder="请输入密码"
                :rules="[{ required: true, message: '请输入密码' }]"
                class="login-field login-field--rounded"
                left-icon="lock-o"
              >
                <template #right-icon>
                  <van-icon :name="passwordVisible ? 'closed-eye' : 'eye-o'" class="pwd-toggle-icon" @click="passwordVisible = !passwordVisible" />
                </template>
              </van-field>
            </div>
            <van-cell center class="remember-cell">
              <van-checkbox v-model="rememberMe" shape="square">记住用户名和密码</van-checkbox>
            </van-cell>
            <div class="submit-wrap">
              <van-button size="large" round block type="primary" native-type="submit" :loading="loading" class="login-btn">
                登录
              </van-button>
            </div>
          </van-form>
        </div>
      </section>
    </div>

    <!-- 页脚：版本与技术支持，底部居中 -->
    <footer class="login-footer">版本号: v1.0.0 | 技术支持</footer>
    <button type="button" class="qr-float-btn" aria-label="手机测试" @click="openQrPopup">手机测试</button>

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
import { getFriendlyErrorMessage } from '@/utils/errorMessage'

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
  const host = import.meta.env.VITE_DEV_SERVER_HOST
  if (import.meta.env.DEV && (!host || host === '')) {
    showToast('未获取到本机 IP，请用 npm run dev 启动并查看终端中的 Network 地址')
  }
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
const passwordVisible = ref(false)

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
      const redirect = (route.query.redirect as string) || '/projects'
      router.replace(redirect)
    } else {
      showToast(res.message || '登录失败')
    }
  } catch (e: unknown) {
    showToast(getFriendlyErrorMessage(e, '用户名或密码错误'))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: #fff;
  display: flex;
  flex-direction: column;
}

/* 布局：手机端上插画下卡片，宽屏左侧插画右侧登录 */
.login-layout {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}
@media (min-width: 768px) {
  .login-layout {
    flex-direction: row;
    align-items: center;
    justify-content: center;
    gap: 48px;
    padding: 24px;
  }
}

/* 插画区域 */
.illustration-wrap {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 24px 24px;
  min-height: 200px;
}
@media (min-width: 768px) {
  .illustration-wrap {
    width: 42%;
    max-width: 420px;
    padding: 48px 24px;
    min-height: 320px;
  }
}
.illustration-inner {
  width: 100%;
  max-width: 280px;
}
@media (min-width: 768px) {
  .illustration-inner {
    max-width: 320px;
  }
}
.login-illustration {
  width: 100%;
  height: auto;
  display: block;
}

/* 登录区域 */
.login-section {
  flex: 1;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 16px 20px 32px;
}
@media (min-width: 768px) {
  .login-section {
    width: 58%;
    max-width: 420px;
    align-items: center;
    padding: 48px 24px;
  }
}

/* 登录卡片：圆角 16px，白色，柔和弥散阴影增强悬浮感 */
.login-card {
  width: 100%;
  max-width: 360px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  padding: 28px 20px 24px;
}
.login-card-title {
  margin: 0 0 24px;
  font-size: 24px;
  font-weight: 800;
  color: #323233;
  text-align: center;
}

/* 输入框：圆角矩形，背景 #F7F8FA，8px 圆角，增加内边距与字段间距 */
.login-fields {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.login-field--rounded :deep(.van-cell) {
  padding: 16px 14px;
  background: #F7F8FA;
  border-radius: 8px;
  border: none;
}
.login-field--rounded :deep(.van-cell__value) {
  padding-top: 0;
  padding-bottom: 0;
}
.login-field--rounded :deep(.van-field__body) {
  background: transparent;
  display: flex;
  align-items: center;
}
.login-field--rounded :deep(.van-field__left-icon) {
  display: flex;
  align-items: center;
  transform: translateY(1px);
}
.login-field--rounded :deep(.van-field__control) {
  background: transparent;
}
.login-field--rounded :deep(.van-cell::after) {
  display: none;
}
.login-field--rounded :deep(.van-icon) {
  color: #969799;
}
.pwd-toggle-icon {
  color: #969799;
  cursor: pointer;
  padding: 4px;
}
.remember-cell {
  margin-top: 8px;
  background: transparent !important;
}
.remember-cell :deep(.van-cell::after) {
  display: none;
}

.submit-wrap {
  padding: 24px 0 0;
}
.login-btn {
  background: var(--app-primary, #1989fa) !important;
  border: none !important;
  font-size: 16px !important;
  font-weight: 700 !important;
  min-height: 48px;
  box-shadow: 0 4px 14px rgba(25, 137, 250, 0.2);
}
.login-btn:active {
  box-shadow: 0 2px 8px rgba(25, 137, 250, 0.25);
}

/* 页脚：底部居中，版本与技术支持 */
.login-footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: calc(24px + env(safe-area-inset-bottom, 0));
  text-align: center;
  font-size: 11px;
  color: #c8c9cc;
  padding: 0 16px;
  z-index: 1;
}

/* 手机测试：保留在右下角，供开发扫码（可隐藏或移除） */
.qr-float-btn {
  position: fixed;
  right: 16px;
  bottom: 24px;
  padding: 6px 10px;
  background: transparent;
  color: #c8c9cc;
  border: none;
  font-size: 11px;
  z-index: 100;
  cursor: pointer;
}
.qr-float-btn:active {
  opacity: 0.7;
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
