import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { settingsApi } from '../api/settings.js'

export function useSettings({ enabled = true } = {}) {
  return useQuery({
    queryKey: ['settings'],
    enabled,
    queryFn: async () => {
      const res = await settingsApi.get()
      return res.data || res
    },
  })
}

export function useUpdateSettings() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: settingsApi.patch,
    onSuccess: (res) => {
      const updated = res.data || res
      queryClient.setQueryData(['settings'], updated)
    },
  })
}

export function useDeleteAccount() {
  return useMutation({
    mutationFn: settingsApi.deleteAccount,
  })
}
