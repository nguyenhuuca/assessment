import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import CommentPanel from '../CommentPanel.jsx'
import { commentsApi } from '../../../api/comments.js'

vi.mock('../../../api/comments.js', () => ({
  commentsApi: { list: vi.fn(), post: vi.fn(), delete: vi.fn() },
}))
vi.mock('../../../hooks/useAuth.js', () => ({
  useAuth: () => ({ user: { email: 'me@x.com' }, isLoggedIn: true }),
}))

describe('CommentPanel', () => {
  it('renders a placeholder for removed comments without author or delete button', async () => {
    commentsApi.list.mockResolvedValue({
      data: [
        { id: 'r1', removed: true },
        { id: 'v1', userEmail: 'me@x.com', content: 'visible one', createdAt: new Date().toISOString() },
      ],
    })
    render(<CommentPanel video={{ id: '1' }} onClose={() => {}} />)
    expect(await screen.findByText('Bình luận đã bị gỡ do vi phạm chính sách')).toBeTruthy()
    expect(screen.getByText('visible one')).toBeTruthy()
    expect(screen.getAllByText('Delete')).toHaveLength(1)
  })
})
