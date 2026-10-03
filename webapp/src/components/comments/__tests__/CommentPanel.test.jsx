import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import CommentPanel from '../CommentPanel.jsx'
import { commentsApi } from '../../../api/comments.js'

vi.mock('../../../api/comments.js', () => ({
  commentsApi: { list: vi.fn(), post: vi.fn(), delete: vi.fn() },
}))

let auth = { user: { email: 'me@x.com' }, isLoggedIn: true }
vi.mock('../../../hooks/useAuth.js', () => ({
  useAuth: () => auth,
}))

const now = Date.now()
const at = (minsAgo) => new Date(now - minsAgo * 60000).toISOString()
const node = (id, email, content, minsAgo, extra = {}) => ({
  id, userEmail: email, content, createdAt: at(minsAgo), replies: [], removed: false, deleted: false, ...extra,
})

function renderPanel() {
  return render(<CommentPanel video={{ id: '1' }} onClose={() => {}} />)
}

beforeEach(() => {
  vi.clearAllMocks()
  auth = { user: { email: 'me@x.com' }, isLoggedIn: true }
  commentsApi.post.mockResolvedValue({})
})

describe('CommentPanel', () => {
  it('renders a placeholder for removed comments without author or delete button', async () => {
    commentsApi.list.mockResolvedValue({
      data: [
        { id: 'r1', removed: true },
        { id: 'v1', userEmail: 'me@x.com', content: 'visible one', createdAt: new Date().toISOString() },
      ],
    })
    renderPanel()
    expect(await screen.findByText('Bình luận đã bị gỡ do vi phạm chính sách')).toBeTruthy()
    expect(screen.getByText('visible one')).toBeTruthy()
    expect(screen.getAllByText('Delete')).toHaveLength(1)
  })

  it('renders replies under their root and counts them in the header', async () => {
    commentsApi.list.mockResolvedValue({
      data: [node('a', 'alice@x.com', 'root text', 60, { replies: [node('b', 'bob@x.com', 'reply text', 30)] })],
    })
    renderPanel()
    expect(await screen.findByText('root text')).toBeTruthy()
    expect(screen.getByText('reply text')).toBeTruthy()
    expect(screen.getByText('2 comments')).toBeTruthy()
  })

  it('collapses after 2 replies and expands on demand', async () => {
    const replies = [1, 2, 3, 4, 5].map(i => node(`r${i}`, 'bob@x.com', `reply ${i}`, 50 - i))
    commentsApi.list.mockResolvedValue({ data: [node('a', 'alice@x.com', 'root text', 60, { replies })] })
    renderPanel()
    await screen.findByText('root text')
    expect(screen.getByText('reply 1')).toBeTruthy()
    expect(screen.getByText('reply 2')).toBeTruthy()
    expect(screen.queryByText('reply 3')).toBeNull()
    fireEvent.click(screen.getByText('Xem thêm 3 phản hồi ▾'))
    expect(screen.getByText('reply 5')).toBeTruthy()
    fireEvent.click(screen.getByText('Ẩn phản hồi ▴'))
    expect(screen.queryByText('reply 3')).toBeNull()
  })

  it('flattens deeper legacy replies and sorts them by createdAt', async () => {
    const deep = node('c', 'carol@x.com', 'deep reply', 10)
    const mid = node('b', 'bob@x.com', 'mid reply', 40, { replies: [deep] })
    commentsApi.list.mockResolvedValue({ data: [node('a', 'alice@x.com', 'root text', 60, { replies: [mid] })] })
    renderPanel()
    await screen.findByText('root text')
    const text = document.body.textContent
    expect(text.indexOf('mid reply')).toBeLessThan(text.indexOf('deep reply'))
  })

  it('replying to a root posts with the root id, reloads and keeps the thread expanded', async () => {
    const replies = [1, 2, 3].map(i => node(`r${i}`, 'bob@x.com', `reply ${i}`, 50 - i))
    commentsApi.list.mockResolvedValue({ data: [node('a', 'alice@x.com', 'root text', 60, { replies })] })
    renderPanel()
    await screen.findByText('root text')
    fireEvent.click(screen.getAllByText('Trả lời')[0])
    const box = screen.getByLabelText('Reply')
    expect(box.value).toBe('')
    fireEvent.change(box, { target: { value: 'hello' } })

    commentsApi.list.mockResolvedValue({
      data: [node('a', 'alice@x.com', 'root text', 60, { replies: [...replies, node('r4', 'me@x.com', 'hello', 0)] })],
    })
    fireEvent.keyDown(box, { key: 'Enter' })
    await waitFor(() => expect(commentsApi.post).toHaveBeenCalledWith('1', 'hello', 'a'))
    expect(await screen.findByText('hello')).toBeTruthy()
    expect(screen.getByText('reply 3')).toBeTruthy() // still expanded
    expect(screen.queryByLabelText('Reply')).toBeNull() // box closed
  })

  it('replying to a reply prefills @name and posts the replied comment id (server notifies its author)', async () => {
    commentsApi.list.mockResolvedValue({
      data: [node('a', 'alice@x.com', 'root text', 60, { replies: [node('b', 'bob@x.com', 'bob says', 30)] })],
    })
    renderPanel()
    await screen.findByText('bob says')
    // actions order: root Trả lời, reply Trả lời
    fireEvent.click(screen.getAllByText('Trả lời')[1])
    const box = screen.getByLabelText('Reply')
    expect(box.value).toBe('@bob ')
    fireEvent.change(box, { target: { value: '@bob thanks' } })
    fireEvent.click(screen.getByLabelText('Send reply'))
    // 'b' (bob's reply), not root 'a' — otherwise bob is never notified (regression)
    await waitFor(() => expect(commentsApi.post).toHaveBeenCalledWith('1', '@bob thanks', 'b'))
    // the reply box still lives in the root's thread
    await waitFor(() => expect(screen.queryByLabelText('Reply')).toBeNull())
  })

  it('Shift+Enter does not send and Esc closes the box; only one box is open', async () => {
    commentsApi.list.mockResolvedValue({
      data: [
        node('a', 'alice@x.com', 'first root', 60),
        node('b', 'bob@x.com', 'second root', 50),
      ],
    })
    renderPanel()
    await screen.findByText('first root')
    fireEvent.click(screen.getAllByText('Trả lời')[0])
    fireEvent.click(screen.getAllByText('Trả lời')[1])
    expect(screen.getAllByLabelText('Reply')).toHaveLength(1)
    const box = screen.getByLabelText('Reply')
    fireEvent.change(box, { target: { value: 'draft' } })
    fireEvent.keyDown(box, { key: 'Enter', shiftKey: true })
    expect(commentsApi.post).not.toHaveBeenCalled()
    fireEvent.keyDown(box, { key: 'Escape' })
    expect(screen.queryByLabelText('Reply')).toBeNull()
  })

  it('shows the server error inline and keeps the reply text', async () => {
    commentsApi.list.mockResolvedValue({ data: [node('a', 'alice@x.com', 'root text', 60)] })
    commentsApi.post.mockRejectedValue({ message: 'Parent comment not visible', status: 400 })
    renderPanel()
    await screen.findByText('root text')
    fireEvent.click(screen.getByText('Trả lời'))
    const box = screen.getByLabelText('Reply')
    fireEvent.change(box, { target: { value: 'oops' } })
    fireEvent.keyDown(box, { key: 'Enter' })
    expect((await screen.findByRole('alert')).textContent).toContain('Parent comment not visible')
    expect(screen.getByLabelText('Reply').value).toBe('oops')
  })

  it('renders removed and deleted placeholders in a thread without actions', async () => {
    commentsApi.list.mockResolvedValue({
      data: [
        node('a', null, null, 60, {
          deleted: true, userEmail: null,
          replies: [
            node('b', null, null, 30, { removed: true, userEmail: null }),
            node('c', 'bob@x.com', 'still here', 20),
          ],
        }),
      ],
    })
    renderPanel()
    expect(await screen.findByText('Bình luận đã bị xoá')).toBeTruthy()
    expect(screen.getByText('Bình luận đã bị gỡ do vi phạm chính sách')).toBeTruthy()
    expect(screen.getByText('still here')).toBeTruthy()
    expect(screen.getAllByText('Trả lời')).toHaveLength(1) // only the visible reply
    expect(screen.queryByText('Delete')).toBeNull()
    expect(screen.getByText('1 comment')).toBeTruthy()
  })

  it('guests see no Reply action', async () => {
    auth = { user: null, isLoggedIn: false }
    commentsApi.list.mockResolvedValue({
      data: [node('a', 'alice@x.com', 'root text', 60, { replies: [node('b', 'bob@x.com', 'reply', 30)] })],
    })
    renderPanel()
    await screen.findByText('root text')
    expect(screen.queryByText('Trả lời')).toBeNull()
  })

  it('deleting reloads the list', async () => {
    commentsApi.list.mockResolvedValue({ data: [node('a', 'me@x.com', 'mine', 60)] })
    commentsApi.delete.mockResolvedValue({})
    renderPanel()
    await screen.findByText('mine')
    fireEvent.click(screen.getByText('Delete'))
    commentsApi.list.mockResolvedValue({ data: [] })
    fireEvent.click(screen.getByText('Confirm delete'))
    await waitFor(() => expect(commentsApi.delete).toHaveBeenCalledWith('1', 'a'))
    expect(await screen.findByText('No comments yet')).toBeTruthy()
  })
})
