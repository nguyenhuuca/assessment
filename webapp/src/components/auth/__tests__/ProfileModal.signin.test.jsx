import React from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ProfileModal from '../ProfileModal.jsx'
import { authApi } from '../../../api/auth.js'

vi.mock('../../../api/auth.js', () => ({
  authApi: {
    setPassword: vi.fn(),
    removePassword: vi.fn(),
    mfa: { setup: vi.fn(), enable: vi.fn(), disable: vi.fn() },
  },
}))

const login = vi.fn()
const updateUser = vi.fn()
let mockUser
let mockSettings
vi.mock('../../../hooks/useAuth.js', () => ({
  useAuth: () => ({ user: mockUser, login, updateUser }),
}))
vi.mock('../../../hooks/useSettings.js', () => ({
  useSettings: () => ({ data: mockSettings }),
}))

function renderModal() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ProfileModal show onHide={() => {}} initialTab="signin" />
    </QueryClientProvider>,
  )
}

const type = (label, value) => fireEvent.change(screen.getByLabelText(label), { target: { value } })

beforeEach(() => {
  vi.clearAllMocks()
  mockUser = { email: 'alice@example.com', mfaEnabled: false }
  mockSettings = { passwordEnabled: false, mfaEnabled: false, passwordLoginAvailable: true }
})

describe('ProfileModal sign-in tab: set password', () => {
  it('opens on the sign-in tab and shows the set form without current password', () => {
    renderModal()
    expect(screen.getByRole('heading', { name: 'Đặt mật khẩu' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Mật khẩu hiện tại')).not.toBeInTheDocument()
    expect(screen.queryByText('Gỡ mật khẩu')).not.toBeInTheDocument()
  })

  it('blocks a mismatched confirmation client-side', () => {
    renderModal()
    type('Mật khẩu mới', 'correct-horse-battery')
    type('Nhập lại mật khẩu mới', 'different-password')
    fireEvent.click(screen.getByRole('button', { name: 'Đặt mật khẩu' }))
    expect(screen.getByText('Mật khẩu xác nhận không khớp')).toBeInTheDocument()
    expect(authApi.setPassword).not.toHaveBeenCalled()
  })

  it('stores the new JWT with passwordEnabled and shows the success message', async () => {
    authApi.setPassword.mockResolvedValue({ data: { jwt: 'new-jwt' } })
    renderModal()
    type('Mật khẩu mới', 'correct-horse-battery')
    type('Nhập lại mật khẩu mới', 'correct-horse-battery')
    fireEvent.click(screen.getByRole('button', { name: 'Đặt mật khẩu' }))
    await waitFor(() => expect(login).toHaveBeenCalledWith('new-jwt', { ...mockUser, passwordEnabled: true }))
    expect(authApi.setPassword).toHaveBeenCalledWith({ newPassword: 'correct-horse-battery' })
    expect(screen.getByText('Đã cập nhật mật khẩu. Các thiết bị khác đã được đăng xuất.')).toBeInTheDocument()
  })

  it('maps server error codes to Vietnamese messages', async () => {
    authApi.setPassword.mockRejectedValue({ message: 'PASSWORD_TOO_COMMON', status: 400 })
    renderModal()
    type('Mật khẩu mới', 'password123')
    type('Nhập lại mật khẩu mới', 'password123')
    fireEvent.click(screen.getByRole('button', { name: 'Đặt mật khẩu' }))
    expect(await screen.findByText('Mật khẩu quá phổ biến, vui lòng chọn mật khẩu khác')).toBeInTheDocument()
    expect(login).not.toHaveBeenCalled()
  })

  it('shows live hints including the email local part rule', () => {
    renderModal()
    type('Mật khẩu mới', 'alice-1234')
    expect(screen.getByText(/Không chứa phần tên của email/).textContent).toMatch(/^•/)
    type('Mật khẩu mới', 'completely-different')
    expect(screen.getByText(/Không chứa phần tên của email/).textContent).toMatch(/^✓/)
  })

  it('requires an OTP when MFA is enabled and sends it', async () => {
    mockUser = { ...mockUser, mfaEnabled: true }
    mockSettings = { ...mockSettings, mfaEnabled: true }
    authApi.setPassword.mockResolvedValue({ data: { jwt: 'j2' } })
    renderModal()
    type('Mật khẩu mới', 'correct-horse-battery')
    type('Nhập lại mật khẩu mới', 'correct-horse-battery')
    fireEvent.click(screen.getByRole('button', { name: 'Đặt mật khẩu' }))
    expect(screen.getByText('Vui lòng nhập mã xác thực gồm 6 chữ số')).toBeInTheDocument()
    expect(authApi.setPassword).not.toHaveBeenCalled()

    type('Mã xác thực MFA (6 chữ số)', '123456')
    fireEvent.click(screen.getByRole('button', { name: 'Đặt mật khẩu' }))
    await waitFor(() => expect(authApi.setPassword).toHaveBeenCalledWith({
      newPassword: 'correct-horse-battery', otp: '123456',
    }))
  })

  it('does not render an OTP field without MFA', () => {
    renderModal()
    expect(screen.queryByLabelText('Mã xác thực MFA (6 chữ số)')).not.toBeInTheDocument()
  })
})

describe('ProfileModal sign-in tab: change and remove', () => {
  beforeEach(() => {
    mockUser = { email: 'alice@example.com', mfaEnabled: false, passwordEnabled: true }
    mockSettings = { passwordEnabled: true, mfaEnabled: false, passwordLoginAvailable: true }
  })

  it('changes the password sending currentPassword', async () => {
    authApi.setPassword.mockResolvedValue({ data: { jwt: 'jwt-changed' } })
    renderModal()
    expect(screen.getByRole('heading', { name: 'Đổi mật khẩu' })).toBeInTheDocument()
    type('Mật khẩu hiện tại', 'old-password-1')
    type('Mật khẩu mới', 'new-password-12')
    type('Nhập lại mật khẩu mới', 'new-password-12')
    fireEvent.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }))
    await waitFor(() => expect(authApi.setPassword).toHaveBeenCalledWith({
      currentPassword: 'old-password-1', newPassword: 'new-password-12',
    }))
    expect(login).toHaveBeenCalledWith('jwt-changed', expect.objectContaining({ passwordEnabled: true }))
  })

  it('maps CURRENT_PASSWORD_INVALID', async () => {
    authApi.setPassword.mockRejectedValue({ message: 'CURRENT_PASSWORD_INVALID', status: 400 })
    renderModal()
    type('Mật khẩu hiện tại', 'bad')
    type('Mật khẩu mới', 'new-password-12')
    type('Nhập lại mật khẩu mới', 'new-password-12')
    fireEvent.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }))
    expect(await screen.findByText('Mật khẩu hiện tại không đúng')).toBeInTheDocument()
  })

  it('removes the password and marks the user passwordEnabled false', async () => {
    authApi.removePassword.mockResolvedValue(null)
    renderModal()
    type('Mật khẩu hiện tại để gỡ', 'old-password-1')
    fireEvent.click(screen.getByRole('button', { name: 'Gỡ mật khẩu' }))
    await waitFor(() => expect(authApi.removePassword).toHaveBeenCalledWith({ currentPassword: 'old-password-1' }))
    expect(updateUser).toHaveBeenCalledWith({ ...mockUser, passwordEnabled: false })
  })

  it('stores the fresh JWT returned on removal (old tokens are revoked)', async () => {
    authApi.removePassword.mockResolvedValue({ data: { jwt: 'fresh-jwt', action: 'PASSWORD_REMOVED' } })
    renderModal()
    type('Mật khẩu hiện tại để gỡ', 'old-password-1')
    fireEvent.click(screen.getByRole('button', { name: 'Gỡ mật khẩu' }))
    await waitFor(() => expect(login).toHaveBeenCalledWith('fresh-jwt', { ...mockUser, passwordEnabled: false }))
    expect(updateUser).not.toHaveBeenCalled()
  })

  it('requires OTP for removal when MFA is enabled', async () => {
    mockSettings = { ...mockSettings, mfaEnabled: true }
    authApi.removePassword.mockResolvedValue(null)
    renderModal()
    type('Mật khẩu hiện tại để gỡ', 'old-password-1')
    fireEvent.click(screen.getByRole('button', { name: 'Gỡ mật khẩu' }))
    expect(authApi.removePassword).not.toHaveBeenCalled()

    const otpFields = screen.getAllByLabelText('Mã xác thực MFA (6 chữ số)')
    fireEvent.change(otpFields[1], { target: { value: '654321' } })
    fireEvent.click(screen.getByRole('button', { name: 'Gỡ mật khẩu' }))
    await waitFor(() => expect(authApi.removePassword).toHaveBeenCalledWith({
      currentPassword: 'old-password-1', otp: '654321',
    }))
  })
})
