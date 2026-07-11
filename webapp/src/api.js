// PrettyZk API client

const API_BASE = ''

async function request(path, options = {}) {
  const url = `${API_BASE}${path}`
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  })
  if (!res.ok) {
    const text = await res.text()
    let msg = text
    try {
      const j = JSON.parse(text)
      msg = j.error || j.message || text
    } catch (_) {}
    throw new Error(msg || `HTTP ${res.status}`)
  }
  const text = await res.text()
  return text ? JSON.parse(text) : null
}

export const api = {
  listServers: () => request('/api/servers'),
  getServer: (id) => request(`/api/servers/${encodeURIComponent(id)}`),
  saveServer: (data) => request('/api/servers', { method: 'POST', body: JSON.stringify(data) }),
  deleteServer: (id) => request(`/api/servers/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  connect: (id) => request(`/api/servers/${encodeURIComponent(id)}/connect`, { method: 'POST' }),
  disconnect: (id) => request(`/api/servers/${encodeURIComponent(id)}/disconnect`, { method: 'POST' }),

  listNodes: (serverId, path = '/') =>
    request(`/api/nodes/${encodeURIComponent(serverId)}?path=${encodeURIComponent(path)}`),
  createNode: (serverId, data) =>
    request(`/api/nodes/${encodeURIComponent(serverId)}`, { method: 'POST', body: JSON.stringify(data) }),
  updateNode: (serverId, data) =>
    request(`/api/nodes/${encodeURIComponent(serverId)}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteNode: (serverId, path) =>
    request(`/api/nodes/${encodeURIComponent(serverId)}?path=${encodeURIComponent(path)}`, { method: 'DELETE' }),
  searchNodes: (serverId, q) =>
    request(`/api/nodes/${encodeURIComponent(serverId)}/search?q=${encodeURIComponent(q)}`),
  execute4LC: (serverId, command) =>
    request(`/api/nodes/${encodeURIComponent(serverId)}/4lc`, { method: 'POST', body: JSON.stringify({ command }) }),

  getConfig: () => request('/api/config'),
  updateTheme: (theme) => request('/api/config/theme', { method: 'PUT', body: JSON.stringify({ theme }) }),
  updateFontSize: (fontSize) => request('/api/config/font-size', { method: 'PUT', body: JSON.stringify({ fontSize }) }),

  version: () => request('/api/version'),
}

export function updateLocale(locale) {
  return fetchApi('/api/config/locale', { method: 'PUT', body: JSON.stringify({ locale }) })
}

export function exportConfig() {
  return fetch('/api/config/export').then(r => r.json())
}

export function importConfig(json) {
  return fetchApi('/api/config/import', { method: 'POST', body: json })
}
