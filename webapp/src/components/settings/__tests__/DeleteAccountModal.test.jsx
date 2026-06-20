import React from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import DeleteAccountModal from '../DeleteAccountModal.jsx'

// ── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('../../../hooks/useSettings.js', () => ({
  useDeleteAccount: vi.fn(),
}))

vi.mock('../../../hooks/useAuth.js', () => ({
  useAuth: vi.fn(),
}))

// ── Helpers ──────────────────────────────────────────────────────────────────

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

const defaultProps = {
  show: true,
  onHide: vi.fn(),
}

function renderModal(props = {}) {
  return render(<DeleteAccountModal {...defaultProps} {...props} />, { wrapper: Wrapper })
}

// ── Setup ────────────────────────────────────────────────────────────────────

beforeEach(async () => {
  vi.resetAllMocks()

  const { useAuth } = await import('../../../hooks/useAuth.js')
  useAuth.mockReturnValue({
    user: { email: 'user@example.com' },
    logout: vi.fn(),
  })

  const { useDeleteAccount } = await import('../../../hooks/useSettings.js')
  useDeleteAccount.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
    reset: vi.fn(),
  })
})

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('DeleteAccountModal', () => {
  describe('visibility', () => {
    it('renders nothing when show=false', async () => {
      const { useDeleteAccount } = await import('../../../hooks/useSettings.js')
      useDeleteAccount.mockReturnValue({
        mutate: vi.fn(),
        isPending: false,
        isError: false,
        error: null,
        reset: vi.fn(),
      })

      const { container } = renderModal({ show: false, settings: { mfaEnabled: false } })
      expect(container.firstChild).toBeNull()
    })

    it('renders the modal when show=true', () => {
      renderModal({ settings: { mfaEnabled: false } })
      expect(screen.getByText('Delete Account')).toBeInTheDocument()
    })
  })

  describe('mfaEnabled=true', () => {
    it('renders OTP input when mfaEnabled=true', () => {
      renderModal({ settings: { mfaEnabled: true } })
      expect(screen.getByPlaceholderText('000000')).toBeInTheDocument()
      expect(screen.getByText(/6-digit authenticator code/i)).toBeInTheDocument()
    })

    it('shows validation error when OTP is incomplete', async () => {
      renderModal({ settings: { mfaEnabled: true } })

      // Leave OTP blank, click confirm
      fireEvent.click(screen.getByRole('button', { name: /delete my account/i }))

      await waitFor(() => {
        expect(screen.getByText(/valid 6-digit otp/i)).toBeInTheDocument()
      })
    })

    it('calls deleteAccount.mutate with { otp } when valid 6-digit OTP entered', async () => {
      const { useDeleteAccount } = await import('../../../hooks/useSettings.js')
      const mutateMock = vi.fn()
      useDeleteAccount.mockReturnValue({
        mutate: mutateMock,
        isPending: false,
        isError: false,
        error: null,
        reset: vi.fn(),
      })

      renderModal({ settings: { mfaEnabled: true } })

      const otpInput = screen.getByPlaceholderText('000000')
      fireEvent.change(otpInput, { target: { value: '123456' } })
      fireEvent.click(screen.getByRole('button', { name: /delete my account/i }))

      expect(mutateMock).toHaveBeenCalledTimes(1)
      expect(mutateMock).toHaveBeenCalledWith(
        { otp: '123456' },
        expect.any(Object)
      )
      // Must not include confirmation field
      const [payload] = mutateMock.mock.calls[0]
      expect(payload).not.toHaveProperty('confirmation')
    })

    it('calls logout() and redirects to / on success', async () => {
      const { useAuth } = await import('../../../hooks/useAuth.js')
      const logoutMock = vi.fn()
      useAuth.mockReturnValue({ user: { email: 'user@example.com' }, logout: logoutMock })

      const { useDeleteAccount } = await import('../../../hooks/useSettings.js')
      let capturedCallbacks
      useDeleteAccount.mockReturnValue({
        mutate: vi.fn((_, callbacks) => { capturedCallbacks = callbacks }),
        isPending: false,
        isError: false,
        error: null,
        reset: vi.fn(),
      })

      // Spy on window.location.href assignment
      const originalLocation = window.location
      delete window.location
      window.location = { href: '' }

      renderModal({ settings: { mfaEnabled: true } })

      fireEvent.change(screen.getByPlaceholderText('000000'), { target: { value: '654321' } })
      fireEvent.click(screen.getByRole('button', { name: /delete my account/i }))

      act(() => {
        capturedCallbacks.onSuccess()
      })

      await waitFor(() => {
        expect(logoutMock).toHaveBeenCalledTimes(1)
        expect(window.location.href).toBe('/')
      })

      // Restore
      window.location = originalLocation
    })
  })

  describe('mfaEnabled=false', () => {
    it('renders email-confirmation input when mfaEnabled=false', () => {
      renderModal({ settings: { mfaEnabled: false } })
      expect(screen.getByPlaceholderText('user@example.com')).toBeInTheDocument()
      expect(screen.getByText(/type your email address to confirm/i)).toBeInTheDocument()
    })

    it('shows validation error when confirmation email does not match', async () => {
      renderModal({ settings: { mfaEnabled: false } })

      const emailInput = screen.getByPlaceholderText('user@example.com')
      fireEvent.change(emailInput, { target: { value: 'wrong@example.com' } })
      fireEvent.click(screen.getByRole('button', { name: /delete my account/i }))

      await waitFor(() => {
        expect(screen.getByText(/does not match/i)).toBeInTheDocument()
      })
    })

    it('calls deleteAccount.mutate with { confirmation } when email matches', async () => {
      const { useDeleteAccount } = await import('../../../hooks/useSettings.js')
      const mutateMock = vi.fn()
      useDeleteAccount.mockReturnValue({
        mutate: mutateMock,
        isPending: false,
        isError: false,
        error: null,
        reset: vi.fn(),
      })

      renderModal({ settings: { mfaEnabled: false } })

      const emailInput = screen.getByPlaceholderText('user@example.com')
      fireEvent.change(emailInput, { target: { value: 'user@example.com' } })
      fireEvent.click(screen.getByRole('button', { name: /delete my account/i }))

      expect(mutateMock).toHaveBeenCalledTimes(1)
      expect(mutateMock).toHaveBeenCalledWith(
        { confirmation: 'user@example.com' },
        expect.any(Object)
      )
      const [payload] = mutateMock.mock.calls[0]
      expect(payload).not.toHaveProperty('otp')
    })

    it('calls logout() and redirects to / on success', async () => {
      const { useAuth } = await import('../../../hooks/useAuth.js')
      const logoutMock = vi.fn()
      useAuth.mockReturnValue({ user: { email: 'user@example.com' }, logout: logoutMock })

      const { useDeleteAccount } = await import('../../../hooks/useSettings.js')
      let capturedCallbacks
      useDeleteAccount.mockReturnValue({
        mutate: vi.fn((_, callbacks) => { capturedCallbacks = callbacks }),
        isPending: false,
        isError: false,
        error: null,
        reset: vi.fn(),
      })

      const originalLocation = window.location
      delete window.location
      window.location = { href: '' }

      renderModal({ settings: { mfaEnabled: false } })

      fireEvent.change(screen.getByPlaceholderText('user@example.com'), {
        target: { value: 'user@example.com' },
      })
      fireEvent.click(screen.getByRole('button', { name: /delete my account/i }))

      act(() => {
        capturedCallbacks.onSuccess()
      })

      await waitFor(() => {
        expect(logoutMock).toHaveBeenCalledTimes(1)
        expect(window.location.href).toBe('/')
      })

      window.location = originalLocation
    })
  })

  describe('error handling', () => {
    it('shows an error alert when the mutation rejects with a message', async () => {
      const { useDeleteAccount } = await import('../../../hooks/useSettings.js')
      let capturedCallbacks
      useDeleteAccount.mockReturnValue({
        mutate: vi.fn((_, callbacks) => { capturedCallbacks = callbacks }),
        isPending: false,
        isError: false,
        error: null,
        reset: vi.fn(),
      })

      renderModal({ settings: { mfaEnabled: false } })

      fireEvent.change(screen.getByPlaceholderText('user@example.com'), {
        target: { value: 'user@example.com' },
      })
      fireEvent.click(screen.getByRole('button', { name: /delete my account/i }))

      act(() => {
        capturedCallbacks.onError({ message: 'Account deletion failed' })
      })

      await waitFor(() => {
        expect(screen.getByText('Account deletion failed')).toBeInTheDocument()
      })
    })

    it('shows a fallback error message when err.message is absent', async () => {
      const { useDeleteAccount } = await import('../../../hooks/useSettings.js')
      let capturedCallbacks
      useDeleteAccount.mockReturnValue({
        mutate: vi.fn((_, callbacks) => { capturedCallbacks = callbacks }),
        isPending: false,
        isError: false,
        error: null,
        reset: vi.fn(),
      })

      renderModal({ settings: { mfaEnabled: false } })

      fireEvent.change(screen.getByPlaceholderText('user@example.com'), {
        target: { value: 'user@example.com' },
      })
      fireEvent.click(screen.getByRole('button', { name: /delete my account/i }))

      act(() => {
        capturedCallbacks.onError({})
      })

      await waitFor(() => {
        expect(screen.getByText(/failed to delete account/i)).toBeInTheDocument()
      })
    })

    it('shows isError alert from mutation state without needing to trigger onError', async () => {
      const { useDeleteAccount } = await import('../../../hooks/useSettings.js')
      useDeleteAccount.mockReturnValue({
        mutate: vi.fn(),
        isPending: false,
        isError: true,
        error: { message: 'Server error from mutation state' },
        reset: vi.fn(),
      })

      renderModal({ settings: { mfaEnabled: false } })

      expect(screen.getByText('Server error from mutation state')).toBeInTheDocument()
    })
  })

  describe('Cancel / close behaviour', () => {
    it('calls onHide when Cancel is clicked', async () => {
      const { useDeleteAccount } = await import('../../../hooks/useSettings.js')
      useDeleteAccount.mockReturnValue({
        mutate: vi.fn(),
        isPending: false,
        isError: false,
        error: null,
        reset: vi.fn(),
      })

      const onHide = vi.fn()
      renderModal({ settings: { mfaEnabled: false }, onHide })

      fireEvent.click(screen.getByRole('button', { name: /cancel/i }))
      expect(onHide).toHaveBeenCalledTimes(1)
    })

    it('disables buttons while deletion is pending', async () => {
      const { useDeleteAccount } = await import('../../../hooks/useSettings.js')
      useDeleteAccount.mockReturnValue({
        mutate: vi.fn(),
        isPending: true,
        isError: false,
        error: null,
        reset: vi.fn(),
      })

      renderModal({ settings: { mfaEnabled: false } })

      expect(screen.getByRole('button', { name: /cancel/i })).toBeDisabled()
      expect(screen.getByRole('button', { name: /deleting/i })).toBeDisabled()
    })
  })
})
