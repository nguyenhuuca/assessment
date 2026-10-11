import { api } from './client.js'

export const viewsApi = {
  record: (videoId) => api.post(`/videos/${encodeURIComponent(videoId)}/view`),
}
