/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  /** 开发时注入的本机局域网 IP，供登录页「手机测试」二维码使用 */
  readonly VITE_DEV_SERVER_HOST?: string
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}
