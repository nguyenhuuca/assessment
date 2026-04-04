import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import VoteButtons from '../VoteButtons.jsx'

vi.mock('../../../api/videos.js', () => ({
  videosApi: {
    like: vi.fn().mockResolvedValue({}),
    unlike: vi.fn().mockResolvedValue({}),
  },
}))

const mockVideo = { id: 'v1', upvotes: 5, downvotes: 2 }

describe('VoteButtons', () => {
  it('renders initial vote counts', () => {
    render(<VoteButtons video={mockVideo} />)
    // upvote count is shown; dislike button shows "Dislike" label (no count)
    expect(screen.getByText('5')).toBeInTheDocument()
    expect(screen.getByTitle('Dislike')).toBeInTheDocument()
  })

  it('increments upvote count on click', async () => {
    render(<VoteButtons video={mockVideo} />)
    const buttons = screen.getAllByRole('button')
    fireEvent.click(buttons[0]) // up vote
    await waitFor(() => {
      expect(screen.getByText('6')).toBeInTheDocument()
    })
  })

  it('un-votes when clicking active vote again', async () => {
    render(<VoteButtons video={mockVideo} />)
    const buttons = screen.getAllByRole('button')
    fireEvent.click(buttons[0]) // vote up → 6
    fireEvent.click(buttons[0]) // un-vote → 5
    await waitFor(() => {
      expect(screen.getByText('5')).toBeInTheDocument()
    })
  })

  it('resets state when video id changes', async () => {
    const { rerender } = render(<VoteButtons video={mockVideo} />)
    const buttons = screen.getAllByRole('button')
    fireEvent.click(buttons[0]) // vote up
    await waitFor(() => expect(screen.getByText('6')).toBeInTheDocument())

    rerender(<VoteButtons video={{ id: 'v2', upvotes: 10, downvotes: 3 }} />)
    await waitFor(() => {
      expect(screen.getByText('10')).toBeInTheDocument()
    })
  })
})
