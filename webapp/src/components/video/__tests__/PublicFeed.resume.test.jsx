import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render as rtlRender, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { PublicFeed } from '../VideoFeed.jsx'
import { useVideos } from '../../../hooks/useVideos.js'
import { videosApi } from '../../../api/videos.js'

vi.mock('../../../hooks/useVideos.js', () => ({ useVideos: vi.fn() }))
vi.mock('../../../api/videos.js', () => ({ videosApi: { get: vi.fn() } }))
vi.mock('../VideoSwiper.jsx', () => ({
  default: ({ videos, searchVideos, initialIndex, resume }) => (
    <div
      data-testid="swiper"
      data-index={initialIndex}
      data-resume={resume ? JSON.stringify(resume) : ''}
      data-slides={videos.map(v => v.id).join(',')}
      data-search={searchVideos.map(v => v.id).join(',')}
    />
  ),
}))

const videos = [{ id: 1 }, { id: 2 }, { id: 3 }]

function newClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

function render(ui) {
  return rtlRender(<QueryClientProvider client={newClient()}>{ui}</QueryClientProvider>)
}

beforeEach(() => {
  vi.clearAllMocks()
  useVideos.mockReturnValue({ data: videos, isLoading: false, error: null })
})

describe('PublicFeed resume', () => {
  it('hands the resume point to the deep-linked video only', () => {
    const onResolved = vi.fn()
    render(<PublicFeed deepLinkId="3" deepLinkStartAt={42} onDeepLinkResolved={onResolved} />)
    const el = screen.getByTestId('swiper')
    expect(el).toHaveAttribute('data-index', '2')
    expect(JSON.parse(el.dataset.resume)).toEqual({ videoId: '3', startAt: 42 })
    expect(onResolved).toHaveBeenCalledTimes(1)
  })

  it('passes no resume point without startAt or without a deep link', () => {
    const { unmount } = render(<PublicFeed deepLinkId="2" onDeepLinkResolved={() => {}} />)
    expect(screen.getByTestId('swiper')).toHaveAttribute('data-resume', '')
    unmount()
    render(<PublicFeed deepLinkStartAt={42} />)
    expect(screen.getByTestId('swiper')).toHaveAttribute('data-resume', '')
  })
})

describe('PublicFeed deep-link fallback (FR-11)', () => {
  it('does not fetch when the id is in the feed', () => {
    render(<PublicFeed deepLinkId="2" onDeepLinkResolved={() => {}} />)
    expect(videosApi.get).not.toHaveBeenCalled()
    expect(screen.getByTestId('swiper')).toHaveAttribute('data-slides', '1,2,3')
  })

  it('shows the loading state while fetching, then inserts the video at index 0 and resumes it', async () => {
    let resolve
    videosApi.get.mockReturnValue(new Promise(r => { resolve = r }))
    const onResolved = vi.fn()
    render(<PublicFeed deepLinkId="42" deepLinkStartAt={30} onDeepLinkResolved={onResolved} initialIndex={2} />)
    expect(screen.queryByTestId('swiper')).toBeNull()
    expect(document.querySelector('.spinner-border')).not.toBeNull()
    expect(onResolved).not.toHaveBeenCalled()
    resolve({ data: { id: 42, title: 'Hidden gem', embedLink: 'https://x/stream/42' } })
    const el = await screen.findByTestId('swiper')
    expect(el).toHaveAttribute('data-slides', '42,1,2,3')
    expect(el).toHaveAttribute('data-search', '1,2,3')
    expect(el).toHaveAttribute('data-index', '0')
    expect(JSON.parse(el.dataset.resume)).toEqual({ videoId: '42', startAt: 30 })
    expect(videosApi.get).toHaveBeenCalledTimes(1)
    expect(videosApi.get).toHaveBeenCalledWith('42')
    await waitFor(() => expect(onResolved).toHaveBeenCalledTimes(1))
  })

  it('keeps the inserted video after the shell clears deepLinkId', async () => {
    videosApi.get.mockResolvedValue({ data: { id: 42, title: 'X' } })
    const onResolved = vi.fn()
    const client = newClient()
    const ui = id => (
      <QueryClientProvider client={client}>
        <PublicFeed deepLinkId={id} onDeepLinkResolved={onResolved} />
      </QueryClientProvider>
    )
    const { rerender } = rtlRender(ui('42'))
    await screen.findByTestId('swiper')
    rerender(ui(null))
    expect(screen.getByTestId('swiper')).toHaveAttribute('data-slides', '42,1,2,3')
    expect(onResolved).toHaveBeenCalledTimes(1)
  })

  it('falls back to the feed at initialIndex on error, inserts nothing and resolves once', async () => {
    videosApi.get.mockRejectedValue(new Error('400'))
    const onResolved = vi.fn()
    render(<PublicFeed deepLinkId="99" deepLinkStartAt={30} initialIndex={1} onDeepLinkResolved={onResolved} />)
    const el = await screen.findByTestId('swiper')
    expect(el).toHaveAttribute('data-slides', '1,2,3')
    expect(el).toHaveAttribute('data-index', '1')
    expect(el).toHaveAttribute('data-resume', '')
    await waitFor(() => expect(onResolved).toHaveBeenCalledTimes(1))
    expect(videosApi.get).toHaveBeenCalledTimes(1)
  })

  it('keeps the inserted video once when a later feed refetch contains it', async () => {
    videosApi.get.mockResolvedValue({ data: { id: 42 } })
    const client = newClient()
    const ui = id => (
      <QueryClientProvider client={client}>
        <PublicFeed deepLinkId={id} onDeepLinkResolved={() => {}} />
      </QueryClientProvider>
    )
    const { rerender } = rtlRender(ui('42'))
    await screen.findByTestId('swiper')
    useVideos.mockReturnValue({ data: [{ id: 1 }, { id: 42 }, { id: 2 }], isLoading: false, error: null })
    rerender(ui(null))
    const el = screen.getByTestId('swiper')
    expect(el).toHaveAttribute('data-slides', '42,1,2')
    expect(el).toHaveAttribute('data-search', '1,42,2')
  })

  // React Query 5 reports a remounted errored query (no data) as pending while it refetches
  it('waits for the refetch instead of trusting a cached error for the same id', async () => {
    const client = newClient()
    videosApi.get.mockRejectedValueOnce(new Error('400'))
    const first = rtlRender(
      <QueryClientProvider client={client}><PublicFeed deepLinkId="42" onDeepLinkResolved={() => {}} /></QueryClientProvider>,
    )
    await waitFor(() => expect(first.getByTestId('swiper')).toHaveAttribute('data-slides', '1,2,3'))
    first.unmount()
    let resolve
    videosApi.get.mockReturnValue(new Promise(r => { resolve = r }))
    rtlRender(
      <QueryClientProvider client={client}><PublicFeed deepLinkId="42" onDeepLinkResolved={() => {}} /></QueryClientProvider>,
    )
    expect(screen.queryByTestId('swiper')).toBeNull()
    resolve({ data: { id: 42 } })
    expect(await screen.findByTestId('swiper')).toHaveAttribute('data-slides', '42,1,2,3')
  })

  it('leaves the feed list passed to the swiper untouched', async () => {
    videosApi.get.mockResolvedValue({ data: { id: 42 } })
    render(<PublicFeed deepLinkId="42" onDeepLinkResolved={() => {}} />)
    await screen.findByTestId('swiper')
    expect(videos).toHaveLength(3)
  })
})
