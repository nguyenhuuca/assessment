import { api } from './client.js'

const query = (params) => {
  const qs = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') qs.set(k, String(v))
  }
  const s = qs.toString()
  return s ? `?${s}` : ''
}

export const suggestionsApi = {
  // user
  submit: ({ url, title, note }) => api.post('/suggestions', {
    url,
    ...(title ? { title } : {}),
    ...(note ? { note } : {}),
  }),
  mine: (status = '', page = 0, size = 20) => api.get(`/suggestions/mine${query({ status, page, size })}`),
  // admin
  adminList: (status = '', page = 0, size = 20) => api.get(`/admin/video-suggestions${query({ status, page, size })}`),
  pendingCount: () => api.get('/admin/video-suggestions/pending-count'),
  approve: (id, title) => api.post(`/admin/video-suggestions/${id}/approve`, title ? { title } : {}),
  reject: (id, reason, note) => api.post(`/admin/video-suggestions/${id}/reject`, { reason, ...(note ? { note } : {}) }),
}
