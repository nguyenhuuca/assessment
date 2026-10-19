import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { suggestionsApi } from '../api/suggestions.js'

export function useAdminSuggestions(status = null, page = 0) {
  return useQuery({
    queryKey: ['admin', 'suggestions', 'list', status, page],
    queryFn: () => suggestionsApi.adminList(status ?? '', page, 20),
  })
}

export function useSuggestionPendingCount() {
  return useQuery({
    queryKey: ['admin', 'suggestions', 'count'],
    queryFn: () => suggestionsApi.pendingCount(),
    refetchInterval: 60_000,
  })
}

function useSuggestionMutation(fn) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'suggestions'] })
      // approving creates an import job: make it appear in the Import tab
      qc.invalidateQueries({ queryKey: ['admin', 'imports'] })
    },
  })
}

export const useApproveSuggestion = () =>
  useSuggestionMutation(({ id, title }) => suggestionsApi.approve(id, title))
export const useRejectSuggestion = () =>
  useSuggestionMutation(({ id, reason, note }) => suggestionsApi.reject(id, reason, note))
export const useReopenSuggestion = () =>
  useSuggestionMutation(({ id }) => suggestionsApi.reopen(id))
