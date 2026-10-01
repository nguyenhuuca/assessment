import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useVideoReaction } from '../useVideoReaction.js'
import { reactionsApi } from '../../api/reactions.js'

vi.mock('../useAuth.js', () => ({ useAuth: () => ({ isLoggedIn: true }) }))
vi.mock('../../api/reactions.js', () => ({
  reactionsApi: { get: vi.fn(), set: vi.fn(), remove: vi.fn() },
}))

const summary = (likeCount, dislikeCount, myReaction) => ({
  data: { videoId: 1, likeCount, dislikeCount, myReaction },
})

function wrapper({ children }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

describe('useVideoReaction', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    reactionsApi.get.mockResolvedValue(summary(10, 2, null))
  })

  it('loads the summary', async () => {
    const { result } = renderHook(() => useVideoReaction(1), { wrapper })
    await waitFor(() => expect(result.current.likeCount).toBe(10))
    expect(result.current.dislikeCount).toBe(2)
    expect(result.current.myReaction).toBeNull()
  })

  it('does not fetch without a video id', () => {
    renderHook(() => useVideoReaction(undefined), { wrapper })
    expect(reactionsApi.get).not.toHaveBeenCalled()
  })

  it('likes and stores the server summary', async () => {
    reactionsApi.set.mockResolvedValue(summary(11, 2, 'LIKE'))
    const { result } = renderHook(() => useVideoReaction(1), { wrapper })
    await waitFor(() => expect(result.current.likeCount).toBe(10))
    act(() => result.current.react('LIKE'))
    await waitFor(() => expect(result.current.myReaction).toBe('LIKE'))
    expect(reactionsApi.set).toHaveBeenCalledWith(1, 'LIKE')
    expect(result.current.likeCount).toBe(11)
  })

  it('switches from like to dislike', async () => {
    reactionsApi.get.mockResolvedValue(summary(10, 2, 'LIKE'))
    reactionsApi.set.mockResolvedValue(summary(9, 3, 'DISLIKE'))
    const { result } = renderHook(() => useVideoReaction(1), { wrapper })
    await waitFor(() => expect(result.current.myReaction).toBe('LIKE'))
    act(() => result.current.react('DISLIKE'))
    await waitFor(() => expect(result.current.myReaction).toBe('DISLIKE'))
    expect(result.current.likeCount).toBe(9)
    expect(result.current.dislikeCount).toBe(3)
  })

  it('removes the reaction when null is passed', async () => {
    reactionsApi.get.mockResolvedValue(summary(10, 2, 'LIKE'))
    reactionsApi.remove.mockResolvedValue(summary(9, 2, null))
    const { result } = renderHook(() => useVideoReaction(1), { wrapper })
    await waitFor(() => expect(result.current.myReaction).toBe('LIKE'))
    act(() => result.current.react(null))
    await waitFor(() => expect(result.current.myReaction).toBeNull())
    expect(reactionsApi.remove).toHaveBeenCalledWith(1)
    expect(result.current.likeCount).toBe(9)
  })

  it('applies the change optimistically before the server responds', async () => {
    let resolve
    reactionsApi.set.mockReturnValue(new Promise((r) => { resolve = r }))
    const { result } = renderHook(() => useVideoReaction(1), { wrapper })
    await waitFor(() => expect(result.current.likeCount).toBe(10))
    act(() => result.current.react('LIKE'))
    await waitFor(() => expect(result.current.likeCount).toBe(11))
    expect(result.current.isPending).toBe(true)
    await act(async () => resolve(summary(11, 2, 'LIKE')))
    await waitFor(() => expect(result.current.isPending).toBe(false))
  })

  it('rolls back on error and exposes the error', async () => {
    reactionsApi.set.mockRejectedValue({ message: 'boom', status: 500 })
    const { result } = renderHook(() => useVideoReaction(1), { wrapper })
    await waitFor(() => expect(result.current.likeCount).toBe(10))
    act(() => result.current.react('LIKE'))
    await waitFor(() => expect(result.current.error).toBeTruthy())
    expect(result.current.myReaction).toBeNull()
    expect(result.current.likeCount).toBe(10)
  })

  it('uses separate state per video id', async () => {
    reactionsApi.get.mockImplementation(async (id) => (id === 1 ? summary(10, 2, 'LIKE') : summary(3, 0, null)))
    const { result, rerender } = renderHook(({ id }) => useVideoReaction(id), { wrapper, initialProps: { id: 1 } })
    await waitFor(() => expect(result.current.myReaction).toBe('LIKE'))
    rerender({ id: 2 })
    await waitFor(() => expect(result.current.likeCount).toBe(3))
    expect(result.current.myReaction).toBeNull()
  })
})
