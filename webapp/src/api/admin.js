import { api } from './client.js'

const getBaseUrl = () => import.meta.env.VITE_API_BASE_URL || 'https://canh-labs.com/api/v1/funny-app'

function createHeaders() {
  const headers = { 'Content-Type': 'application/json' }
  const jwt = localStorage.getItem('jwt')
  const guestToken = localStorage.getItem('guestToken')
  if (jwt) headers['Authorization'] = jwt
  if (guestToken) headers['X-Guest-Token'] = guestToken
  return headers
}

async function patch(path, body) {
  const res = await fetch(`${getBaseUrl()}${path}`, {
    method: 'PATCH',
    headers: createHeaders(),
    body: body ? JSON.stringify(body) : undefined,
  })
  const data = await res.json()
  if (!res.ok) {
    const msg = data?.error?.message || `HTTP ${res.status}`
    throw { message: msg, status: res.status }
  }
  return data
}

function buildUrl(path, params) {
  if (!params || Object.keys(params).length === 0) return path
  const query = Object.entries(params)
    .filter(([, v]) => v !== null && v !== undefined)
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&')
  return query ? `${path}?${query}` : path
}

export const getVideos         = (params) => api.get(buildUrl('/admin/videos', params))
export const updateVideoStatus = (id, status) => patch(`/admin/videos/${id}/status`, { status })
export const deleteVideo       = (id) => api.delete(`/admin/videos/${id}`)
export const getAccounts       = (params) => api.get(buildUrl('/admin/accounts', params))
export const updateAccountRole = (id, role) => patch(`/admin/accounts/${id}/role`, { role })
export const deleteAccount     = (id) => api.delete(`/admin/accounts/${id}`)
export const getStats          = () => api.get('/admin/stats')
