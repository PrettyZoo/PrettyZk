// PrettyZk API client

const API_BASE = ''
const DEFAULT_TIMEOUT = 15000 // 15s

async function request(path, options: RequestInit = {}) {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), DEFAULT_TIMEOUT)

  try {
    const url = `${API_BASE}${path}`
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

  listNodes: (serverId: string, path = '/') =>
    request(`/api/nodes/${encodeURIComponent(serverId)}?path=${encodeURIComponent(path)}`),
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
}
