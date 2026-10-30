import { api } from './client.js'

export const videosApi = {
  list: (category) => api.get(`/video-stream/list${category ? `?category=${encodeURIComponent(category)}` : ''}`),
  get: (id) => api.get(`/video-stream/${encodeURIComponent(id)}`),
  delete: (id) => api.delete(`/share-links/${id}`),
}
