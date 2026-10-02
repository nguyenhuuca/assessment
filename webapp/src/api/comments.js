import { api } from './client.js'

export const commentsApi = {
  list: (videoId) => api.get(`/videos/${videoId}/comments`),
  post: (videoId, content, parentId) =>
    api.post(`/videos/${videoId}/comments`, { content, ...(parentId && { parentId }) }),
  delete: (videoId, commentId) => api.delete(`/videos/${videoId}/comments/${commentId}`),
}
