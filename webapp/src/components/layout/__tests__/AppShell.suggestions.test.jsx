import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AppShell from '../AppShell.jsx'
import { notificationsApi } from '../../../api/notifications.js'
import { suggestionsApi } from '../../../api/suggestions.js'

let auth
vi.mock('../../../hooks/useAuth.js', () => ({ useAuth: () => auth }))
vi.mock('../../../hooks/useLocaleSync.js', () => ({ useLocaleSync: () => {} }))
vi.mock('../../../hooks/useTheme.js', () => ({ useTheme: () => ({ theme: 'dark', toggle: vi.fn() }) }))
vi.mock('../../../api/sseStream.js', () => ({
  openStream: vi.fn(({ signal }) => new Promise((_, reject) => signal.addEventListener('abort', () => reject(new Error('aborted'))))),
}))
vi.mock('../../../api/notifications.js', () => ({
  NOTIFICATIONS_STREAM_URL: () => 'http://api/notifications/stream',
  notificationsApi: { list: vi.fn(), unreadCount: vi.fn(), markRead: vi.fn(), markAllRead: vi.fn() },
}))
vi.mock('../../../api/suggestions.js', () => ({
  suggestionsApi: { submit: vi.fn(), mine: vi.fn() },
}))

vi.mock('../../auth/LoginForm.jsx', () => ({ default: () => null }))
vi.mock('../../auth/MagicLinkHandler.jsx', () => ({ default: () => null }))
vi.mock('../../auth/MFALoginModal.jsx', () => ({ default: () => null }))
vi.mock('../../auth/ProfileModal.jsx', () => ({ default: () => null }))
vi.mock('../../modals/DeleteConfirmModal.jsx', () => ({ default: () => null }))
vi.mock('../../explore/ExploreView.jsx', () => ({ default: () => null }))
vi.mock('../../settings/SettingsPage.jsx', () => ({ default: () => null }))
vi.mock('../../library/LibraryView.jsx', () => ({ default: () => null }))
vi.mock('../../history/HistoryView.jsx', () => ({ default: () => <div data-testid="history" /> }))
vi.mock('../../admin/AdminView.jsx', () => ({
  default: ({ initialTab }) => <div data-testid="admin" data-tab={initialTab ?? ''} />,
}))
vi.mock('../../video/VideoFeed.jsx', () => ({
  PublicFeed: ({ deepLinkId }) => <div data-testid="public-feed" data-deeplink={deepLinkId ?? ''} />,
}))

const notif = (over) => ({
  id: 'n1', type: 'SUGGESTION_LIVE', videoId: 55, commentId: null, actorDisplay: null, actorCount: 0,
  snippet: 'Clip hay', reason: null, read: false, updatedAt: new Date().toISOString(), ...over,
})

function renderShell() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AppShell />
    </QueryClientProvider>,
  )
}

async function clickNotification(text) {
  const bells = await screen.findAllByRole('button', { name: 'Thông báo, 1 chưa đọc' })
  fireEvent.click(bells[0])
  fireEvent.click(await screen.findByText(text))
}

beforeEach(() => {
  vi.clearAllMocks()
  window.history.replaceState({}, '', '/')
  auth = { user: { email: 'me@x.com' }, jwt: 'j', isLoggedIn: true, logout: vi.fn() }
  suggestionsApi.mine.mockResolvedValue({ data: { content: [], totalPages: 0, totalElements: 0 } })
  notificationsApi.unreadCount.mockResolvedValue({ data: { count: 1 } })
  notificationsApi.list.mockResolvedValue({ data: { content: [notif()] } })
  notificationsApi.markRead.mockResolvedValue(null)
})

describe('AppShell "+" button', () => {
  it('desktop FAB opens the suggestion modal for a regular user', () => {
    renderShell()
    fireEvent.click(document.querySelector('.fab'))
    expect(screen.getByRole('dialog', { name: 'Đề xuất video' })).toBeInTheDocument()
    expect(screen.queryByTestId('admin')).toBeNull()
  })

  it('mobile FAB opens the suggestion modal too, and there is no header upload button', () => {
    renderShell()
    fireEvent.click(within(document.querySelector('.mobile-nav')).getByTitle('Đề xuất video'))
    expect(screen.getByRole('dialog', { name: 'Đề xuất video' })).toBeInTheDocument()
    expect(screen.queryByText('upload')).toBeNull()
  })

  it('admin FAB navigates to Admin with the Import tab preselected', () => {
    auth = { ...auth, user: { email: 'a@x.com', isAdmin: true } }
    renderShell()
    fireEvent.click(document.querySelector('.fab'))
    expect(screen.getByTestId('admin').dataset.tab).toBe('IMPORT_VIDEO')
    expect(screen.queryByRole('dialog', { name: 'Đề xuất video' })).toBeNull()
  })

  it('the FAB is hidden for guests', () => {
    auth = { user: null, jwt: null, isLoggedIn: false, logout: vi.fn() }
    renderShell()
    expect(document.querySelector('.fab')).toBeNull()
  })

  it('"Xem tất cả" in the modal opens History', () => {
    renderShell()
    fireEvent.click(document.querySelector('.fab'))
    fireEvent.click(screen.getByRole('button', { name: 'Xem tất cả' }))
    expect(screen.getByTestId('history')).toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: 'Đề xuất video' })).toBeNull()
  })

  it('"Xem" on a live item in the modal plays that video', async () => {
    suggestionsApi.mine.mockResolvedValue({
      data: { content: [{ id: 1, title: 'Live clip', sourceUrl: 'https://youtu.be/1', status: 'APPROVED', live: true, liveVideoId: 31 }], totalPages: 1 },
    })
    renderShell()
    fireEvent.click(document.querySelector('.fab'))
    fireEvent.click(await screen.findByRole('button', { name: 'Xem video Live clip' }))
    await waitFor(() => expect(screen.getByTestId('public-feed').dataset.deeplink).toBe('31'))
    expect(screen.queryByRole('dialog', { name: 'Đề xuất video' })).toBeNull()
  })
})

describe('AppShell suggestion notifications', () => {
  it('SUGGESTION_LIVE plays the video', async () => {
    renderShell()
    await clickNotification('Video bạn đề xuất đã lên app: Clip hay')
    await waitFor(() => expect(screen.getByTestId('public-feed').dataset.deeplink).toBe('55'))
  })

  it.each([
    ['SUGGESTION_APPROVED', 'Đề xuất của bạn đã được duyệt: Clip hay'],
    ['SUGGESTION_REJECTED', 'Đề xuất của bạn bị từ chối: Clip hay (Vi phạm bản quyền)'],
  ])('%s opens History', async (type, text) => {
    notificationsApi.list.mockResolvedValue({ data: { content: [notif({ type, videoId: null, reason: 'COPYRIGHT' })] } })
    renderShell()
    await clickNotification(text)
    expect(await screen.findByTestId('history')).toBeInTheDocument()
    expect(notificationsApi.markRead).toHaveBeenCalledWith('n1')
  })
})

describe('ShareModal removal', () => {
  it('leaves no ShareModal source file or reference', async () => {
    const files = import.meta.glob('../../**/*.{js,jsx}', { query: '?raw', import: 'default', eager: true })
    for (const [path, src] of Object.entries(files)) {
      if (path.includes('/__tests__/')) continue
      expect(src, path).not.toMatch(/ShareModal/)
    }
  })
})
