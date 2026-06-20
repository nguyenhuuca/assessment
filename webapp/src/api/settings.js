import { api } from './client.js'

export const settingsApi = {
  get: () => api.get('/user/settings'),
  patch: (partial) => api.patch('/user/settings', partial),
  deleteAccount: (body) => api.delete('/user/account', body),
}
