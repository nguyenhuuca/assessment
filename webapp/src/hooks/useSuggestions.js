import { keepPreviousData, useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { suggestionsApi } from '../api/suggestions.js'

export const SUGGESTION_PAGE_SIZE = 20
export const RECENT_SUGGESTIONS = 5

const unwrap = (res) => res?.data ?? res

export function normalizePage(res) {
  const data = unwrap(res) || {}
  return {
    content: Array.isArray(data.content) ? data.content : [],
    totalPages: data.totalPages ?? 0,
    totalElements: data.totalElements ?? 0,
  }
}

/** One page of the caller's suggestions (status '' = all; PENDING | APPROVED | REJECTED | LIVE). */
export function useMySuggestions(status = '', page = 0, { size = SUGGESTION_PAGE_SIZE, enabled = true } = {}) {
  return useQuery({
    queryKey: ['suggestions', 'mine', status, page, size],
    enabled,
    placeholderData: keepPreviousData,
    queryFn: async () => normalizePage(await suggestionsApi.mine(status, page, size)),
  })
}

/** All pages loaded so far for a status filter ("Xem thêm" appends the next page). */
export function useMySuggestionsInfinite(status = '', { size = SUGGESTION_PAGE_SIZE, enabled = true } = {}) {
  return useInfiniteQuery({
    queryKey: ['suggestions', 'mine-infinite', status, size],
    enabled,
    initialPageParam: 0,
    queryFn: async ({ pageParam }) => ({ ...normalizePage(await suggestionsApi.mine(status, pageParam, size)), page: pageParam }),
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  })
}

/** Submit a suggestion; refreshes the user's lists on success. */
export function useSubmitSuggestion() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (vars) => suggestionsApi.submit(vars),
    onSuccess: () => Promise.all([
      qc.invalidateQueries({ queryKey: ['suggestions', 'mine'] }),
      qc.invalidateQueries({ queryKey: ['suggestions', 'mine-infinite'] }),
    ]),
  })
}


/** Ask admins to review a rejected suggestion again (optional message, max 300). */
export function useRequestRereview() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, message }) => suggestionsApi.requestRereview(id, message),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['suggestions'] }),
  })
}
