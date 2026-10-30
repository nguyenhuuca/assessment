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
// visibility: 'FEED' | 'COLLECTION_ONLY' | 'HIDDEN'
export const updateVideoVisibility = (id, visibility) => patch(`/admin/videos/${id}/visibility`, { visibility })
// HLS opt-in per video (SUPER_ADMIN): enable queues a conversion, disable removes the HLS output
export const enableVideoHls    = (id) => api.put(`/admin/videos/${id}/hls`)
export const disableVideoHls   = (id) => api.delete(`/admin/videos/${id}/hls`)
// File flags are sent only when checked; the server defaults both to false.
export const deleteVideo       = (id, { deleteLocalFile = false, deleteDriveFile = false } = {}) =>
  api.delete(buildUrl(`/admin/videos/${id}`, {
    deleteLocalFile: deleteLocalFile || null,
    deleteDriveFile: deleteDriveFile || null,
  }))
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
// visibility is sent only for 'COLLECTION_ONLY'; omitted means the server default (FEED)
export const createImports = ({ items, scheduledAt = null, visibility }) =>
  api.post('/admin/video-imports', visibility ? { items, scheduledAt, visibility } : { items, scheduledAt })
export const listImports   = (params) => api.get(buildUrl('/admin/video-imports', cleanParams(params)))
export const previewImport = (url) => api.post('/admin/video-imports/preview', { url })
export const runImportNow  = (id) => api.post(`/admin/video-imports/${id}/run-now`)
export const cancelImport  = (id) => api.post(`/admin/video-imports/${id}/cancel`)
export const retryImport   = (id) => api.post(`/admin/video-imports/${id}/retry`)

// Scheduled jobs — see docs/adr/0023-dynamic-cron-jobs.md D4
export const getJobs   = () => api.get('/admin/jobs')
export const updateJob = (name, body) => patch(`/admin/jobs/${encodeURIComponent(name)}`, body)
export const runJob    = (name) => api.post(`/admin/jobs/${encodeURIComponent(name)}/runs`)
export const previewJobCron = (cron, zone, count = 5) =>
  api.get(buildUrl('/admin/jobs/preview', { cron, zone: zone || undefined, count }))

// Drive force sync — see docs/plans/plan-admin-drive-force-sync.md
export const getDriveSyncFiles  = (params = {}) => api.get(buildUrl('/admin/drive-sync/files', cleanParams(params)))
export const startDriveSyncRun  = (fileIds) => api.post('/admin/drive-sync/runs', { fileIds })
export const getDriveSyncRun    = () => api.get('/admin/drive-sync/runs/current')
