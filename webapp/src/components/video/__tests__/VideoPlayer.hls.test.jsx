import React from 'react'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render as rtlRender, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import VideoPlayer from '../VideoPlayer.jsx'

vi.mock('../../../hooks/useAuth.js', () => ({ useAuth: () => ({ isLoggedIn: false }) }))
vi.mock('../../../api/watchHistory.js', () => ({ watchHistoryApi: { progress: vi.fn(), summary: vi.fn() } }))
vi.mock('../../../api/views.js', () => ({ viewsApi: { record: vi.fn(() => Promise.resolve(null)) } }))
vi.mock('hls.js', () => {
  class Hls {
    static isSupported() { return true }
    static Events = { ERROR: 'hlsError' }
    on() {}
    loadSource() {}
    attachMedia() {}
    destroy() {}
  }
  return { default: Hls }
})

const { viewsApi } = await import('../../../api/views.js')

function render(ui) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return rtlRender(<QueryClientProvider client={client}>{ui}</QueryClientProvider>)
}

function tick(container, seconds) {
  const v = container.querySelector('video')
  Object.defineProperty(v, 'duration', { value: 60, configurable: true })
  Object.defineProperty(v, 'currentTime', { value: seconds, configurable: true, writable: true })
  fireEvent.timeUpdate(v)
}

function playTo(container, to, from = 0) {
  for (let t = from + 0.25; t <= to + 1e-9; t += 0.25) tick(container, Math.round(t * 100) / 100)
}

const hlsVideo = { id: 5, src: 'https://x/stream/5', hlsUrl: 'https://x/hls/5/index.m3u8', title: 't' }

function nativeSupport() {
  HTMLMediaElement.prototype.canPlayType.mockImplementation(() => 'maybe')
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockImplementation(() => Promise.resolve())
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
  vi.spyOn(HTMLMediaElement.prototype, 'canPlayType').mockImplementation(() => '')
  Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
})
afterEach(() => vi.restoreAllMocks())

describe('VideoPlayer with hlsUrl', () => {
  it('keeps the Range src when there is no hlsUrl', () => {
    const { container } = render(<VideoPlayer video={{ ...hlsVideo, hlsUrl: null }} active />)
    expect(container.querySelector('video').getAttribute('src')).toBe('https://x/stream/5')
  })

  it('uses the HLS url as src with native support', () => {
    nativeSupport()
    const { container } = render(<VideoPlayer video={hlsVideo} active />)
    expect(container.querySelector('video').getAttribute('src')).toBe(hlsVideo.hlsUrl)
  })

  it('leaves src to hls.js without native support, and uses a single <video>', async () => {
    const { container } = render(<VideoPlayer video={hlsVideo} active />)
    await waitFor(() => expect(container.querySelector('video').hasAttribute('src')).toBe(false))
    expect(container.querySelectorAll('video')).toHaveLength(1)
  })

  it('still counts a view after 3 s of playback on HLS', () => {
    nativeSupport()
    const { container } = render(<VideoPlayer video={hlsVideo} active />)
    playTo(container, 3.5)
    expect(viewsApi.record).toHaveBeenCalledWith(5)
  })

  it('still applies startAt on loadedmetadata on HLS', () => {
    nativeSupport()
    const { container } = render(<VideoPlayer video={hlsVideo} active startAt={20} />)
    const v = container.querySelector('video')
    Object.defineProperty(v, 'duration', { value: 60, configurable: true })
    Object.defineProperty(v, 'currentTime', { value: 0, configurable: true, writable: true })
    fireEvent.loadedMetadata(v)
    expect(v.currentTime).toBe(20)
  })

  it('falls back to the Range src on a media error', () => {
    nativeSupport()
    const { container } = render(<VideoPlayer video={hlsVideo} active />)
    const v = container.querySelector('video')
    fireEvent.error(v)
    expect(v.getAttribute('src')).toBe('https://x/stream/5')
  })
})
