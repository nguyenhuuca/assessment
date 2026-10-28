import React from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render as rtlRender, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import VideoPlayer from '../VideoPlayer.jsx'

vi.mock('../../../hooks/useAuth.js', () => ({ useAuth: () => ({ isLoggedIn: false }) }))
vi.mock('../../../api/views.js', () => ({ viewsApi: { record: vi.fn() } }))
vi.mock('../../../api/watchHistory.js', () => ({
  watchHistoryApi: { progress: vi.fn(), summary: vi.fn() },
}))

const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
function render(ui) {
  const wrap = (node) => <QueryClientProvider client={client}>{node}</QueryClientProvider>
  const res = rtlRender(wrap(ui))
  return { ...res, rerender: (next) => res.rerender(wrap(next)) }
}

function setMedia(v, currentTime, duration) {
  Object.defineProperty(v, 'duration', { value: duration, configurable: true })
  Object.defineProperty(v, 'currentTime', { value: currentTime, configurable: true, writable: true })
}

const video = { id: 1, src: 'https://x/stream/a', title: 't' }
const label = (c) => c.querySelector('.video-time')

beforeEach(() => {
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockImplementation(() => Promise.resolve())
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
})

describe('VideoPlayer time label', () => {
  it('shows 0:00 / 0:45 after metadata', () => {
    const { container } = render(<VideoPlayer video={video} active />)
    const v = container.querySelector('video')
    setMedia(v, 0, 45)
    fireEvent.loadedMetadata(v)
    expect(label(container).textContent).toBe('0:00 / 0:45')
  })

  it('updates while playing', () => {
    const { container } = render(<VideoPlayer video={video} active />)
    const v = container.querySelector('video')
    setMedia(v, 0, 45)
    fireEvent.loadedMetadata(v)
    setMedia(v, 12.6, 45)
    fireEvent.timeUpdate(v)
    expect(label(container).textContent).toBe('0:12 / 0:45')
  })

  it('updates on a progress-bar seek', () => {
    const { container } = render(<VideoPlayer video={video} active />)
    const v = container.querySelector('video')
    setMedia(v, 0, 60)
    fireEvent.loadedMetadata(v)
    const track = container.querySelector('div[data-no-toggle]')
    track.getBoundingClientRect = () => ({ left: 0, width: 100 })
    fireEvent.click(track, { clientX: 50 })
    expect(label(container).textContent).toBe('0:30 / 1:00')
  })

  it.each([NaN, Infinity, 0])('is hidden when duration is %s', (dur) => {
    const { container } = render(<VideoPlayer video={video} active />)
    const v = container.querySelector('video')
    setMedia(v, 0, dur)
    fireEvent.loadedMetadata(v)
    fireEvent.timeUpdate(v)
    expect(label(container)).toBeNull()
  })

  it('does not capture pointer events', () => {
    const { container } = render(<VideoPlayer video={video} active />)
    const v = container.querySelector('video')
    setMedia(v, 0, 45)
    fireEvent.loadedMetadata(v)
    expect(label(container).style.pointerEvents).toBe('none')
  })

  it('resets when the video changes', () => {
    const { container, rerender } = render(<VideoPlayer video={video} active />)
    const v = container.querySelector('video')
    setMedia(v, 20, 45)
    fireEvent.loadedMetadata(v)
    fireEvent.timeUpdate(v)
    expect(label(container).textContent).toBe('0:20 / 0:45')
    rerender(<VideoPlayer video={{ ...video, id: 2 }} active />)
    expect(label(container)).toBeNull()
  })
})
