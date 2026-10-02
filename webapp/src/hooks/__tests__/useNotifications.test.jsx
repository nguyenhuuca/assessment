import React from 'react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useMarkRead, useNotificationStream, useUnreadCount, backoffDelay } from '../useNotifications.js'
import { openStream } from '../../api/sseStream.js'
import { notificationsApi } from '../../api/notifications.js'

vi.mock('../../api/sseStream.js', () => ({ openStream: vi.fn() }))
vi.mock('../../api/notifications.js', () => ({
  NOTIFICATIONS_STREAM_URL: () => 'http://api/notifications/stream',
  notificationsApi: {
    list: vi.fn(), unreadCount: vi.fn(), markRead: vi.fn(), markAllRead: vi.fn(),
  },
}))

let auth
vi.mock('../useAuth.js', () => ({ useAuth: () => auth }))

function makeWrapper() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>
  return { client, wrapper }
}

beforeEach(() => {
  vi.clearAllMocks()
  auth = { jwt: 'jwt-1', isLoggedIn: true }
})

afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('backoffDelay', () => {
  it('grows exponentially from 1s to a 30s cap, plus 1-5s jitter', () => {
    vi.spyOn(Math, 'random').mockReturnValue(0)
    expect(backoffDelay(1)).toBe(2000)
    expect(backoffDelay(2)).toBe(3000)
    expect(backoffDelay(3)).toBe(5000)
    expect(backoffDelay(10)).toBe(31000)
    Math.random.mockReturnValue(0.999)
    expect(backoffDelay(10)).toBeLessThan(35000)
  })
})

describe('useNotificationStream', () => {
  it('sends the JWT as a header (never in the URL) and applies unread events to the cache', async () => {
    let emit
    openStream.mockImplementation(({ onEvent, signal }) => {
      emit = onEvent
      return new Promise((_, reject) => signal.addEventListener('abort', () => reject(new Error('aborted'))))
    })
    const { client, wrapper } = makeWrapper()
    const invalidate = vi.spyOn(client, 'invalidateQueries')
    renderHook(() => useNotificationStream(), { wrapper })

    await waitFor(() => expect(openStream).toHaveBeenCalledTimes(1))
    const args = openStream.mock.calls[0][0]
    expect(args.headers).toEqual({ Authorization: 'jwt-1' })
    expect(args.url).not.toContain('jwt-1')

    act(() => emit({ event: 'unread', data: '{"unread": 3}' }))
    expect(client.getQueryData(['notifications', 'unread'])).toEqual({ count: 3 })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['notifications', 'list'] })

    act(() => emit({ event: 'unread', data: 'not json' }))
    expect(client.getQueryData(['notifications', 'unread'])).toEqual({ count: 3 })
  })

  it('reconnects with backoff after the stream ends or fails', async () => {
    vi.useFakeTimers()
    vi.spyOn(Math, 'random').mockReturnValue(0)
    openStream
      .mockRejectedValueOnce({ status: 503 })
      .mockResolvedValueOnce(undefined)
      .mockImplementation(({ signal }) => new Promise((_, reject) => signal.addEventListener('abort', () => reject(new Error('aborted')))))
    const { wrapper } = makeWrapper()
    renderHook(() => useNotificationStream(), { wrapper })

    await act(async () => { await vi.advanceTimersByTimeAsync(0) })
    expect(openStream).toHaveBeenCalledTimes(1)

    await act(async () => { await vi.advanceTimersByTimeAsync(1999) })
    expect(openStream).toHaveBeenCalledTimes(1)
    await act(async () => { await vi.advanceTimersByTimeAsync(2) })
    expect(openStream).toHaveBeenCalledTimes(2)

    // second attempt ended cleanly -> next delay is 3s
    await act(async () => { await vi.advanceTimersByTimeAsync(2990) })
    expect(openStream).toHaveBeenCalledTimes(2)
    await act(async () => { await vi.advanceTimersByTimeAsync(20) })
    expect(openStream).toHaveBeenCalledTimes(3)
  })

  it('stops for good on 401', async () => {
    vi.useFakeTimers()
    openStream.mockRejectedValue({ status: 401 })
    const { wrapper } = makeWrapper()
    renderHook(() => useNotificationStream(), { wrapper })

    await act(async () => { await vi.advanceTimersByTimeAsync(120000) })
    expect(openStream).toHaveBeenCalledTimes(1)
  })

  it('aborts the connection on unmount and does not reconnect', async () => {
    vi.useFakeTimers()
    let signal
    openStream.mockImplementation(({ signal: s }) => {
      signal = s
      return new Promise((_, reject) => s.addEventListener('abort', () => reject(new Error('aborted'))))
    })
    const { wrapper } = makeWrapper()
    const { unmount } = renderHook(() => useNotificationStream(), { wrapper })
    await act(async () => { await vi.advanceTimersByTimeAsync(0) })
    expect(signal.aborted).toBe(false)

    unmount()
    expect(signal.aborted).toBe(true)
    await act(async () => { await vi.advanceTimersByTimeAsync(120000) })
    expect(openStream).toHaveBeenCalledTimes(1)
  })

  it('does not connect when logged out', async () => {
    auth = { jwt: null, isLoggedIn: false }
    const { wrapper } = makeWrapper()
    renderHook(() => useNotificationStream(), { wrapper })
    await Promise.resolve()
    expect(openStream).not.toHaveBeenCalled()
  })
})

describe('useMarkRead', () => {
  it('decrements the badge optimistically and rolls back on error', async () => {
    let rejectMark
    notificationsApi.markRead.mockImplementation(() => new Promise((_, rej) => { rejectMark = rej }))
    const { client, wrapper } = makeWrapper()
    client.setQueryData(['notifications', 'unread'], { count: 2 })
    const { result } = renderHook(() => useMarkRead(), { wrapper })

    act(() => result.current.mutate({ id: 'n1', wasUnread: true }))
    await waitFor(() => expect(client.getQueryData(['notifications', 'unread'])).toEqual({ count: 1 }))

    await act(async () => { rejectMark({ status: 500 }) })
    await waitFor(() => expect(client.getQueryData(['notifications', 'unread'])).toEqual({ count: 2 }))
  })

  it('does not decrement for an already-read item', async () => {
    notificationsApi.markRead.mockResolvedValue(null)
    notificationsApi.unreadCount.mockResolvedValue({ data: { count: 2 } })
    const { client, wrapper } = makeWrapper()
    client.setQueryData(['notifications', 'unread'], { count: 2 })
    const { result } = renderHook(() => useMarkRead(), { wrapper })
    await act(async () => { await result.current.mutateAsync({ id: 'n1', wasUnread: false }) })
    expect(client.getQueryData(['notifications', 'unread'])).toEqual({ count: 2 })
  })
})

describe('useUnreadCount', () => {
  it('unwraps the {data:{count}} envelope', async () => {
    notificationsApi.unreadCount.mockResolvedValue({ status: 'OK', data: { count: 4 } })
    const { wrapper } = makeWrapper()
    const { result } = renderHook(() => useUnreadCount(), { wrapper })
    await waitFor(() => expect(result.current.data).toEqual({ count: 4 }))
  })
})
