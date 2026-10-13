import { api } from './client.js'

const enc = encodeURIComponent

export const collectionsApi = {
  mine: () => api.get('/collections'),
  publicList: () => api.get('/collections/public'),
  create: (body) => api.post('/collections', body),
  update: (id, body) => api.patch(`/collections/${enc(id)}`, body),
  remove: (id) => api.delete(`/collections/${enc(id)}`),
  videos: (id, page = 0, size = 24) => api.get(`/collections/${enc(id)}/videos?page=${page}&size=${size}`),
  addVideo: (id, videoId) => api.put(`/collections/${enc(id)}/videos/${enc(videoId)}`),
  removeVideo: (id, videoId) => api.delete(`/collections/${enc(id)}/videos/${enc(videoId)}`),
  membership: (videoId) => api.get(`/collections/membership?videoId=${enc(videoId)}`),
}
