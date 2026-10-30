import React from 'react'
import '../../../i18n/index.js'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import SearchModal from '../../modals/SearchModal.jsx'
import ExploreView from '../../explore/ExploreView.jsx'
import AddVideosModal from '../../library/AddVideosModal.jsx'
import { useVideos } from '../../../hooks/useVideos.js'
import { collectionsApi } from '../../../api/collections.js'

vi.mock('../../../hooks/useVideos.js', () => ({ useVideos: vi.fn() }))
vi.mock('../../../hooks/useWatchHistory.js', () => ({ useWatchHistorySummary: () => new Map() }))
vi.mock('../../../api/collections.js', () => ({
  collectionsApi: {
    mine: vi.fn(), publicList: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn(),
    videos: vi.fn(), videoIds: vi.fn(), addVideo: vi.fn(), removeVideo: vi.fn(), membership: vi.fn(),
  },
}))

// Surfaces that read the feed list must show exactly that list: a COLLECTION_ONLY video is never in it
const FEED = [
  { id: 1, title: 'Feed One', fileId: 'f1', upvotes: 5, viewCount: 1, category: 'regular' },
  { id: 2, title: 'Feed Two', fileId: 'f2', upvotes: 1, viewCount: 2, category: 'regular' },
  { id: 3, title: 'Feed Three', fileId: 'f3', upvotes: 0, viewCount: 3, category: 'regular' },
]

beforeEach(() => {
  vi.clearAllMocks()
  useVideos.mockReturnValue({ data: FEED, isLoading: false })
  collectionsApi.videoIds.mockResolvedValue({ data: [] })
})

describe('feed-list surfaces show only the feed list', () => {
  it('SearchModal lists only the videos it is given', () => {
    render(<SearchModal show videos={FEED} currentIndex={0} />)
    expect(screen.getAllByText(/^Feed /)).toHaveLength(3)
    expect(screen.queryByText('Collection Only Clip')).toBeNull()
  })

  it('ExploreView shows only useVideos items', () => {
    render(<ExploreView onNavigateToVideo={() => {}} />)
    expect(useVideos).toHaveBeenCalled()
    expect(screen.getAllByText(/Feed (One|Two|Three)/).length).toBeGreaterThanOrEqual(3)
    expect(screen.queryByText('Collection Only Clip')).toBeNull()
  })

  it('AddVideosModal lists only useVideos items', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <AddVideosModal collection={{ id: 'c1', name: 'C', visibility: 'PRIVATE' }} onClose={() => {}} />
      </QueryClientProvider>,
    )
    expect(await screen.findAllByText(/^Feed /)).toHaveLength(3)
    expect(screen.queryByText('Collection Only Clip')).toBeNull()
    expect(useVideos).toHaveBeenCalledWith(null)
  })
})
