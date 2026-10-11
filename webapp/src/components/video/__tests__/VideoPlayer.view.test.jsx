import React from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, fireEvent } from '@testing-library/react'
import VideoPlayer from '../VideoPlayer.jsx'

vi.mock('../../../api/views.js', () => ({
  viewsApi: { record: vi.fn(() => Promise.resolve(null)) },
}))

const { viewsApi } = await import('../../../api/views.js')

function tick(container, seconds) {
  const v = container.querySelector('video')
  Object.defineProperty(v, 'duration', { value: 60, configurable: true })
  Object.defineProperty(v, 'currentTime', { value: seconds, configurable: true, writable: true })
  fireEvent.timeUpdate(v)
}

/** Plays from `from` to `to` in 0.25 s steps, like a real <video>. */
function playTo(container, to, from = 0) {
  for (let t = from + 0.25; t <= to + 1e-9; t += 0.25) tick(container, Math.round(t * 100) / 100)
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockImplementation(() => Promise.resolve())
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
  Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
})

describe('VideoPlayer view tracking', () => {
  const video = { id: 12, src: 'https://x/stream/a', title: 't' }

  it('does not count before 3 seconds of playback', () => {
    const { container } = render(<VideoPlayer video={video} active />)
    playTo(container, 2.5)
    expect(viewsApi.record).not.toHaveBeenCalled()
  })

  it('counts once after 3 seconds, not again on later time updates', () => {
    const { container } = render(<VideoPlayer video={video} active />)
    playTo(container, 3.1)
    playTo(container, 10)
    expect(viewsApi.record).toHaveBeenCalledTimes(1)
    expect(viewsApi.record).toHaveBeenCalledWith(12)
  })

  it('seeking to 3 s without watching does not count', () => {
    const { container } = render(<VideoPlayer video={video} active />)
    tick(container, 0.25)
    tick(container, 5)   // user jumped ahead on the progress bar
    tick(container, 5.25)
    expect(viewsApi.record).not.toHaveBeenCalled()
  })

  it('playback in a hidden tab does not count', () => {
    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true })
    const { container } = render(<VideoPlayer video={video} active />)
    playTo(container, 6)
    expect(viewsApi.record).not.toHaveBeenCalled()
  })

  it('counts again for the next video', () => {
    const { container, rerender } = render(<VideoPlayer video={video} active />)
    playTo(container, 4)
    rerender(<VideoPlayer video={{ ...video, id: 13 }} active />)
    tick(container, 0) // new source starts at 0
    playTo(container, 4)
    expect(viewsApi.record).toHaveBeenNthCalledWith(2, 13)
  })

  it('does not count an inactive (background) player', () => {
    const { container } = render(<VideoPlayer video={video} active={false} />)
    playTo(container, 5)
    expect(viewsApi.record).not.toHaveBeenCalled()
  })
})
