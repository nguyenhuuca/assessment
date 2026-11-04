import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AppShell from '../AppShell.jsx'

let auth
vi.mock('../../../hooks/useAuth.js', () => ({ useAuth: () => auth }))
vi.mock('../../../hooks/useLocaleSync.js', () => ({ useLocaleSync: () => {} }))
vi.mock('../../../hooks/useTheme.js', () => ({ useTheme: () => ({ theme: 'dark', toggle: vi.fn() }) }))
vi.mock('../../../hooks/useNotifications.js', () => ({ useNotificationStream: () => {} }))
vi.mock('../../notifications/NotificationBell.jsx', () => ({ default: () => null }))
vi.mock('../../auth/LoginForm.jsx', () => ({ default: () => <div data-testid="login-form" /> }))
vi.mock('../../auth/MagicLinkHandler.jsx', () => ({ default: () => null }))
vi.mock('../../auth/MFALoginModal.jsx', () => ({ default: () => null }))
vi.mock('../../auth/ProfileModal.jsx', () => ({ default: () => null }))
vi.mock('../../modals/DeleteConfirmModal.jsx', () => ({ default: () => null }))
vi.mock('../../explore/ExploreView.jsx', () => ({ default: () => null }))
vi.mock('../../settings/SettingsPage.jsx', () => ({ default: () => null }))
vi.mock('../../library/LibraryView.jsx', () => ({ default: () => null }))
vi.mock('../../history/HistoryView.jsx', () => ({ default: () => null }))
vi.mock('../../admin/AdminView.jsx', () => ({ default: () => null }))
vi.mock('../../video/VideoFeed.jsx', () => ({ PublicFeed: () => null }))
vi.mock('../../report/ReportPage.jsx', () => ({ default: () => null }))

function renderShell() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><AppShell /></QueryClientProvider>)
}
const header = () => document.querySelector('header')

beforeEach(() => {
  window.history.replaceState({}, '', '/')
  auth = { user: { email: 'alice@x.com', isAdmin: false, role: 'USER' }, jwt: 'j', isLoggedIn: true, logout: vi.fn() }
})

describe('AppShell header', () => {
  it('does not show the username when logged in, but keeps profile and logout', () => {
    renderShell()
    expect(within(header()).queryByText(/alice/)).toBeNull()
    expect(within(header()).getByTitle('Hồ sơ')).toBeInTheDocument()
    expect(within(header()).getByTitle('Đăng xuất')).toBeInTheDocument()
  })

  it('shows the login form when logged out', () => {
    auth = { ...auth, user: null, jwt: null, isLoggedIn: false }
    const { getByTestId } = renderShell()
    expect(getByTestId('login-form')).toBeInTheDocument()
  })
})
