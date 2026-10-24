import React from 'react'
import '../../../i18n/index.js'
import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
import VideoSwiper from '../VideoSwiper.jsx'

vi.mock('../VideoPlayer.jsx', () => ({ default: () => <div data-testid="player" /> }))
vi.mock('../VoteButtons.jsx', () => ({ default: () => null }))
vi.mock('../../modals/SearchModal.jsx', () => ({ default: () => null }))
vi.mock('../../library/AddToCollectionModal.jsx', () => ({ default: () => null }))

const videos = [
  { id: 1, title: 'A', src: 'https://x/stream/1' },
  { id: 2, title: 'B', src: 'https://x/stream/2' },
  { id: 3, title: 'C', src: 'https://x/stream/3', hlsUrl: 'https://x/hls/3/index.m3u8' },
]

describe('VideoSwiper preload', () => {
  it('preloads upcoming Range videos but skips the ones with hlsUrl', () => {
    const { container } = render(<VideoSwiper videos={videos} />)
    const srcs = [...container.querySelectorAll('video')].map(v => v.getAttribute('src'))
    expect(srcs).toEqual(['https://x/stream/2'])
  })
})
