import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { notificationsApi, NOTIFICATIONS_STREAM_URL } from '../api/notifications.js'
import { openStream } from '../api/sseStream.js'
import { useAuth } from './useAuth.js'

export const UNREAD_KEY = ['notifications', 'unread']
export const LIST_KEY = ['notifications', 'list']
const STREAM_UP_KEY = ['notifications', 'streamUp']

const POLL_STREAM_DOWN = 30_000
const POLL_STREAM_UP = 120_000
const BACKOFF_BASE = 1_000
const BACKOFF_MAX = 30_000

/** Reconnect delay: exponential 1s -> 30s plus 1-5s jitter. */
export function backoffDelay(attempt) {
  const exp = Math.min(BACKOFF_BASE * 2 ** Math.max(attempt - 1, 0), BACKOFF_MAX)
  return exp + 1000 + Math.random() * 4000
}

export function useUnreadCount() {
  const queryClient = useQueryClient()
  return useQuery({
    queryKey: UNREAD_KEY,
    queryFn: async () => {
      const res = await notificationsApi.unreadCount()
      return res?.data ?? res
    },
    refetchOnWindowFocus: true,
    refetchInterval: () =>
      queryClient.getQueryData(STREAM_UP_KEY) ? POLL_STREAM_UP : POLL_STREAM_DOWN,
  })
}

export function useNotificationList(page = 0, { enabled = true } = {}) {
  return useQuery({
    queryKey: [...LIST_KEY, page],
    queryFn: async () => {
      const res = await notificationsApi.list(page)
      return res?.data ?? res
    },
    enabled,
  })
}

/** mutate({ id, wasUnread }) — decrements the badge optimistically when the item was unread. */
export function useMarkRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id }) => notificationsApi.markRead(id),
    onMutate: async ({ wasUnread = true }) => {
      await queryClient.cancelQueries({ queryKey: UNREAD_KEY })
      const previous = queryClient.getQueryData(UNREAD_KEY)
      if (wasUnread && previous) {
        queryClient.setQueryData(UNREAD_KEY, { ...previous, count: Math.max((previous.count ?? 0) - 1, 0) })
      }
      return { previous }
    },
    onError: (_err, _vars, ctx) => {
      if (ctx?.previous) queryClient.setQueryData(UNREAD_KEY, ctx.previous)
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: UNREAD_KEY })
      queryClient.invalidateQueries({ queryKey: LIST_KEY })
    },
  })
}

export function useMarkAllRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => notificationsApi.markAllRead(),
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: UNREAD_KEY })
      const previous = queryClient.getQueryData(UNREAD_KEY)
      queryClient.setQueryData(UNREAD_KEY, { ...(previous || {}), count: 0 })
      return { previous }
    },
    onError: (_err, _vars, ctx) => {
      if (ctx?.previous) queryClient.setQueryData(UNREAD_KEY, ctx.previous)
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: UNREAD_KEY })
      queryClient.invalidateQueries({ queryKey: LIST_KEY })
    },
  })
}

function sleep(ms, signal) {
  return new Promise(resolve => {
    if (signal.aborted) return resolve()
    const onAbort = () => { clearTimeout(timer); resolve() }
    const timer = setTimeout(() => { signal.removeEventListener('abort', onAbort); resolve() }, ms)
    signal.addEventListener('abort', onAbort, { once: true })
  })
}

/**
 * Keeps one SSE connection open while logged in. Mount once (AppShell).
 * Reconnects with backoff, stops for good on 401, aborts on logout/unmount.
 */
export function useNotificationStream() {
  const { jwt, isLoggedIn } = useAuth()
  const queryClient = useQueryClient()

  useEffect(() => {
    if (!isLoggedIn || !jwt) return undefined
    const ac = new AbortController()
    const setUp = (up) => queryClient.setQueryData(STREAM_UP_KEY, up)

    async function run() {
      let attempt = 0
      while (!ac.signal.aborted) {
        try {
          await openStream({
            url: NOTIFICATIONS_STREAM_URL(),
            headers: { Authorization: jwt },
            signal: ac.signal,
            onEvent: ({ event, data }) => {
              attempt = 0
              setUp(true)
              if (event !== 'unread') return
              try {
                const n = Number(JSON.parse(data)?.unread)
                if (Number.isFinite(n)) queryClient.setQueryData(UNREAD_KEY, { count: n })
              } catch { return }
              queryClient.invalidateQueries({ queryKey: LIST_KEY })
            },
          })
        } catch (err) {
          if (ac.signal.aborted) return
          if (err?.status === 401) { setUp(false); return }
        }
        setUp(false)
        if (ac.signal.aborted) return
        attempt += 1
        await sleep(backoffDelay(attempt), ac.signal)
      }
    }

    run()
    return () => { ac.abort(); setUp(false) }
  }, [isLoggedIn, jwt, queryClient])
}
