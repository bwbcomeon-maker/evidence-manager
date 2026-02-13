import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import os from 'os'

/** 获取本机局域网 IPv4，用于开发时手机扫码访问（二维码显示 IP 而非 localhost） */
function getLocalNetworkIP(): string {
  const interfaces = os.networkInterfaces()
  for (const name of Object.keys(interfaces)) {
    const iface = interfaces[name]
    if (!iface) continue
    for (const info of iface) {
      if (info.family === 'IPv4' && !info.internal) {
        return info.address
      }
    }
  }
  return ''
}

// 仅开发环境下注入，生产构建不注入
const devHost = process.env.NODE_ENV === 'development' ? getLocalNetworkIP() : ''

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  define: {
    // 供登录页「手机测试」二维码使用：强制使用局域网 IP，避免手机扫到 localhost 无法访问
    'import.meta.env.VITE_DEV_SERVER_HOST': JSON.stringify(devHost)
  },
  server: {
    port: 3000,
    host: true, // 允许局域网访问
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
})
