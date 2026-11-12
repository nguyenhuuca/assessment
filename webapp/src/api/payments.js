import { api } from './client.js'

const BASE = '/payments'

export const getPlans    = () => api.get(`${BASE}/plans`)
export const createOrder = (body) => api.post(`${BASE}/orders`, body)
export const getOrder    = (code) => api.get(`${BASE}/orders/${encodeURIComponent(code)}`)
export const getPaymentsConfig = () => api.get(`${BASE}/config`)
