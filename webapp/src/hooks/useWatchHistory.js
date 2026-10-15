import { useMemo } from 'react'
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { watchHistoryApi } from '../api/watchHistory.js'
import { useAuth } from './useAuth.js'

export const WATCH_HISTORY_PAGE_SIZE = 20
export const WATCH_HISTORY_KEY = ['watchHistory']

const unwrap = (res) => res?.data ?? res

function normalizePage(res) {
  const data = unwrap(res) || {}
  return {
    content: Array.isArray(data.content) ? data.content : [],
    totalPages: data.totalPages ?? 0,
    totalElements: data.totalElements ?? 0,
  }
}

/** Watch history pages for a title search (q = '' lists everything). "Xem thêm" appends the next page. */
export function useWatchHistoryInfinite(q = '', { size = WATCH_HISTORY_PAGE_SIZE } = {}) {
  const { isLoggedIn } = useAuth()
  return useInfiniteQuery({
    queryKey: ['watchHistory', 'list', q],
    enabled: !!isLoggedIn,
    initialPageParam: 0,
    queryFn: async ({ pageParam }) => ({ ...normalizePage(await watchHistoryApi.list(q, pageParam, size)), page: pageParam }),
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  })
}

/**
 * Map<videoId, {positionSec, durationSec, completed}> for badges. One request shared by every consumer
 * (same query key), only for logged-in users. Empty map for guests / while loading.
 */
export function useWatchHistorySummary() {
  const { isLoggedIn } = useAuth()
  const query = useQuery({
    queryKey: ['watchHistory', 'summary'],
    enabled: !!isLoggedIn,
    staleTime: 60_000,
    queryFn: async () => {
      const list = unwrap(await watchHistoryApi.summary())
      return Array.isArray(list) ? list : []
    },
  })
  return useMemo(
    () => new Map((query.data ?? []).map(s => [String(s.videoId), s])),
    [query.data],
  )
}

export function useRemoveWatchEntry() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (videoId) => watchHistoryApi.remove(videoId),
    onSuccess: () => qc.invalidateQueries({ queryKey: WATCH_HISTORY_KEY }),
  })
}

export function useClearWatchHistory() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => watchHistoryApi.clear(),
    onSuccess: () => qc.invalidateQueries({ queryKey: WATCH_HISTORY_KEY }),
  })
}
