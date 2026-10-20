import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AppShell from '../AppShell.jsx'

let auth
vi.mock('../../../hooks/useAuth.js', () => ({ useAuth: () => auth }))
vi.mock('../../../hooks/useLocaleSync.js', () => ({ useLocaleSync: () => {} }))
vi.mock('../../../hooks/useTheme.js', () => ({ useTheme: () => ({ theme: 'dark', toggle: vi.fn() }) }))
vi.mock('../../../hooks/useNotifications.js', () => ({ useNotificationStream: () => {} }))
vi.mock('../../notifications/NotificationBell.jsx', () => ({ default: () => null }))
vi.mock('../../auth/LoginForm.jsx', () => ({ default: () => null }))
vi.mock('../../auth/MagicLinkHandler.jsx', () => ({ default: () => null }))
vi.mock('../../auth/MFALoginModal.jsx', () => ({ default: () => null }))
vi.mock('../../auth/ProfileModal.jsx', () => ({ default: () => null }))
vi.mock('../../modals/DeleteConfirmModal.jsx', () => ({ default: () => null }))
vi.mock('../../explore/ExploreView.jsx', () => ({ default: () => null }))
vi.mock('../../settings/SettingsPage.jsx', () => ({ default: () => null }))
vi.mock('../../library/LibraryView.jsx', () => ({ default: () => null }))
vi.mock('../../history/HistoryView.jsx', () => ({ default: () => null }))
vi.mock('../../admin/AdminView.jsx', () => ({ default: () => <div data-testid="admin" /> }))
vi.mock('../../video/VideoFeed.jsx', () => ({ PublicFeed: () => <div data-testid="public-feed" /> }))
vi.mock('../../report/ReportPage.jsx', () => ({
  default: ({ fromPage }) => <div data-testid="report" data-from={fromPage} />,
}))

function renderShell() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><AppShell /></QueryClientProvider>)
}
const sidenav = () => document.querySelector('.desktop-sidenav')

beforeEach(() => {
  window.history.replaceState({}, '', '/')
  auth = { user: { email: 'a@x.com', isAdmin: true, role: 'ADMIN' }, jwt: 'j', isLoggedIn: true, logout: vi.fn() }
})

describe('AppShell Report nav', () => {
  it('shows Report for admins, directly above Settings', () => {
    renderShell()
    const labels = within(sidenav()).getAllByRole('button').map(b => b.textContent)
    const report = labels.findIndex(l => /Báo lỗi/.test(l))
    const settings = labels.findIndex(l => /Cài đặt/.test(l))
    expect(report).toBeGreaterThan(-1)
    expect(report + 1).toBe(settings)
  })

  it('hides Report for regular users', () => {
    auth = { ...auth, user: { email: 'u@x.com', isAdmin: false, role: 'USER' } }
    renderShell()
    expect(within(sidenav()).queryByText('Báo lỗi')).toBeNull()
    fireEvent.click(within(document.querySelector('.mobile-nav')).getByRole('button', { name: 'Menu' }))
    expect(screen.queryByRole('menuitem', { name: /Báo lỗi/ })).toBeNull()
  })

  it('opens the page with the previous nav key', () => {
    renderShell()
    fireEvent.click(within(sidenav()).getByText('Báo lỗi'))
    expect(screen.getByTestId('report').dataset.from).toBe('home')
  })

  it('mobile menu lists Report before Settings', () => {
    renderShell()
    fireEvent.click(within(document.querySelector('.mobile-nav')).getByRole('button', { name: 'Menu' }))
    const items = screen.getAllByRole('menuitem')
    const r = items.findIndex(i => /Báo lỗi/.test(i.textContent))
    expect(r).toBeGreaterThan(-1)
    expect(items[r + 1].textContent).toMatch(/Cài đặt/)
    fireEvent.click(items[r])
    expect(screen.getByTestId('report')).toBeInTheDocument()
  })
})
