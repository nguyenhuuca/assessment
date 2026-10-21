import { api, getBaseUrl } from './client.js'

export const NOTIFICATIONS_STREAM_URL = () => `${getBaseUrl()}/notifications/stream`

export const notificationsApi = {
  // category: 'GENERAL' | 'ADMIN' (admins' dropdown tabs); omitted = every type
  list: (page = 0, size = 20, category) =>
    api.get(`/notifications?page=${page}&size=${size}${category ? `&category=${category}` : ''}`),
  unreadCount: () => api.get('/notifications/unread-count'),
  markRead: (id) => api.patch(`/notifications/${encodeURIComponent(id)}/read`),
  markAllRead: () => api.patch('/notifications/read-all'),
}
