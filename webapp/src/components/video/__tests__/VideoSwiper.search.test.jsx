import React from 'react'
import '../../../i18n/index.js'
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import VideoSwiper from '../VideoSwiper.jsx'

vi.mock('../VideoPlayer.jsx', () => ({ default: ({ video }) => <div data-testid="player" data-id={video.id} /> }))
vi.mock('../VoteButtons.jsx', () => ({ default: () => null }))
vi.mock('../../library/AddToCollectionModal.jsx', () => ({ default: () => null }))

const feed = [
  { id: 1, title: 'Alpha', src: 'https://x/1' },
  { id: 2, title: 'Beta', src: 'https://x/2' },
]
const inserted = { id: 42, title: 'Secret', src: 'https://x/42' }
const slides = [inserted, ...feed]

function renderSwiper(props) {
  return render(<VideoSwiper videos={slides} searchVideos={feed} initialIndex={0} mobileSearchOpen {...props} />)
}

const currentMarks = container => container.querySelectorAll('.mobile-search-modal-item span[style*="accent-cyan"]')

describe('VideoSwiper search with a deep-linked slide', () => {
  it('does not list the inserted video in search', () => {
    renderSwiper()
    expect(screen.queryByText('Secret')).toBeNull()
    expect(screen.getByText('Alpha')).toBeInTheDocument()
  })

  it('selecting the second feed result shows that slide', () => {
    renderSwiper()
    fireEvent.click(screen.getByText('Beta'))
    expect(screen.getByTestId('player')).toHaveAttribute('data-id', '2')
  })

  it('marks no row current while the inserted slide plays', () => {
    const { container } = renderSwiper()
    expect(currentMarks(container)).toHaveLength(0)
  })

  it('marks the playing feed row current', () => {
    const { container } = renderSwiper({ initialIndex: 2 })
    expect(currentMarks(container)).toHaveLength(1)
  })

  it('searchVideos defaults to videos', () => {
    renderSwiper({ searchVideos: undefined })
    expect(screen.getByText('Secret')).toBeInTheDocument()
  })
})
