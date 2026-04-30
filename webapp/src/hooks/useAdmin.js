import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import * as adminApi from '../api/admin.js'

export function useAdminVideos(page = 0, status = null) {
  return useQuery({
    queryKey: ['admin', 'videos', page, status],
    queryFn: () => adminApi.getVideos({ page, size: 20, ...(status ? { status } : {}) }),
  })
}

export function useAdminAccounts(page = 0) {
  return useQuery({
    queryKey: ['admin', 'accounts', page],
    queryFn: () => adminApi.getAccounts({ page, size: 20 }),
  })
}

export function useAdminStats() {
  return useQuery({
    queryKey: ['admin', 'stats'],
    queryFn: () => adminApi.getStats(),
  })
}

export function useUpdateVideoStatus() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, status }) => adminApi.updateVideoStatus(id, status),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'videos'] }),
  })
}

export function useDeleteVideo() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id) => adminApi.deleteVideo(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'videos'] }),
  })
}

export function useUpdateAccountRole() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, role }) => adminApi.updateAccountRole(id, role),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'accounts'] }),
  })
}

export function useDeleteAccount() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id) => adminApi.deleteAccount(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'accounts'] }),
  })
}
