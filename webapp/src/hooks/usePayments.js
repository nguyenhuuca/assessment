import { useQuery, useMutation } from '@tanstack/react-query'
import * as paymentsApi from '../api/payments.js'

const unwrap = (res) => res?.data ?? res

export function usePlans({ enabled = true } = {}) {
  return useQuery({
    queryKey: ['payments', 'plans'],
    queryFn: async () => unwrap(await paymentsApi.getPlans()),
    enabled,
    staleTime: 5 * 60 * 1000,
  })
}

/** Whether payments are switched on server-side. Treated as disabled while loading or on error. */
export function usePaymentsConfig() {
  const q = useQuery({
    queryKey: ['payments', 'config'],
    queryFn: async () => unwrap(await paymentsApi.getPaymentsConfig()),
    staleTime: 60 * 60 * 1000,
    retry: false,
  })
  return { ...q, enabled: q.data?.enabled === true }
}

export function useCreateOrder() {
  return useMutation({
    mutationFn: async (body) => unwrap(await paymentsApi.createOrder(body)),
  })
}

export const ORDER_POLL_MS = 3000

/** Polls while the order is PENDING and not past expiresAt; any other status stops the polling. */
export function orderRefetchInterval(order, now = Date.now()) {
  if (!order || order.status !== 'PENDING') return false
  const exp = order.expiresAt ? new Date(order.expiresAt).getTime() : NaN
  if (Number.isFinite(exp) && exp <= now) return false
  return ORDER_POLL_MS
}

export function useOrderStatus(code) {
  return useQuery({
    queryKey: ['payments', 'order', code],
    queryFn: async () => unwrap(await paymentsApi.getOrder(code)),
    enabled: !!code,
    refetchInterval: (query) => orderRefetchInterval(query.state.data),
  })
}
