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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import { login } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

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
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    const msg = err?.response?.data?.message ?? err?.message ?? '用户名或密码错误'
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
</style>
