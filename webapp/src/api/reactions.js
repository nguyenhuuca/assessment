import { api } from './client.js'

export const reactionsApi = {
  get: (videoId) => api.get(`/videos/${encodeURIComponent(videoId)}/reaction`),
  set: (videoId, reaction) => api.put(`/videos/${encodeURIComponent(videoId)}/reaction`, { reaction }),
  remove: (videoId) => api.delete(`/videos/${encodeURIComponent(videoId)}/reaction`),
}
