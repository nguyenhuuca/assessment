import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { PublicFeed } from '../VideoFeed.jsx'
import { useVideos } from '../../../hooks/useVideos.js'

vi.mock('../../../hooks/useVideos.js', () => ({ useVideos: vi.fn() }))
vi.mock('../VideoSwiper.jsx', () => ({
  default: ({ initialIndex, resume }) => (
    <div data-testid="swiper" data-index={initialIndex} data-resume={resume ? JSON.stringify(resume) : ''} />
  ),
}))

const videos = [{ id: 1 }, { id: 2 }, { id: 3 }]

beforeEach(() => {
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
