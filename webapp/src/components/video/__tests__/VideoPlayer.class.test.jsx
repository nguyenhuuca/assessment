import React from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render as rtlRender } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import VideoPlayer from '../VideoPlayer.jsx'

vi.mock('../../../hooks/useAuth.js', () => ({ useAuth: () => ({ isLoggedIn: false }) }))
vi.mock('../../../api/watchHistory.js', () => ({ watchHistoryApi: { progress: vi.fn(), summary: vi.fn() } }))
vi.mock('../../../api/views.js', () => ({
  viewsApi: { record: vi.fn(() => Promise.resolve(null)) },
}))

function render(ui) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return rtlRender(<QueryClientProvider client={client}>{ui}</QueryClientProvider>)
}

beforeEach(() => {
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockImplementation(() => Promise.resolve())
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
})

describe('VideoPlayer root element', () => {
  it('has the video-player class so the mobile CSS can square its corners', () => {
    const { container } = render(
      <VideoPlayer video={{ id: 1, src: 'https://x/stream/a', title: 't' }} active />,
    )
    expect(container.firstChild).toHaveClass('video-player')
  })

  it('letterboxes the video (contain) instead of zooming it to fill the frame', () => {
    const { container } = render(
      <VideoPlayer video={{ id: 1, src: 'https://x/stream/a', title: 't' }} active />,
    )
    expect(container.querySelector('video').style.objectFit).toBe('contain')
  })
})
