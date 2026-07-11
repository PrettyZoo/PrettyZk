/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

interface Window {
  __toast?: (message: string, type?: string, duration?: number) => void
  __dialog?: {
    show: boolean
    title: string
    message: string
    resolve?: (value: boolean) => void
  }
  __t?: (key: string, params?: Record<string, string>) => string
}
