import { api } from './client.js'

// PATCH goes through the shared client: Accept-Language, 204 handling, error code and TOKEN_REVOKED logout
const patch = (path, body) => api.patch(path, body)

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
export const updateVideoVisibility = (id, hidden) => patch(`/admin/videos/${id}/visibility`, { hidden })
export const deleteVideo       = (id) => api.delete(`/admin/videos/${id}`)
export const getAccounts       = (params) => api.get(buildUrl('/admin/accounts', params))
export const updateAccountRole = (id, role) => patch(`/admin/accounts/${id}/role`, { role })
export const updateAccountPermissions = (id, permissions) => patch(`/admin/accounts/${id}/permissions`, { permissions })
export const deleteAccount     = (id) => api.delete(`/admin/accounts/${id}`)
export const getStats          = () => api.get('/admin/stats')

// Omit empty filters so the query string only carries meaningful params.
const cleanParams = (params = {}) =>
  Object.fromEntries(Object.entries(params).filter(([, v]) => v !== null && v !== undefined && v !== ''))

export const getComments       = (params) => api.get(buildUrl('/admin/comments', cleanParams(params)))
export const moderateComment   = (id, body) => patch(`/admin/comments/${id}/moderation`, body)
export const bulkModerateComments = (body) => patch('/admin/comments/moderation', body)
export const hardDeleteComments = (ids) => api.post('/admin/comments/hard-delete', { ids })

// Video import (admin) — see docs/plans/plan-admin-video-import.md "API Contract"
export const createImports = ({ items, scheduledAt = null }) =>
  api.post('/admin/video-imports', { items, scheduledAt })
export const listImports   = (params) => api.get(buildUrl('/admin/video-imports', cleanParams(params)))
export const previewImport = (url) => api.post('/admin/video-imports/preview', { url })
export const runImportNow  = (id) => api.post(`/admin/video-imports/${id}/run-now`)
export const cancelImport  = (id) => api.post(`/admin/video-imports/${id}/cancel`)
export const retryImport   = (id) => api.post(`/admin/video-imports/${id}/retry`)

// Drive force sync — see docs/plans/plan-admin-drive-force-sync.md
export const getDriveSyncFiles  = (params = {}) => api.get(buildUrl('/admin/drive-sync/files', cleanParams(params)))
export const startDriveSyncRun  = (fileIds) => api.post('/admin/drive-sync/runs', { fileIds })
export const getDriveSyncRun    = () => api.get('/admin/drive-sync/runs/current')
