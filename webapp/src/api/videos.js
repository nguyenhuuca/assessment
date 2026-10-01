import { api } from './client.js'

export const videosApi = {
  list: (category) => api.get(`/video-stream/list${category ? `?category=${encodeURIComponent(category)}` : ''}`),
  privateList: () => api.get('/private-videos'),
  share: (data) => api.post('/video/share', data),
  delete: (id) => api.delete(`/share-links/${id}`),
}
