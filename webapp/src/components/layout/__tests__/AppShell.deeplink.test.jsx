import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AppShell from '../AppShell.jsx'
import { commentsApi } from '../../../api/comments.js'
import { notificationsApi } from '../../../api/notifications.js'

vi.mock('../../../api/comments.js', () => ({
  commentsApi: { list: vi.fn(), post: vi.fn(), delete: vi.fn() },
}))
vi.mock('../../../api/sseStream.js', () => ({
  openStream: vi.fn(({ signal }) => new Promise((_, reject) => signal.addEventListener('abort', () => reject(new Error('aborted'))))),
}))
vi.mock('../../../api/notifications.js', () => ({
  NOTIFICATIONS_STREAM_URL: () => 'http://api/notifications/stream',
  notificationsApi: { list: vi.fn(), unreadCount: vi.fn(), markRead: vi.fn(), markAllRead: vi.fn() },
}))

vi.mock('../../../hooks/useAuth.js', () => ({
  useAuth: () => ({ user: { email: 'me@x.com' }, jwt: 'jwt-1', isLoggedIn: true, logout: vi.fn() }),
}))
vi.mock('../../../hooks/useTheme.js', () => ({ useTheme: () => ({ theme: 'dark', toggle: vi.fn() }) }))

vi.mock('../../auth/LoginForm.jsx', () => ({ default: () => null }))
vi.mock('../../auth/MagicLinkHandler.jsx', () => ({ default: () => null }))
vi.mock('../../auth/MFALoginModal.jsx', () => ({ default: () => null }))
vi.mock('../../auth/ProfileModal.jsx', () => ({ default: () => null }))
vi.mock('../../modals/ShareModal.jsx', () => ({ default: () => null }))
vi.mock('../../modals/DeleteConfirmModal.jsx', () => ({ default: () => null }))
vi.mock('../../explore/ExploreView.jsx', () => ({ default: () => null }))
vi.mock('../../admin/AdminView.jsx', () => ({ default: () => null }))
vi.mock('../../settings/SettingsPage.jsx', () => ({ default: () => null }))
vi.mock('../../video/VideoFeed.jsx', () => ({
  PublicFeed: ({ deepLinkId }) => <div data-testid="public-feed" data-deeplink={deepLinkId ?? ''} />,
  PrivateFeed: () => null,
}))

const at = (m) => new Date(Date.now() - m * 60000).toISOString()
const node = (id, content, m, replies = []) => ({
  id, userEmail: 'bob@x.com', content, createdAt: at(m), replies, removed: false, deleted: false,
})

function renderShell() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AppShell />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  window.history.replaceState({}, '', '/')
  const replies = [1, 2, 3, 4, 5].map(i => node(`r${i}`, `reply ${i}`, 50 - i))
  commentsApi.list.mockResolvedValue({ data: [node('root', 'root text', 60, replies)] })
  notificationsApi.unreadCount.mockResolvedValue({ data: { count: 1 } })
  notificationsApi.list.mockResolvedValue({
    data: {
      content: [{
        id: 'n1', type: 'COMMENT_REPLY', videoId: 9, commentId: 'r2', actorDisplay: 'bob',
        actorCount: 1, snippet: 'reply 2', read: false, updatedAt: new Date().toISOString(),
      }],
    },
  })
  notificationsApi.markRead.mockResolvedValue(null)
})

describe('AppShell deep link ?v=&c=', () => {
  it('opens the comment panel, highlights the comment and expands a collapsed reply', async () => {
    window.history.replaceState({}, '', '/?v=7&c=r5')
    renderShell()

    expect(await screen.findByText('reply 5')).toBeTruthy()
    expect(commentsApi.list).toHaveBeenCalledWith('7')
    const row = document.querySelector('[data-comment-id="r5"]')
    expect(row.classList.contains('comment-highlight')).toBe(true)
    expect(document.querySelectorAll('.comment-highlight')).toHaveLength(1)
    expect(screen.getByTestId('public-feed').dataset.deeplink).toBe('7')
    expect(window.location.search).toBe('')
  })

  it('does not open the panel for ?v= alone', async () => {
    window.history.replaceState({}, '', '/?v=7')
    renderShell()
    await waitFor(() => expect(screen.getByTestId('public-feed').dataset.deeplink).toBe('7'))
    expect(commentsApi.list).not.toHaveBeenCalled()
  })

  it('clicking a notification jumps to the video and highlights the comment', async () => {
    renderShell()
    const bells = await screen.findAllByRole('button', { name: 'Thông báo, 1 chưa đọc' })
    fireEvent.click(bells[0])
    fireEvent.click(await screen.findByText('bob đã trả lời bình luận của bạn'))

    await waitFor(() => expect(commentsApi.list).toHaveBeenCalledWith('9'))
    await screen.findByText('root text')
    const row = document.querySelector('[data-comment-id="r2"]')
    expect(row.classList.contains('comment-highlight')).toBe(true)
    expect(screen.getByTestId('public-feed').dataset.deeplink).toBe('9')
    expect(notificationsApi.markRead).toHaveBeenCalledWith('n1')
  })
})
