import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  useMyCollections,
  usePublicCollections,
  useCollectionVideos,
  useCollectionMembership,
  useCollectionVideoIds,
  useCollectionPlaylist,
  useCreateCollection,
  useDeleteCollection,
  useToggleCollectionItem,
} from '../useCollections.js'
import { collectionsApi } from '../../api/collections.js'

vi.mock('../../api/collections.js', () => ({
  collectionsApi: {
    mine: vi.fn(), publicList: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn(),
    videos: vi.fn(), videoIds: vi.fn(), addVideo: vi.fn(), removeVideo: vi.fn(), membership: vi.fn(),
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
    expect(keys).not.toContain(JSON.stringify(['collections', 'public'])) // private by default
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

  it('loads every video id of a collection', async () => {
    collectionsApi.videoIds.mockResolvedValue({ data: [4, 5] })
    const { result } = renderHook(() => useCollectionVideoIds('c1'), { wrapper })
    await waitFor(() => expect(result.current.data).toEqual([4, 5]))
    expect(collectionsApi.videoIds).toHaveBeenCalledWith('c1')
  })

  it('toggling in a private collection does not refetch the public list', async () => {
    collectionsApi.addVideo.mockResolvedValue(null)
    const spy = vi.spyOn(client, 'invalidateQueries')
    const { result } = renderHook(() => useToggleCollectionItem(), { wrapper })
    await act(async () => { await result.current.mutateAsync({ id: 'c', videoId: 3, inCollection: false, visibility: 'PRIVATE' }) })
    const keys = spy.mock.calls.map(c => JSON.stringify(c[0].queryKey))
    expect(keys).toContain(JSON.stringify(['collections', 'mine']))
    expect(keys).not.toContain(JSON.stringify(['collections', 'public']))
  })

  it('toggling in a public (or unknown) collection refetches the public list', async () => {
    collectionsApi.addVideo.mockResolvedValue(null)
    const spy = vi.spyOn(client, 'invalidateQueries')
    const { result } = renderHook(() => useToggleCollectionItem(), { wrapper })
    await act(async () => { await result.current.mutateAsync({ id: 'c', videoId: 3, inCollection: false, visibility: 'PUBLIC' }) })
    await act(async () => { await result.current.mutateAsync({ id: 'c', videoId: 4, inCollection: false }) })
    const publicCalls = spy.mock.calls.filter(c => JSON.stringify(c[0].queryKey) === JSON.stringify(['collections', 'public']))
    expect(publicCalls).toHaveLength(2)
  })

  it('creating a private collection skips the public list', async () => {
    collectionsApi.create.mockResolvedValue({ data: { id: 'new' } })
    const spy = vi.spyOn(client, 'invalidateQueries')
    const { result } = renderHook(() => useCreateCollection(), { wrapper })
    await act(async () => { await result.current.mutateAsync({ name: 'Riêng', visibility: 'PRIVATE' }) })
    const keys = spy.mock.calls.map(c => JSON.stringify(c[0].queryKey))
    expect(keys).not.toContain(JSON.stringify(['collections', 'public']))
  })

  it('deleting drops the cached pages of that collection', async () => {
    collectionsApi.remove.mockResolvedValue(null)
    client.setQueryData(['collections', 'x', 'videos', 0], { videos: [] })
    client.setQueryData(['collections', 'y', 'videos', 0], { videos: [] })
    const { result } = renderHook(() => useDeleteCollection(), { wrapper })
    await act(async () => { await result.current.mutateAsync('x') })
    expect(client.getQueryData(['collections', 'x', 'videos', 0])).toBeUndefined()
    expect(client.getQueryData(['collections', 'y', 'videos', 0])).toBeTruthy()
  })

  it('playlist loads every page of a collection', async () => {
    collectionsApi.videos.mockImplementation((_id, page) => Promise.resolve({
      data: { content: [{ id: page + 1, title: `V${page}` }], totalPages: 3 },
    }))
    const { result } = renderHook(() => useCollectionPlaylist('c1'), { wrapper })
    await waitFor(() => expect(result.current.data).toHaveLength(3))
    expect(result.current.data.map(v => v.id)).toEqual([1, 2, 3])
    expect(collectionsApi.videos).toHaveBeenCalledTimes(3)
    expect(collectionsApi.videos).toHaveBeenLastCalledWith('c1', 2, 50)
  })
})
