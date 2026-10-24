import React, { useRef } from 'react'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, waitFor, act } from '@testing-library/react'
import { useHlsSource } from '../useHlsSource.js'

const instances = []
let supported = true
vi.mock('hls.js', () => {
  class Hls {
    static isSupported() { return supported }
    static Events = { ERROR: 'hlsError' }
    constructor() { this.handlers = {}; this.destroy = vi.fn(); this.loadSource = vi.fn(); this.attachMedia = vi.fn(); instances.push(this) }
    on(evt, cb) { this.handlers[evt] = cb }
  }
  return { default: Hls }
})

const HLS = 'https://x/hls/1/index.m3u8'
const RANGE = 'https://x/stream/1'

function Probe({ hlsUrl, rangeUrl = RANGE }) {
  const ref = useRef(null)
  const src = useHlsSource(ref, { hlsUrl, rangeUrl })
  return <video ref={ref} src={src} data-testid="v" />
}

function setNative(on) {
  vi.spyOn(HTMLMediaElement.prototype, 'canPlayType').mockImplementation(() => (on ? 'maybe' : ''))
}

beforeEach(() => { instances.length = 0; supported = true; setNative(false) })
afterEach(() => vi.restoreAllMocks())

describe('useHlsSource', () => {
  it('uses the Range URL and never loads hls.js without hlsUrl', () => {
    const { container } = render(<Probe hlsUrl={null} />)
    expect(container.querySelector('video').getAttribute('src')).toBe(RANGE)
    expect(instances).toHaveLength(0)
  })

  it('uses the HLS URL natively and does not load hls.js', () => {
    setNative(true)
    const { container } = render(<Probe hlsUrl={HLS} />)
    expect(container.querySelector('video').getAttribute('src')).toBe(HLS)
    expect(instances).toHaveLength(0)
  })

  it('attaches hls.js when there is no native support', async () => {
    const { container } = render(<Probe hlsUrl={HLS} />)
    await waitFor(() => expect(instances).toHaveLength(1))
    expect(instances[0].loadSource).toHaveBeenCalledWith(HLS)
    expect(instances[0].attachMedia).toHaveBeenCalledWith(container.querySelector('video'))
    expect(container.querySelector('video').hasAttribute('src')).toBe(false)
  })

  it('falls back to the Range URL when hls.js is not supported', async () => {
    supported = false
    const { container } = render(<Probe hlsUrl={HLS} />)
    await waitFor(() => expect(container.querySelector('video').getAttribute('src')).toBe(RANGE))
    expect(instances).toHaveLength(0)
  })

  it('on a fatal error destroys hls.js, switches to Range and restores position and play state', async () => {
    const { container } = render(<Probe hlsUrl={HLS} />)
    await waitFor(() => expect(instances).toHaveLength(1))
    const v = container.querySelector('video')
    Object.defineProperty(v, 'currentTime', { value: 42, configurable: true, writable: true })
    Object.defineProperty(v, 'paused', { value: false, configurable: true })
    const play = vi.spyOn(HTMLMediaElement.prototype, 'play').mockImplementation(() => Promise.resolve())

    act(() => instances[0].handlers.hlsError('hlsError', { fatal: true }))

    expect(instances[0].destroy).toHaveBeenCalled()
    await waitFor(() => expect(v.getAttribute('src')).toBe(RANGE))
    v.currentTime = 0
    act(() => { v.dispatchEvent(new Event('loadedmetadata')) })
    expect(v.currentTime).toBe(42)
    expect(play).toHaveBeenCalled()
  })

  it('does not apply the old position when the video switches before metadata loads', async () => {
    const { container, rerender } = render(<Probe hlsUrl={HLS} />)
    await waitFor(() => expect(instances).toHaveLength(1))
    const v = container.querySelector('video')
    Object.defineProperty(v, 'currentTime', { value: 42, configurable: true, writable: true })
    Object.defineProperty(v, 'paused', { value: true, configurable: true })
    act(() => instances[0].handlers.hlsError('hlsError', { fatal: true }))
    await waitFor(() => expect(v.getAttribute('src')).toBe(RANGE))

    rerender(<Probe hlsUrl="https://x/hls/2/index.m3u8" rangeUrl="https://x/stream/2" />)
    v.currentTime = 0
    act(() => { v.dispatchEvent(new Event('loadedmetadata')) })
    expect(v.currentTime).toBe(0)
  })

  it('ignores non-fatal hls.js errors', async () => {
    const { container } = render(<Probe hlsUrl={HLS} />)
    await waitFor(() => expect(instances).toHaveLength(1))
    act(() => instances[0].handlers.hlsError('hlsError', { fatal: false }))
    expect(instances[0].destroy).not.toHaveBeenCalled()
    expect(container.querySelector('video').hasAttribute('src')).toBe(false)
  })

  it('falls back when the native HLS source raises a media error', () => {
    setNative(true)
    const { container } = render(<Probe hlsUrl={HLS} />)
    const v = container.querySelector('video')
    act(() => { v.dispatchEvent(new Event('error')) })
    expect(v.getAttribute('src')).toBe(RANGE)
  })

  it('destroys hls.js on unmount', async () => {
    const { unmount } = render(<Probe hlsUrl={HLS} />)
    await waitFor(() => expect(instances).toHaveLength(1))
    unmount()
    expect(instances[0].destroy).toHaveBeenCalled()
  })

  it('destroys hls.js when the video changes', async () => {
    const { rerender } = render(<Probe hlsUrl={HLS} />)
    await waitFor(() => expect(instances).toHaveLength(1))
    rerender(<Probe hlsUrl="https://x/hls/2/index.m3u8" rangeUrl="https://x/stream/2" />)
    expect(instances[0].destroy).toHaveBeenCalled()
    await waitFor(() => expect(instances).toHaveLength(2))
  })
})
