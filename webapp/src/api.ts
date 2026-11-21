// PrettyZk API client

const DEFAULT_TIMEOUT = 30000 // 30s; connect waits for the TreeCache snapshot

type BackendWindow = Window & {
  __PRETTYZK_BACKEND__?: string
  __PRETTYZK_BACKEND_ERROR__?: string
  __PRETTYZK_FRONTEND_READY__?: boolean
  __TAURI_INTERNALS__?: unknown
}

const backendWindow = typeof window !== 'undefined' ? (window as BackendWindow) : null
let backendBase: string | null = backendWindow?.__PRETTYZK_BACKEND__ ?? null
let backendError: string | null = backendWindow?.__PRETTYZK_BACKEND_ERROR__ ?? null
let baseWaiters: Array<{ resolve: (base: string) => void; reject: (error: Error) => void }> = []

function flushBaseWaiters() {
  if (backendBase !== null) {
    const base = backendBase
    baseWaiters.splice(0).forEach((waiter) => waiter.resolve(base))
  } else if (backendError !== null) {
    const error = new Error(backendError)
    baseWaiters.splice(0).forEach((waiter) => waiter.reject(error))
  }
}

if (backendWindow) {
  backendWindow.addEventListener('prettyzk-backend-ready', () => {
    backendBase = backendWindow.__PRETTYZK_BACKEND__ ?? null
    backendError = null
    flushBaseWaiters()
  })
  backendWindow.addEventListener('prettyzk-backend-error', (event) => {
    const detail = (event as CustomEvent).detail
    backendError = typeof detail === 'string' && detail ? detail : '本地服务启动失败'
    flushBaseWaiters()
  })
}

function runningInTauri(): boolean {
  return !!backendWindow && '__TAURI_INTERNALS__' in backendWindow
}

function backendUrl(): Promise<string> {
  if (backendBase !== null) return Promise.resolve(backendBase)
  if (backendError !== null) return Promise.reject(new Error(backendError))
  // When the UI is served by the Java backend itself (browser / Vite dev),
  // relative URLs already point at it.
  if (!runningInTauri()) return Promise.resolve('')
  return new Promise((resolve, reject) => baseWaiters.push({ resolve, reject }))
}

export async function wsUrl(path: string): Promise<string> {
  const base = await backendUrl()
  if (base) return base.replace(/^http/, 'ws') + path
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}${path}`
}

async function request(path: string, options: RequestInit = {}) {
  const base = await backendUrl()
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), DEFAULT_TIMEOUT)

  try {
    const url = `${base}${path}`
    const res = await fetch(url, {
      signal: controller.signal,
      headers: { 'Content-Type': 'application/json', ...options.headers },
      ...options,
    })
    if (!res.ok) {
      const text = await res.text()
      let msg = text
      try {
        const j = JSON.parse(text)
        msg = j.error || j.message || text
      } catch (_) { /* not JSON, use raw text */ }
      throw new Error(msg || `HTTP ${res.status}`)
    }
    const text = await res.text()
    return text ? JSON.parse(text) : null
  } finally {
    clearTimeout(timeout)
  }
}

export const api = {
  listServers: () => request('/api/servers'),
  getServer: (id: string) => request(`/api/servers/${encodeURIComponent(id)}`),
  saveServer: (data: unknown) => request('/api/servers', { method: 'POST', body: JSON.stringify(data) }),
  deleteServer: (id: string) => request(`/api/servers/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  connect: (id: string) => request(`/api/servers/${encodeURIComponent(id)}/connect`, { method: 'POST' }),
  disconnect: (id: string) => request(`/api/servers/${encodeURIComponent(id)}/disconnect`, { method: 'POST' }),

  listChildren: (serverId: string, path = '/', offset = 0, limit = 5000) =>
    request(
      `/api/nodes/${encodeURIComponent(serverId)}/children?path=${encodeURIComponent(path)}` +
        `&offset=${offset}&limit=${limit}`,
    ),
  getNodeDetail: (serverId: string, path = '/') =>
    request(`/api/nodes/${encodeURIComponent(serverId)}/detail?path=${encodeURIComponent(path)}`),
  createNode: (serverId: string, data: unknown) =>
    request(`/api/nodes/${encodeURIComponent(serverId)}`, { method: 'POST', body: JSON.stringify(data) }),
  updateNode: (serverId: string, data: unknown) =>
    request(`/api/nodes/${encodeURIComponent(serverId)}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteNode: (serverId: string, path: string) =>
    request(`/api/nodes/${encodeURIComponent(serverId)}?path=${encodeURIComponent(path)}`, { method: 'DELETE' }),
  searchNodes: (serverId: string, q: string) =>
    request(`/api/nodes/${encodeURIComponent(serverId)}/search?q=${encodeURIComponent(q)}`),
  execute4LC: (serverId: string, command: string) =>
    request(`/api/nodes/${encodeURIComponent(serverId)}/4lc`, { method: 'POST', body: JSON.stringify({ command }) }),

  getConfig: () => request('/api/config'),
  updateTheme: (theme: string) => request('/api/config/theme', { method: 'PUT', body: JSON.stringify({ theme }) }),
  updateFontSize: (fontSize: number) => request('/api/config/font-size', { method: 'PUT', body: JSON.stringify({ fontSize }) }),
  updateLocale: (locale: string) => request('/api/config/locale', { method: 'PUT', body: JSON.stringify({ locale }) }),
  exportConfig: () => request('/api/config/export', { method: 'POST' }),
  importConfig: (json: string) => request('/api/config/import', { method: 'POST', body: json }),

  version: () => request('/api/version'),
  checkLatestVersion: () => request('/api/version/latest'),
}
