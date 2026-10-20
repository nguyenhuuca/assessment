import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import * as reportsApi from '../api/reports.js'

export function useReports(page = 0) {
  return useQuery({
    queryKey: ['admin', 'reports', page],
    queryFn: () => reportsApi.listReports({ page, size: 20 }),
    placeholderData: keepPreviousData,
  })
}

export function useCreateReport() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body) => reportsApi.createReport(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'reports'] }),
  })
}

export function useRetryReport() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id) => reportsApi.retryReport(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'reports'] }),
  })
}
