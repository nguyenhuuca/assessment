import React from 'react'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render as rtlRender, fireEvent, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import VideoPlayer from '../VideoPlayer.jsx'
import { viewsApi } from '../../../api/views.js'
import { watchHistoryApi } from '../../../api/watchHistory.js'

let auth
vi.mock('../../../hooks/useAuth.js', () => ({ useAuth: () => auth }))
vi.mock('../../../api/views.js', () => ({ viewsApi: { record: vi.fn() } }))
vi.mock('../../../api/watchHistory.js', () => ({
  watchHistoryApi: { progress: vi.fn(), summary: vi.fn() },
}))

let client
function render(ui) {
  const wrap = (node) => <QueryClientProvider client={client}>{node}</QueryClientProvider>
  const res = rtlRender(wrap(ui))
  return { ...res, rerender: (next) => res.rerender(wrap(next)) }
}

function tick(container, seconds, duration = 60) {
  const v = container.querySelector('video')
  Object.defineProperty(v, 'duration', { value: duration, configurable: true })
  Object.defineProperty(v, 'currentTime', { value: seconds, configurable: true, writable: true })
  fireEvent.timeUpdate(v)
  return v
}

/** Plays from `from` to `to` in 0.25 s steps, like a real <video>. */
function playTo(container, to, from = 0) {
  for (let t = from + 0.25; t <= to + 1e-9; t += 0.25) tick(container, Math.round(t * 100) / 100)
}

const flush = () => act(async () => { await vi.advanceTimersByTimeAsync(0) })

const video = { id: 12, src: 'https://x/stream/a', title: 't' }

beforeEach(() => {
  vi.useFakeTimers()
  vi.clearAllMocks()
  auth = { isLoggedIn: true }
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  viewsApi.record.mockResolvedValue(null)
  watchHistoryApi.progress.mockResolvedValue(null)
  watchHistoryApi.summary.mockResolvedValue({ data: [] })
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockImplementation(() => Promise.resolve())
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
  Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
})

afterEach(() => {
  vi.useRealTimers()
})

describe('VideoPlayer progress reporting', () => {
  it('sends nothing before the view was sent', () => {
    const { container } = render(<VideoPlayer video={video} active />)
    const v = tick(container, 2)
    fireEvent.pause(v)
    expect(viewsApi.record).not.toHaveBeenCalled()
    expect(watchHistoryApi.progress).not.toHaveBeenCalled()
  })

  it('pause after the view sends the position and duration', async () => {
    const { container } = render(<VideoPlayer video={video} active />)
    playTo(container, 4)
    const v = tick(container, 4.25)
    fireEvent.pause(v)
    expect(watchHistoryApi.progress).toHaveBeenCalledTimes(1)
    expect(watchHistoryApi.progress).toHaveBeenCalledWith(12, 4, 60)
    await flush()
  })

  it('sends at most one call per 10 s while playing, but pause is always sent', async () => {
    const { container } = render(<VideoPlayer video={video} active />)
    playTo(container, 4)
    const v = tick(container, 5)
    fireEvent.playing(v)
    fireEvent.pause(v)                       // forced: 1st
    fireEvent.pause(v)                       // forced again: pause is never throttled
    expect(watchHistoryApi.progress).toHaveBeenCalledTimes(2)
    watchHistoryApi.progress.mockClear()

    // hidden-tab report is throttled to one per 10 s
    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true })
    await act(async () => { await vi.advanceTimersByTimeAsync(11_000) })
    document.dispatchEvent(new Event('visibilitychange'))
    expect(watchHistoryApi.progress).toHaveBeenCalledTimes(1)
    await act(async () => { await vi.advanceTimersByTimeAsync(3_000) })
    document.dispatchEvent(new Event('visibilitychange'))
    expect(watchHistoryApi.progress).toHaveBeenCalledTimes(1)
    await act(async () => { await vi.advanceTimersByTimeAsync(8_000) })
    document.dispatchEvent(new Event('visibilitychange'))
    expect(watchHistoryApi.progress).toHaveBeenCalledTimes(2)
  })

  it('reports every 30 s while playing, not while paused', async () => {
    const { container } = render(<VideoPlayer video={video} active />)
    playTo(container, 4)
    const v = tick(container, 6)
    fireEvent.playing(v)
    await act(async () => { await vi.advanceTimersByTimeAsync(30_000) })
    expect(watchHistoryApi.progress).toHaveBeenCalledTimes(1)
    expect(watchHistoryApi.progress).toHaveBeenLastCalledWith(12, 6, 60)
    fireEvent.pause(v)
    watchHistoryApi.progress.mockClear()
    await act(async () => { await vi.advanceTimersByTimeAsync(60_000) })
    expect(watchHistoryApi.progress).not.toHaveBeenCalled()
  })

  it('unmounting flushes the last position', () => {
    const { container, unmount } = render(<VideoPlayer video={video} active />)
    playTo(container, 4)
    tick(container, 4.25)
    unmount()
    expect(watchHistoryApi.progress).toHaveBeenCalledTimes(1)
    expect(watchHistoryApi.progress).toHaveBeenCalledWith(12, 4, 60)
  })

  it('switching to another video flushes the previous one', () => {
    const { container, rerender } = render(<VideoPlayer video={video} active />)
    playTo(container, 4)
    rerender(<VideoPlayer video={{ ...video, id: 13 }} active />)
    expect(watchHistoryApi.progress).toHaveBeenCalledWith(12, 4, 60)
  })

  it('active turning false reports (throttled) the position', () => {
    const { container, rerender } = render(<VideoPlayer video={video} active />)
    playTo(container, 4)
    rerender(<VideoPlayer video={video} active={false} />)
    expect(watchHistoryApi.progress).toHaveBeenCalledWith(12, 4, 60)
  })

  it('a failing progress call never throws into playback', async () => {
    watchHistoryApi.progress.mockRejectedValue(new Error('offline'))
    const { container } = render(<VideoPlayer video={video} active />)
    playTo(container, 4)
    const v = tick(container, 5)
    expect(() => fireEvent.pause(v)).not.toThrow()
    await flush()
  })

  it('guests send no progress and do not invalidate history', async () => {
    auth = { isLoggedIn: false }
    const spy = vi.spyOn(client, 'invalidateQueries')
    const { container, unmount } = render(<VideoPlayer video={video} active />)
    playTo(container, 4)
    const v = tick(container, 5)
    fireEvent.pause(v)
    await flush()
    unmount()
    expect(viewsApi.record).toHaveBeenCalledTimes(1)
    expect(watchHistoryApi.progress).not.toHaveBeenCalled()
    expect(spy).not.toHaveBeenCalled()
    expect(watchHistoryApi.summary).not.toHaveBeenCalled()
  })

  it('invalidates the watch history queries once the view call succeeded', async () => {
    const spy = vi.spyOn(client, 'invalidateQueries')
    const { container } = render(<VideoPlayer video={video} active />)
    playTo(container, 3.25)
    await flush()
    expect(spy).toHaveBeenCalledWith({ queryKey: ['watchHistory'] })
  })
})

describe('VideoPlayer progress bar', () => {
  it('renders the fill with the themed class and a width that follows playback', () => {
    const { container } = render(<VideoPlayer video={video} active />)
    const fill = container.querySelector('.player-progress-fill')
    expect(fill).not.toBeNull()
    expect(fill.parentElement.classList.contains('player-progress-track')).toBe(true)
    expect(fill.style.width).toBe('0%')
    tick(container, 30)
    expect(fill.style.width).toBe('50%')
  })
})

describe('VideoPlayer startAt', () => {
  function loaded(container, duration) {
    const v = container.querySelector('video')
    Object.defineProperty(v, 'duration', { value: duration, configurable: true })
    Object.defineProperty(v, 'currentTime', { value: 0, configurable: true, writable: true })
    fireEvent.loadedMetadata(v)
    return v
  }

  it('seeks once to startAt on loadedmetadata', () => {
    const { container } = render(<VideoPlayer video={video} active startAt={20} />)
    const v = loaded(container, 60)
    expect(v.currentTime).toBe(20)
    v.currentTime = 1
    fireEvent.loadedMetadata(v)
    expect(v.currentTime).toBe(1)
  })

  it.each([[0], [5], [3], [50], [55]])('does not seek for startAt=%s on a 60 s video', (startAt) => {
    const { container } = render(<VideoPlayer video={video} active startAt={startAt} />)
    expect(loaded(container, 60).currentTime).toBe(0)
  })

  it('does nothing without startAt', () => {
    const { container } = render(<VideoPlayer video={video} active />)
    expect(loaded(container, 60).currentTime).toBe(0)
  })

  it('the seek does not count as watched time', () => {
    const { container } = render(<VideoPlayer video={video} active startAt={30} />)
    loaded(container, 60)
    tick(container, 30)
    tick(container, 30.25)
    expect(viewsApi.record).not.toHaveBeenCalled()
  })
})
