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

// 响应拦截器：code=401 跳转 /login
http.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      return response.data
    }
    const data = response.data as { code?: number; message?: string; data?: unknown }
    if (data && data.code === 401) {
      window.location.href = '/login'
      return Promise.reject(new Error(data.message || '未登录'))
    }
    return response.data
  },
  (error) => {
    const data = error.response?.data as { code?: number; message?: string } | undefined
    if (data?.code === 401) {
      window.location.href = '/login'
    }
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

export default http
