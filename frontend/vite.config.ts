import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import os from 'os'

/** 获取本机局域网 IPv4，用于开发时手机扫码访问（二维码显示 IP 而非 localhost） */
function getLocalNetworkIP(): string {
  const interfaces = os.networkInterfaces()
  // 优先常见网卡名（避免拿到 Docker/虚拟网卡）
  const preferOrder = ['en0', 'en1', 'eth0', 'Ethernet', 'WLAN', 'Wi-Fi']
  for (const name of preferOrder) {
    const iface = interfaces[name]
    if (!iface) continue
    for (const info of iface) {
      if ((info.family === 'IPv4' || info.family === 4) && !info.internal) {
        return info.address
      }
    }
  }
  for (const name of Object.keys(interfaces)) {
    const iface = interfaces[name]
    if (!iface) continue
    for (const info of iface) {
      if ((info.family === 'IPv4' || info.family === 4) && !info.internal) {
        return info.address
      }
    }
  }
  return ''
}

// https://vitejs.dev/config/
export default defineConfig(({ command }) => {
  // 仅 dev serve 时注入本机 IP，供登录页「手机测试」二维码使用；build 不注入
  const devHost = command === 'serve' ? getLocalNetworkIP() : ''
  return {
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  define: {
    'import.meta.env.VITE_DEV_SERVER_HOST': JSON.stringify(devHost)
  },
  server: {
    port: 3000,
    host: true, // 允许局域网访问，手机可连同一 WiFi 后通过 IP:3000 访问
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
  }
})
