import { reactive } from 'vue'

export interface AppConfig {
  theme: string
  fontSize: number
  locale: string
}

export interface ServerSummary {
  id: string
  alias?: string
  host: string
  port: number
  [key: string]: unknown
}

export const state = reactive({
  servers: [] as ServerSummary[],
  serverStatuses: {} as Record<string, string>,
  config: { theme: 'default', fontSize: 14, locale: 'en' } as AppConfig,
})
