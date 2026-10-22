import React from 'react'
import '../../../i18n/index.js'
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import VideoSwiper from '../VideoSwiper.jsx'

vi.mock('../VideoPlayer.jsx', () => ({ default: () => <div data-testid="player" /> }))
vi.mock('../VoteButtons.jsx', () => ({ default: () => null }))
vi.mock('../../modals/SearchModal.jsx', () => ({ default: () => null }))
vi.mock('../../library/AddToCollectionModal.jsx', () => ({ default: () => null }))

const owner = 'me@example.com'
const videos = [{ id: 1, title: 'A', isPrivate: true, userShared: owner }, { id: 2, title: 'B' }]

describe('VideoSwiper action bar', () => {
  it('shows icon-only buttons with a tooltip and an accessible name', () => {
    render(<VideoSwiper videos={videos} currentUser={{ email: owner }} />)
    for (const name of ['Bình luận', 'Lưu', 'Chia sẻ', 'Xóa', 'Video trước', 'Video tiếp theo']) {
      const btn = screen.getByRole('button', { name })
      expect(btn).toHaveAttribute('title', name)
      expect(btn.querySelector('.label')).toBeNull()
      expect(btn.querySelector('.icon')).toHaveAttribute('aria-hidden', 'true')
    }
    expect(screen.queryByText('Bình luận')).toBeNull()
  })
})
