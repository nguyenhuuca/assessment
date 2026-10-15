import { api } from './client.js'

const hasSession = () => {
  try { return !!localStorage.getItem('jwt') } catch { return false }
}

export const watchHistoryApi = {
  list: (q = '', page = 0, size = 20) => {
    const params = new URLSearchParams()
    const term = (q || '').trim()
    if (term) params.set('q', term)
    params.set('page', String(page))
    params.set('size', String(size))
    return api.get(`/watch-history?${params.toString()}`)
  },
  summary: () => api.get('/watch-history/summary'),
  /** Fire-and-forget resume point. keepalive lets it survive a tab close; guests never call the server. */
  progress: (videoId, positionSec, durationSec) => {
    if (!hasSession()) return Promise.resolve(null)
    return api.put(`/watch-history/${encodeURIComponent(videoId)}/progress`, { positionSec, durationSec }, { keepalive: true })
  },
  remove: (videoId) => api.delete(`/watch-history/${encodeURIComponent(videoId)}`),
  clear: () => api.delete('/watch-history'),
}
