import { api } from './client.js'

export const commentsApi = {
  list: (videoId) => api.get(`/videos/${videoId}/comments`),
  post: (videoId, content) => api.post(`/videos/${videoId}/comments`, { content }),
  delete: (videoId, commentId) => api.delete(`/videos/${videoId}/comments/${commentId}`),
}
