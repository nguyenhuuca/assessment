import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import * as adminPayments from '../api/adminPayments.js'

const unwrap = (res) => res?.data ?? res
const KEY = ['admin', 'payments']

export const useAdminPaymentsSummary = () =>
  useQuery({ queryKey: [...KEY, 'summary'], queryFn: async () => unwrap(await adminPayments.getSummary()) })

export const useAdminPaymentOrders = (params) =>
  useQuery({
    queryKey: [...KEY, 'orders', params],
    queryFn: async () => unwrap(await adminPayments.getOrders(params)),
    placeholderData: keepPreviousData,
  })

export const useAdminUnmatched = (params) =>
  useQuery({
    queryKey: [...KEY, 'unmatched', params],
    queryFn: async () => unwrap(await adminPayments.getUnmatched(params)),
    placeholderData: keepPreviousData,
  })

export const useAdminPaymentPlans = () =>
  useQuery({ queryKey: [...KEY, 'plans'], queryFn: async () => unwrap(await adminPayments.getPlans()) })

function useInvalidating(mutationFn) {
  const qc = useQueryClient()
  return useMutation({ mutationFn, onSuccess: () => qc.invalidateQueries({ queryKey: KEY }) })
}

export const useIgnoreTransaction = () =>
  useInvalidating(async ({ sepayId }) => unwrap(await adminPayments.ignoreTransaction(sepayId)))
export const useLinkTransaction = () =>
  useInvalidating(async ({ sepayId, orderCode }) => unwrap(await adminPayments.linkTransaction(sepayId, orderCode)))
export const useResolveOrder = () =>
  useInvalidating(async ({ id, grantPremium }) => unwrap(await adminPayments.resolveOrder(id, grantPremium)))
export const useMarkOrderPaid = () =>
  useInvalidating(async ({ id, amount }) => unwrap(await adminPayments.markOrderPaid(id, amount)))
export const useCheckOrderOnSepay = () =>
  useInvalidating(async ({ id }) => unwrap(await adminPayments.checkOrderOnSepay(id)))
export const useCancelOrder = () =>
  useInvalidating(async ({ id }) => unwrap(await adminPayments.cancelOrder(id)))
export const useCreatePlan = () =>
  useInvalidating(async (body) => unwrap(await adminPayments.createPlan(body)))
export const useUpdatePlan = () =>
  useInvalidating(async ({ id, body }) => unwrap(await adminPayments.updatePlan(id, body)))
export const useSetPlanActive = () =>
  useInvalidating(async ({ id, active }) => unwrap(await adminPayments.setPlanActive(id, active)))
