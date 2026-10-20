import { api } from './client.js'

function buildUrl(path, params) {
  if (!params || Object.keys(params).length === 0) return path
  const query = Object.entries(params)
    .filter(([, v]) => v !== null && v !== undefined)
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&')
  return query ? `${path}?${query}` : path
}

// In-app report -> GitHub issue (docs/specs/spec-in-app-report.md "API Changes")
export const createReport = (body) => api.post('/admin/reports', body)
export const listReports  = ({ page = 0, size = 20 } = {}) => api.get(buildUrl('/admin/reports', { page, size }))
export const retryReport  = (id) => api.post(`/admin/reports/${encodeURIComponent(id)}/retry`)
