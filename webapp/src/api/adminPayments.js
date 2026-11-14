import { api } from './client.js'

const BASE = '/admin/payments'

function qs(params) {
  const q = Object.entries(params || {})
    .filter(([, v]) => v !== null && v !== undefined && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&')
  return q ? `?${q}` : ''
}

export const getSummary      = () => api.get(`${BASE}/summary`)
export const getOrders       = (params) => api.get(`${BASE}/orders${qs(params)}`)
export const getUnmatched    = (params) => api.get(`${BASE}/transactions/unmatched${qs(params)}`)
export const ignoreTransaction = (sepayId) => api.post(`${BASE}/transactions/${encodeURIComponent(sepayId)}/ignore`, {})
export const linkTransaction = (sepayId, orderCode) => api.post(`${BASE}/transactions/${encodeURIComponent(sepayId)}/link`, { orderCode })
export const resolveOrder    = (id, grantPremium) => api.post(`${BASE}/orders/${encodeURIComponent(id)}/resolve`, { grantPremium })
export const markOrderPaid   = (id, amount) => api.post(`${BASE}/orders/${encodeURIComponent(id)}/mark-paid`, { amount })
export const checkOrderOnSepay = (id) => api.post(`${BASE}/orders/${encodeURIComponent(id)}/check-sepay`, {})
export const cancelOrder     = (id) => api.post(`${BASE}/orders/${encodeURIComponent(id)}/cancel`, {})
export const getPlans        = () => api.get(`${BASE}/plans`)
export const createPlan      = (body) => api.post(`${BASE}/plans`, body)
export const updatePlan      = (id, body) => api.put(`${BASE}/plans/${encodeURIComponent(id)}`, body)
export const setPlanActive   = (id, active) => api.patch(`${BASE}/plans/${encodeURIComponent(id)}/active`, { active })
