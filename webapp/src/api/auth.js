import { api } from './client.js'

export const authApi = {
  join: (email) => api.post('/user/join', { email }),
  me: () => api.get('/user/me'),
  authOptions: () => api.get('/user/auth-options'),
  login: (email, password) => api.post('/user/login', { email, password }),
  setPassword: (body) => api.put('/user/password', body),
  removePassword: (body) => api.delete('/user/password', body),
  verifyMagic: (token) => api.get(`/user/verify-magic?token=${encodeURIComponent(token)}`),
  mfa: {
    setup: () => api.get('/user/mfa/setup'),
    verify: (otp, username, sessionToken) => api.post('/user/mfa/verify', { otp, username, sessionToken }),
    enable: (otp, secret) => api.post('/user/mfa/enable', { otp, secret }),
    disable: (otp) => api.post('/user/mfa/disable', { otp }),
  },
}
