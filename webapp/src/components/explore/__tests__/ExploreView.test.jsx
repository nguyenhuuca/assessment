import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, within } from '@testing-library/react'
import ExploreView from '../ExploreView.jsx'
import { useVideos } from '../../../hooks/useVideos.js'

vi.mock('../../../hooks/useVideos.js', () => ({ useVideos: vi.fn() }))

const v = (id, o) => ({ id, title: `Clip ${id}`, fileId: `f${id}`, upvotes: 0, viewCount: 0, category: 'regular', ...o })
const VIDEOS = [
  v(1, { upvotes: 9 }),            // hero (most liked)
  v(2, { upvotes: 5, viewCount: 1234 }),
  v(3, { upvotes: 3, viewCount: 28000 }),
  v(4, { upvotes: 1, viewCount: 5 }),
]

function renderView(onNavigateToVideo = vi.fn()) {
  useVideos.mockReturnValue({ data: VIDEOS, isLoading: false })
  render(<ExploreView onNavigateToVideo={onNavigateToVideo} />)
  return onNavigateToVideo
}

const tiles = () => screen.getAllByRole('button', { name: /views$/ })

describe('ExploreView reels grid', () => {
  it('renders a portrait tile per video (except the hero) with a compact view count', () => {
    renderView()
    expect(tiles()).toHaveLength(3)
    expect(within(tiles()[0]).getByText('1.2K')).toBeInTheDocument()
    expect(within(tiles()[1]).getByText('28K')).toBeInTheDocument()
    expect(within(tiles()[2]).getByText('5')).toBeInTheDocument()
  })

  it('Most Viewed sorts tiles by view count', () => {
    renderView()
    fireEvent.click(screen.getByText('Most Viewed'))
    expect(tiles().map(t => t.getAttribute('title'))).toEqual(['Clip 2', 'Clip 4', 'Clip 1'])
  })

  it('clicking a tile opens that video in the feed', () => {
    const nav = renderView()
    fireEvent.click(tiles()[1])
    expect(nav).toHaveBeenCalledWith(2) // index of Clip 3 in the feed list
  })
})
