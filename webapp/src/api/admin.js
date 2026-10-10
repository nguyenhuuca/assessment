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
export const updateVideoPriority = (id, priority) => patch(`/admin/videos/${id}/priority`, { priority })
export const deleteVideo       = (id) => api.delete(`/admin/videos/${id}`)
export const getAccounts       = (params) => api.get(buildUrl('/admin/accounts', params))
export const updateAccountRole = (id, role) => patch(`/admin/accounts/${id}/role`, { role })
export const deleteAccount     = (id) => api.delete(`/admin/accounts/${id}`)
export const getStats          = () => api.get('/admin/stats')

// Omit empty filters so the query string only carries meaningful params.
const cleanParams = (params = {}) =>
  Object.fromEntries(Object.entries(params).filter(([, v]) => v !== null && v !== undefined && v !== ''))

export const getComments       = (params) => api.get(buildUrl('/admin/comments', cleanParams(params)))
export const moderateComment   = (id, body) => patch(`/admin/comments/${id}/moderation`, body)
export const bulkModerateComments = (body) => patch('/admin/comments/moderation', body)

// Video import (admin) — see docs/plans/plan-admin-video-import.md "API Contract"
export const createImports = ({ items, scheduledAt = null }) =>
  api.post('/admin/video-imports', { items, scheduledAt })
export const listImports   = (params) => api.get(buildUrl('/admin/video-imports', cleanParams(params)))
export const previewImport = (url) => api.post('/admin/video-imports/preview', { url })
export const runImportNow  = (id) => api.post(`/admin/video-imports/${id}/run-now`)
export const cancelImport  = (id) => api.post(`/admin/video-imports/${id}/cancel`)
export const retryImport   = (id) => api.post(`/admin/video-imports/${id}/retry`)
