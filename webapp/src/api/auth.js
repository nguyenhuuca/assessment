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
    setup: (username) => api.get(`/user/mfa/setup?username=${encodeURIComponent(username)}`),
    verify: (otp, username, sessionToken) => api.post('/user/mfa/verify', { otp, username, sessionToken }),
    enable: (otp, username, secret) => api.post('/user/mfa/enable', { otp, username, secret }),
    disable: (otp, username) => api.post('/user/mfa/disable', { otp, username }),
  },
}
