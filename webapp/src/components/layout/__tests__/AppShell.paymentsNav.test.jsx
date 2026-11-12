import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
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
vi.mock('../../admin/AdminView.jsx', () => ({ default: () => null }))
vi.mock('../../video/VideoFeed.jsx', () => ({ PublicFeed: () => null }))
vi.mock('../../report/ReportPage.jsx', () => ({ default: () => null }))
vi.mock('../../../api/payments.js', () => ({ getPaymentsConfig: vi.fn() }))
import * as paymentsApi from '../../../api/payments.js'

function renderShell() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><AppShell /></QueryClientProvider>)
}

beforeEach(() => {
  window.history.replaceState({}, '', '/')
  auth = { user: { email: 'a@x.com', isAdmin: false, role: 'USER' }, jwt: 'j', isLoggedIn: true, logout: vi.fn() }
})

describe('AppShell donate nav vs payments config', () => {
  it('shows the donate nav when payments are enabled', async () => {
    paymentsApi.getPaymentsConfig.mockResolvedValue({ data: { enabled: true } })
    renderShell()
    expect((await screen.findAllByText('Ủng hộ')).length).toBeGreaterThan(0)
  })

  it('hides the donate nav when payments are disabled', async () => {
    paymentsApi.getPaymentsConfig.mockResolvedValue({ data: { enabled: false } })
    renderShell()
    await waitFor(() => expect(paymentsApi.getPaymentsConfig).toHaveBeenCalled())
    expect(screen.queryByText('Ủng hộ')).toBeNull()
  })

  it('hides it while the config is still loading', () => {
    paymentsApi.getPaymentsConfig.mockReturnValue(new Promise(() => {}))
    renderShell()
    expect(screen.queryByText('Ủng hộ')).toBeNull()
  })
})
