import axios from 'axios'

// baseURL：开发时用 /api（Vite 代理到后端）；直连时须为完整地址且以 /api 结尾，如 http://localhost:8081/api
let baseURL = import.meta.env.VITE_API_BASE_URL ?? '/api'
if (baseURL !== '/api' && !baseURL.endsWith('/api')) {
  baseURL = baseURL.replace(/\/?$/, '') + '/api'
}

const http = axios.create({
  baseURL,
  timeout: 10000,
  withCredentials: true, // 必须：Session 登录需携带 Cookie
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
http.interceptors.request.use(
  (config) => config,
  (error) => Promise.reject(error)
)

// 响应拦截器：code=401 跳转 /login（登录接口本身返回 401 时不跳转，由登录页展示错误信息）
function isLoginRequest(config: { url?: string }): boolean {
  return (config?.url ?? '').includes('/auth/login')
}

http.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      return response.data
    }
    const data = response.data as { code?: number; message?: string; data?: unknown }
    if (data && data.code === 401 && !isLoginRequest(response.config)) {
      window.location.href = '/login'
      return Promise.reject(new Error(data.message || '未登录'))
    }
    return response.data
  },
  (error) => {
    const data = error.response?.data as { code?: number; message?: string } | undefined
    const isLogin = isLoginRequest(error.config ?? {})
    if (data?.code === 401 && !isLogin) {
      window.location.href = '/login'
    }
    if (!isLogin) console.error('API Error:', error)
    return Promise.reject(error)
  }
)

export default http
