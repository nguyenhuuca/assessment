import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AppShell from '../AppShell.jsx'

vi.mock('../../../hooks/useAuth.js', () => ({
  useAuth: () => ({ user: { email: 'me@x.com' }, jwt: 'jwt-1', isLoggedIn: true, logout: vi.fn() }),
}))
vi.mock('../../../hooks/useLocaleSync.js', () => ({ useLocaleSync: () => {} }))
vi.mock('../../../hooks/useNotifications.js', () => ({ useNotificationStream: () => {} }))
vi.mock('../../../hooks/useTheme.js', () => ({ useTheme: () => ({ theme: 'dark', toggle: vi.fn() }) }))
vi.mock('../../notifications/NotificationBell.jsx', () => ({ default: () => null }))
vi.mock('../../auth/LoginForm.jsx', () => ({ default: () => null }))
vi.mock('../../auth/MagicLinkHandler.jsx', () => ({ default: () => null }))
vi.mock('../../auth/MFALoginModal.jsx', () => ({ default: () => null }))
vi.mock('../../auth/ProfileModal.jsx', () => ({ default: () => null }))
vi.mock('../../modals/DeleteConfirmModal.jsx', () => ({ default: () => null }))
vi.mock('../../history/HistoryView.jsx', () => ({
  default: ({ onPlayVideo }) => (
    <div>
      <button onClick={() => onPlayVideo(5, { startAt: 33 })}>resume</button>
      <button onClick={() => onPlayVideo(6, { startAt: 0 })}>restart</button>
    </div>
  ),
}))
vi.mock('../../video/VideoFeed.jsx', () => ({
  PublicFeed: ({ deepLinkId, deepLinkStartAt }) => (
    <div data-testid="public-feed" data-deeplink={deepLinkId ?? ''} data-startat={deepLinkStartAt ?? ''} />
  ),
}))

function renderShell() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><AppShell /></QueryClientProvider>)
}

beforeEach(() => {
  window.history.replaceState({}, '', '/')
})

describe('AppShell playVideo(id, { startAt })', () => {
  it('passes the resume point to the home feed together with the deep link', () => {
    renderShell()
    fireEvent.click(screen.getAllByRole('button', { name: /Lịch sử/ })[0])
    fireEvent.click(screen.getByRole('button', { name: 'resume' }))
    const feed = screen.getByTestId('public-feed')
    expect(feed).toHaveAttribute('data-deeplink', '5')
    expect(feed).toHaveAttribute('data-startat', '33')
  })

  it('startAt 0 means no resume point', () => {
    renderShell()
    fireEvent.click(screen.getAllByRole('button', { name: /Lịch sử/ })[0])
    fireEvent.click(screen.getByRole('button', { name: 'restart' }))
    const feed = screen.getByTestId('public-feed')
    expect(feed).toHaveAttribute('data-deeplink', '6')
    expect(feed).toHaveAttribute('data-startat', '')
  })
})
