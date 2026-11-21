// PrettyZk API client

import { backendState } from './backend.ts'

const DEFAULT_TIMEOUT = 30000 // 30s; connect waits for the TreeCache snapshot
const BACKEND_WAIT_TIMEOUT = 180000 // Rust waits 120s for a port + 30s health

const backendWindow = typeof window !== 'undefined' ? window : null
let backendBase: string | null = backendWindow?.__PRETTYZK_BACKEND__ ?? null
let backendError: string | null = backendWindow?.__PRETTYZK_BACKEND_ERROR__ ?? null

type BaseWaiter = {
  resolve: (base: string) => void
  reject: (error: Error) => void
  timer: ReturnType<typeof setTimeout>
}
let baseWaiters: BaseWaiter[] = []

function runningInTauri(): boolean {
  return (
    !!backendWindow &&
    (backendWindow.isTauri === true || '__TAURI_INTERNALS__' in backendWindow)
  )
}

function markReady() {
  backendState.ready = true
  backendState.error = ''
}

function markError(message: string) {
  backendError = message
  backendState.ready = false
  backendState.error = message
}

if (runningInTauri()) {
  if (backendBase !== null) markReady()
  else if (backendError !== null) markError(backendError)
} else {
  // Browser / Vite dev: the UI is served by the Java backend itself.
  markReady()
}

function flushBaseWaiters() {
  if (backendBase === null && backendError === null) return
  const waiters = baseWaiters
  baseWaiters = []
  if (backendBase !== null) {
    const base = backendBase
    for (const waiter of waiters) {
      clearTimeout(waiter.timer)
      waiter.resolve(base)
    }
  } else if (backendError !== null) {
    const error = new Error(backendError)
    for (const waiter of waiters) {
      clearTimeout(waiter.timer)
      waiter.reject(error)
    }
  }
}

if (backendWindow) {
  backendWindow.addEventListener('prettyzk-backend-ready', () => {
    backendBase = backendWindow.__PRETTYZK_BACKEND__ ?? null
    if (backendBase !== null) {
      backendError = null
      markReady()
    }
    flushBaseWaiters()
  })
  backendWindow.addEventListener('prettyzk-backend-error', (event) => {
    const detail = (event as CustomEvent).detail
    markError(typeof detail === 'string' && detail ? detail : 'local service failed to start')
    flushBaseWaiters()
  })
}

function backendUrl(): Promise<string> {
  if (backendBase !== null) return Promise.resolve(backendBase)
  if (backendError !== null) return Promise.reject(new Error(backendError))
  if (!runningInTauri()) return Promise.resolve('')

  return new Promise((resolve, reject) => {
    const waiter: BaseWaiter = {
      resolve,
      reject,
      timer: setTimeout(() => {
        const index = baseWaiters.indexOf(waiter)
        if (index < 0) return
        baseWaiters.splice(index, 1)
        const message = 'local service did not start in time'
        markError(message)
        flushBaseWaiters()
        reject(new Error(message))
      }, BACKEND_WAIT_TIMEOUT),
    }
    baseWaiters.push(waiter)
  })
}

export async function httpUrl(path: string): Promise<string> {
  return (await backendUrl()) + path
}

export async function openExternal(url: string): Promise<void> {
  if (!/^https?:\/\//i.test(url)) throw new Error('invalid URL')
  if (runningInTauri() && backendWindow?.__TAURI_INTERNALS__) {
    await backendWindow.__TAURI_INTERNALS__.invoke('open_external', { url })
    return
  }
  window.open(url, '_blank', 'noopener,noreferrer')
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
    // Only JSON bodies need this header; sending it on GETs would force a
    // CORS preflight for every request.
    const headers = new Headers(options.headers)
    if (options.body != null && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json')
    }
    const res = await fetch(url, {
      ...options,
      signal: controller.signal,
      headers,
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
