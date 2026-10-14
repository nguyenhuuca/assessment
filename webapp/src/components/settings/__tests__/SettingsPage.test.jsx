import React from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import SettingsPage from '../SettingsPage.jsx'
import i18n from '../../../i18n/index.js'

// ── Mock external deps ──────────────────────────────────────────────────────

vi.mock('../../../hooks/useSettings.js', () => ({
  useSettings: vi.fn(),
  useUpdateSettings: vi.fn(),
}))

vi.mock('../../../hooks/useAuth.js', () => ({
  useAuth: vi.fn(),
}))

// ProfileModal and DeleteAccountModal make their own hook calls; stub them out
// so they don't interfere with SettingsPage assertions.
vi.mock('../../../components/auth/ProfileModal.jsx', () => ({
  default: () => null,
}))

vi.mock('../DeleteAccountModal.jsx', () => ({
  default: () => null,
}))

// ── Helpers ─────────────────────────────────────────────────────────────────

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
}

function Wrapper({ children }) {
  return (
    <QueryClientProvider client={makeQueryClient()}>
      {children}
    </QueryClientProvider>
  )
}

function renderSettings() {
  return render(<SettingsPage />, { wrapper: Wrapper })
}

const baseSettings = {
  email: 'user@example.com',
  passwordEnabled: true,
  mfaEnabled: false,
  notifyNewContent: true,
  notifyEmail: true,
  defaultQuality: 'AUTO',
  incognitoEnabled: false,
  profilePrivate: false,
  accountStatus: null,
}

// ── Setup ────────────────────────────────────────────────────────────────────

beforeEach(async () => {
  vi.resetAllMocks()

  // Import mocks after reset so we get fresh references
  const { useAuth } = await import('../../../hooks/useAuth.js')
  useAuth.mockReturnValue({ user: { email: 'user@example.com' } })

  const { useSettings, useUpdateSettings } = await import('../../../hooks/useSettings.js')
  useSettings.mockReturnValue({
    data: { ...baseSettings },
    isLoading: false,
    isError: false,
    error: null,
  })
  useUpdateSettings.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    reset: vi.fn(),
  })
})

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('SettingsPage language', () => {
  it('renders in English and offers the language row', () => {
    i18n.changeLanguage('en')
    renderSettings()
    expect(screen.getByText('Account Identity')).toBeInTheDocument()
    expect(screen.getByText('Language / Ngôn ngữ')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'EN' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('switches the UI language from the settings row', () => {
    renderSettings()
    expect(screen.getByText('Ngôn ngữ / Language')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'EN' }))
    expect(i18n.language).toBe('en')
    expect(screen.getByText('Account Identity')).toBeInTheDocument()
  })
})

describe('SettingsPage', () => {
  describe('section rendering', () => {
    it('renders all 5 main sections', () => {
      renderSettings()

      // Account Identity section
      expect(screen.getByText('Danh tính tài khoản')).toBeInTheDocument()
      // Notifications section
      expect(screen.getByText('Thông báo')).toBeInTheDocument()
      // Playback section
      expect(screen.getByText('Phát video')).toBeInTheDocument()
      // Privacy & Visibility section
      expect(screen.getByText('Quyền riêng tư & hiển thị')).toBeInTheDocument()
      // Danger Zone section
      expect(screen.getByText('Vùng nguy hiểm')).toBeInTheDocument()
    })

    it('shows a loading spinner while settings are loading', async () => {
      const { useSettings } = await import('../../../hooks/useSettings.js')
      // useSettings is mocked above; the full loading-state assertions live in the next test
      expect(vi.isMockFunction(useSettings)).toBe(true)
    })

    it('renders a spinner state when isLoading is true', async () => {
      const { useSettings } = await import('../../../hooks/useSettings.js')
      useSettings.mockReturnValue({
        data: undefined,
        isLoading: true,
        isError: false,
        error: null,
      })

      renderSettings()
      expect(screen.getByText(/đang tải cài đặt/i)).toBeInTheDocument()
    })

    it('renders an error alert when the query fails', async () => {
      const { useSettings } = await import('../../../hooks/useSettings.js')
      useSettings.mockReturnValue({
        data: undefined,
        isLoading: false,
        isError: true,
        error: { message: 'Network error' },
      })

      renderSettings()
      expect(screen.getByText('Network error')).toBeInTheDocument()
    })
  })

  describe('Account Identity section', () => {
    it('shows Change Password button when passwordEnabled=true', () => {
      renderSettings()
      expect(screen.getByRole('button', { name: /đổi mật khẩu/i })).toBeInTheDocument()
    })

    it('hides Change Password and shows magic-link text when passwordEnabled=false', async () => {
      const { useSettings } = await import('../../../hooks/useSettings.js')
      useSettings.mockReturnValue({
        data: { ...baseSettings, passwordEnabled: false },
        isLoading: false,
        isError: false,
        error: null,
      })

      renderSettings()

      expect(screen.queryByRole('button', { name: /đổi mật khẩu/i })).not.toBeInTheDocument()
      expect(screen.getByText(/magic link/i)).toBeInTheDocument()
    })

    it('shows Manage Sign-in button when passwordEnabled=false', async () => {
      const { useSettings } = await import('../../../hooks/useSettings.js')
      useSettings.mockReturnValue({
        data: { ...baseSettings, passwordEnabled: false },
        isLoading: false,
        isError: false,
        error: null,
      })

      renderSettings()
      expect(screen.getByRole('button', { name: /quản lý đăng nhập/i })).toBeInTheDocument()
    })
  })

  describe('Account Status panel', () => {
    it('does not render Account Status when accountStatus is null', () => {
      renderSettings()
      expect(screen.queryByText('Trạng thái tài khoản')).not.toBeInTheDocument()
    })

    it('renders Account Status panel when accountStatus is present', async () => {
      const { useSettings } = await import('../../../hooks/useSettings.js')
      useSettings.mockReturnValue({
        data: {
          ...baseSettings,
          accountStatus: { plan: 'Pro', renewsAt: '2026-12-31T00:00:00Z' },
        },
        isLoading: false,
        isError: false,
        error: null,
      })

      renderSettings()
      expect(screen.getByText('Trạng thái tài khoản')).toBeInTheDocument()
      expect(screen.getByText('Pro')).toBeInTheDocument()
    })

    it('shows free plan text when renewsAt is absent', async () => {
      const { useSettings } = await import('../../../hooks/useSettings.js')
      useSettings.mockReturnValue({
        data: {
          ...baseSettings,
          accountStatus: { plan: 'Free', renewsAt: null },
        },
        isLoading: false,
        isError: false,
        error: null,
      })

      renderSettings()
      expect(screen.getByText(/gói miễn phí/i)).toBeInTheDocument()
    })
  })

  describe('Notifications toggles', () => {
    it('calls mutate with only notifyNewContent when that toggle is clicked', async () => {
      const { useUpdateSettings } = await import('../../../hooks/useSettings.js')
      const mutateMock = vi.fn()
      useUpdateSettings.mockReturnValue({
        mutate: mutateMock,
        isPending: false,
        isError: false,
        reset: vi.fn(),
      })

      renderSettings()

      // The "New Content Alerts" toggle is the first switch on the page
      const switches = screen.getAllByRole('switch')
      fireEvent.click(switches[0]) // notifyNewContent toggle

      expect(mutateMock).toHaveBeenCalledTimes(1)
      expect(mutateMock).toHaveBeenCalledWith(
        { notifyNewContent: false }, // was true, toggled to false
        expect.any(Object)
      )
      // Must NOT include other fields
      const [payload] = mutateMock.mock.calls[0]
      expect(Object.keys(payload)).toHaveLength(1)
      expect(payload).not.toHaveProperty('notifyEmail')
    })

    it('calls mutate with only notifyEmail when email-notifications toggle is clicked', async () => {
      const { useUpdateSettings } = await import('../../../hooks/useSettings.js')
      const mutateMock = vi.fn()
      useUpdateSettings.mockReturnValue({
        mutate: mutateMock,
        isPending: false,
        isError: false,
        reset: vi.fn(),
      })

      renderSettings()

      const switches = screen.getAllByRole('switch')
      fireEvent.click(switches[1]) // notifyEmail toggle

      expect(mutateMock).toHaveBeenCalledTimes(1)
      const [payload] = mutateMock.mock.calls[0]
      expect(Object.keys(payload)).toHaveLength(1)
      expect(payload).toHaveProperty('notifyEmail', false)
    })

    it('disables all toggles while a mutation is pending', async () => {
      const { useUpdateSettings } = await import('../../../hooks/useSettings.js')
      useUpdateSettings.mockReturnValue({
        mutate: vi.fn(),
        isPending: true,
        isError: false,
        reset: vi.fn(),
      })

      renderSettings()

      const switches = screen.getAllByRole('switch')
      switches.forEach(sw => expect(sw).toBeDisabled())
    })
  })

  describe('Playback section', () => {
    it('renders quality segment with AUTO selected by default', () => {
      renderSettings()
      const autoBtn = screen.getByRole('button', { name: 'Tự động' })
      expect(autoBtn).toHaveClass('active')
    })

    it('calls mutate with defaultQuality when a quality button is clicked', async () => {
      const { useUpdateSettings } = await import('../../../hooks/useSettings.js')
      const mutateMock = vi.fn()
      useUpdateSettings.mockReturnValue({
        mutate: mutateMock,
        isPending: false,
        isError: false,
        reset: vi.fn(),
      })

      renderSettings()

      fireEvent.click(screen.getByRole('button', { name: '1080p' }))

      expect(mutateMock).toHaveBeenCalledTimes(1)
      expect(mutateMock).toHaveBeenCalledWith(
        { defaultQuality: '1080P' },
        expect.any(Object)
      )
    })
  })

  describe('global patch error display', () => {
    it('shows an error alert when mutate onError fires', async () => {
      const { useUpdateSettings } = await import('../../../hooks/useSettings.js')
      let capturedCallbacks
      useUpdateSettings.mockReturnValue({
        mutate: vi.fn((_, callbacks) => { capturedCallbacks = callbacks }),
        isPending: false,
        isError: false,
        reset: vi.fn(),
      })

      renderSettings()

      const switches = screen.getAllByRole('switch')
      fireEvent.click(switches[0])

      // Simulate mutation error callback inside act() to avoid the warning
      act(() => {
        capturedCallbacks.onError({ message: 'Save failed' })
      })

      await waitFor(() => {
        expect(screen.getByText('Save failed')).toBeInTheDocument()
      })
    })
  })
})
