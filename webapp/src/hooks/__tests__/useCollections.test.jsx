import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  useMyCollections,
  usePublicCollections,
  useCollectionVideos,
  useCollectionMembership,
  useCreateCollection,
  useToggleCollectionItem,
} from '../useCollections.js'
import { collectionsApi } from '../../api/collections.js'

vi.mock('../../api/collections.js', () => ({
  collectionsApi: {
    mine: vi.fn(), publicList: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn(),
    videos: vi.fn(), addVideo: vi.fn(), removeVideo: vi.fn(), membership: vi.fn(),
  },
}))

let client
function wrapper({ children }) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

beforeEach(() => {
  vi.clearAllMocks()
  client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
})

describe('useCollections hooks', () => {
  it('loads my and public lists', async () => {
    collectionsApi.mine.mockResolvedValue({ data: [{ id: 'a', name: 'Xem sau' }] })
    collectionsApi.publicList.mockResolvedValue({ data: [{ id: 'p', name: 'Hay' }] })
    const mine = renderHook(() => useMyCollections(), { wrapper })
    const pub = renderHook(() => usePublicCollections(), { wrapper })
    await waitFor(() => expect(mine.result.current.data).toEqual([{ id: 'a', name: 'Xem sau' }]))
    await waitFor(() => expect(pub.result.current.data).toEqual([{ id: 'p', name: 'Hay' }]))
  })

  it('does not load my list when disabled', () => {
    renderHook(() => useMyCollections(false), { wrapper })
    expect(collectionsApi.mine).not.toHaveBeenCalled()
  })

  it('maps a page of videos', async () => {
    collectionsApi.videos.mockResolvedValue({
      data: { content: [{ id: 1, title: 'T', embedLink: 'http://v', fileId: 'f1' }], totalPages: 2, totalElements: 30 },
    })
    const { result } = renderHook(() => useCollectionVideos('c1', 1), { wrapper })
    await waitFor(() => expect(result.current.data).toBeTruthy())
    expect(collectionsApi.videos).toHaveBeenCalledWith('c1', 1, expect.any(Number))
    expect(result.current.data.videos[0]).toMatchObject({ id: 1, src: 'http://v', fileId: 'f1' })
    expect(result.current.data.totalPages).toBe(2)
  })

  it('loads membership ids', async () => {
    collectionsApi.membership.mockResolvedValue({ data: ['a'] })
    const { result } = renderHook(() => useCollectionMembership(5), { wrapper })
    await waitFor(() => expect(result.current.data).toEqual(['a']))
  })

  it('creates a collection and adds the video, invalidating lists', async () => {
    collectionsApi.create.mockResolvedValue({ data: { id: 'new' } })
    collectionsApi.addVideo.mockResolvedValue(null)
    const spy = vi.spyOn(client, 'invalidateQueries')
    const { result } = renderHook(() => useCreateCollection(), { wrapper })
    await act(async () => { await result.current.mutateAsync({ name: 'Mới', videoId: 7 }) })
    expect(collectionsApi.create).toHaveBeenCalledWith({ name: 'Mới' })
    expect(collectionsApi.addVideo).toHaveBeenCalledWith('new', 7)
    const keys = spy.mock.calls.map(c => JSON.stringify(c[0].queryKey))
    expect(keys).toContain(JSON.stringify(['collections', 'mine']))
    expect(keys).toContain(JSON.stringify(['collections', 'public']))
    expect(keys).toContain(JSON.stringify(['collections', 'membership', 7]))
  })

  it('toggles an item: add then remove, invalidating membership', async () => {
    collectionsApi.addVideo.mockResolvedValue(null)
    collectionsApi.removeVideo.mockResolvedValue(null)
    const spy = vi.spyOn(client, 'invalidateQueries')
    const { result } = renderHook(() => useToggleCollectionItem(), { wrapper })
    await act(async () => { await result.current.mutateAsync({ id: 'c', videoId: 3, inCollection: false }) })
    expect(collectionsApi.addVideo).toHaveBeenCalledWith('c', 3)
    await act(async () => { await result.current.mutateAsync({ id: 'c', videoId: 3, inCollection: true }) })
    expect(collectionsApi.removeVideo).toHaveBeenCalledWith('c', 3)
    const keys = spy.mock.calls.map(c => JSON.stringify(c[0].queryKey))
    expect(keys).toContain(JSON.stringify(['collections', 'membership', 3]))
    expect(keys).toContain(JSON.stringify(['collections', 'c', 'videos']))
  })
})
