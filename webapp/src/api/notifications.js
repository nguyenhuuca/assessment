import { api, getBaseUrl } from './client.js'

export const NOTIFICATIONS_STREAM_URL = () => `${getBaseUrl()}/notifications/stream`

export const notificationsApi = {
  list: (page = 0, size = 20) => api.get(`/notifications?page=${page}&size=${size}`),
  unreadCount: () => api.get('/notifications/unread-count'),
  markRead: (id) => api.patch(`/notifications/${encodeURIComponent(id)}/read`),
  markAllRead: () => api.patch('/notifications/read-all'),
}
