import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  useWatchHistoryInfinite, useWatchHistorySummary, useRemoveWatchEntry, useClearWatchHistory,
} from '../useWatchHistory.js'
import { watchHistoryApi } from '../../api/watchHistory.js'

let auth
vi.mock('../useAuth.js', () => ({ useAuth: () => auth }))
vi.mock('../../api/watchHistory.js', () => ({
  watchHistoryApi: { list: vi.fn(), summary: vi.fn(), remove: vi.fn(), clear: vi.fn(), progress: vi.fn() },
}))

const item = (videoId) => ({ videoId, title: `V${videoId}`, watchedAt: '2026-10-15T01:00:00Z', positionSec: 5, durationSec: 60, completed: false })
const page = (content, number, totalPages) => ({ data: { content, number, totalPages, totalElements: 3 } })

let client
const wrapper = ({ children }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>

beforeEach(() => {
  vi.clearAllMocks()
  auth = { isLoggedIn: true }
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
})

describe('useWatchHistoryInfinite', () => {
  it('pages through the list', async () => {
    watchHistoryApi.list
      .mockResolvedValueOnce(page([item(1), item(2)], 0, 2))
      .mockResolvedValueOnce(page([item(3)], 1, 2))
    const { result } = renderHook(() => useWatchHistoryInfinite(''), { wrapper })
    await waitFor(() => expect(result.current.data?.pages).toHaveLength(1))
    expect(watchHistoryApi.list).toHaveBeenCalledWith('', 0, 20)
    expect(result.current.hasNextPage).toBe(true)

    await act(async () => { await result.current.fetchNextPage() })
    await waitFor(() => expect(result.current.data.pages).toHaveLength(2))
    expect(watchHistoryApi.list).toHaveBeenLastCalledWith('', 1, 20)
    expect(result.current.data.pages.flatMap(p => p.content).map(i => i.videoId)).toEqual([1, 2, 3])
    expect(result.current.hasNextPage).toBe(false)
  })

  it('puts q in the query key and refetches per term', async () => {
    watchHistoryApi.list.mockResolvedValue(page([], 0, 0))
    const { result, rerender } = renderHook(({ q }) => useWatchHistoryInfinite(q), { wrapper, initialProps: { q: '' } })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    rerender({ q: 'cat' })
    await waitFor(() => expect(watchHistoryApi.list).toHaveBeenCalledWith('cat', 0, 20))
    const keys = client.getQueryCache().getAll().map(q => q.queryKey)
    expect(keys).toContainEqual(['watchHistory', 'list', ''])
    expect(keys).toContainEqual(['watchHistory', 'list', 'cat'])
  })

  it('makes no call for guests', () => {
    auth = { isLoggedIn: false }
    renderHook(() => useWatchHistoryInfinite(''), { wrapper })
    expect(watchHistoryApi.list).not.toHaveBeenCalled()
  })
})

describe('useWatchHistorySummary', () => {
  it('returns a Map keyed by video id from a single request shared by all consumers', async () => {
    watchHistoryApi.summary.mockResolvedValue({
      data: [
        { videoId: 7, positionSec: 12, durationSec: 60, completed: false },
        { videoId: 8, positionSec: 59, durationSec: 60, completed: true },
      ],
    })
    const { result } = renderHook(() => ({ a: useWatchHistorySummary(), b: useWatchHistorySummary() }), { wrapper })
    await waitFor(() => expect(result.current.a.size).toBe(2))
    expect(result.current.a.get('7')).toMatchObject({ positionSec: 12, completed: false })
    expect(result.current.b.get('8').completed).toBe(true)
    expect(result.current.a.get('99')).toBeUndefined()
    expect(watchHistoryApi.summary).toHaveBeenCalledTimes(1)
  })

  it('is an empty Map and makes no call for guests', () => {
    auth = { isLoggedIn: false }
    const { result } = renderHook(() => useWatchHistorySummary(), { wrapper })
    expect(result.current.size).toBe(0)
    expect(watchHistoryApi.summary).not.toHaveBeenCalled()
  })
})

describe('mutations', () => {
  it('remove calls the API and invalidates every watchHistory query', async () => {
    watchHistoryApi.remove.mockResolvedValue(null)
    const spy = vi.spyOn(client, 'invalidateQueries')
    const { result } = renderHook(() => useRemoveWatchEntry(), { wrapper })
    await act(async () => { await result.current.mutateAsync(5) })
    expect(watchHistoryApi.remove).toHaveBeenCalledWith(5)
    expect(spy).toHaveBeenCalledWith({ queryKey: ['watchHistory'] })
  })

  it('clear calls the API and invalidates every watchHistory query', async () => {
    watchHistoryApi.clear.mockResolvedValue(null)
    const spy = vi.spyOn(client, 'invalidateQueries')
    const { result } = renderHook(() => useClearWatchHistory(), { wrapper })
    await act(async () => { await result.current.mutateAsync() })
    expect(watchHistoryApi.clear).toHaveBeenCalledTimes(1)
    expect(spy).toHaveBeenCalledWith({ queryKey: ['watchHistory'] })
  })
})
