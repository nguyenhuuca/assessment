import React from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import LoginForm from '../LoginForm.jsx'
import { authApi } from '../../../api/auth.js'

vi.mock('../../../api/auth.js', () => ({
  authApi: { authOptions: vi.fn(), join: vi.fn(), login: vi.fn() },
}))

const login = vi.fn()
vi.mock('../../../hooks/useAuth.js', () => ({
  useAuth: () => ({ login }),
}))

function renderForm(props = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <LoginForm {...props} />
    </QueryClientProvider>,
  )
}

const OPEN_BTN = 'Đăng nhập bằng mật khẩu'

async function openPasswordModal() {
  fireEvent.click(await screen.findByRole('button', { name: OPEN_BTN }))
}

function fillAndSubmit(email, password) {
  const dialog = screen.getByRole('dialog')
  fireEvent.change(within(dialog).getByLabelText('Email'), { target: { value: email } })
  fireEvent.change(within(dialog).getByLabelText('Mật khẩu'), { target: { value: password } })
  fireEvent.click(within(dialog).getByRole('button', { name: 'Đăng nhập' }))
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  authApi.authOptions.mockResolvedValue({ data: { passwordLoginAvailable: true } })
})

describe('LoginForm password entry point', () => {
  it('shows the password login button when available, without a password field inline', async () => {
    renderForm()
    expect(await screen.findByRole('button', { name: OPEN_BTN })).toBeInTheDocument()
    expect(screen.queryByLabelText('Mật khẩu')).not.toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('hides the button when password login is unavailable', async () => {
    authApi.authOptions.mockResolvedValue({ data: { passwordLoginAvailable: false } })
    renderForm()
    await waitFor(() => expect(authApi.authOptions).toHaveBeenCalled())
    expect(screen.queryByRole('button', { name: OPEN_BTN })).not.toBeInTheDocument()
  })

  it('hides the button when auth-options fails', async () => {
    authApi.authOptions.mockRejectedValue({ message: 'boom', status: 500 })
    renderForm()
    await waitFor(() => expect(authApi.authOptions).toHaveBeenCalled())
    expect(screen.queryByRole('button', { name: OPEN_BTN })).not.toBeInTheDocument()
  })

  it('opens a modal pre-filled with the header email and helper text', async () => {
    renderForm()
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'a@b.com' } })
    await openPasswordModal()
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByLabelText('Email')).toHaveValue('a@b.com')
    expect(within(dialog).getByText(/Chưa có mật khẩu\? Đăng nhập bằng link/)).toBeInTheDocument()
  })

  it('closes the modal on cancel', async () => {
    renderForm()
    await openPasswordModal()
    fireEvent.click(screen.getByRole('button', { name: 'Huỷ' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})

describe('LoginForm password login (modal)', () => {
  it('logs in with email and password and closes the modal', async () => {
    authApi.login.mockResolvedValue({ data: { jwt: 'jwt-1', user: { email: 'a@b.com' } } })
    renderForm()
    await openPasswordModal()
    fillAndSubmit('a@b.com', 'hunter2hunter2')
    await waitFor(() => expect(login).toHaveBeenCalledWith('jwt-1', { email: 'a@b.com' }))
    expect(authApi.login).toHaveBeenCalledWith('a@b.com', 'hunter2hunter2')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('routes MFA_REQUIRED through onMfaRequired', async () => {
    const data = { action: 'MFA_REQUIRED', sessionToken: 's1', user: { email: 'a@b.com' } }
    authApi.login.mockResolvedValue({ data })
    const onMfaRequired = vi.fn()
    renderForm({ onMfaRequired })
    await openPasswordModal()
    fillAndSubmit('a@b.com', 'pw')
    await waitFor(() => expect(onMfaRequired).toHaveBeenCalledWith(data))
    expect(login).not.toHaveBeenCalled()
    expect(JSON.parse(localStorage.getItem('user'))).toEqual({ email: 'a@b.com' })
  })

  it('shows a generic message on 401 and keeps the modal open', async () => {
    authApi.login.mockRejectedValue({ message: 'INVALID_CREDENTIALS', status: 401 })
    renderForm()
    await openPasswordModal()
    fillAndSubmit('a@b.com', 'wrong')
    expect(await screen.findByText(/Email hoặc mật khẩu không đúng/)).toBeInTheDocument()
    expect(screen.queryByText(/INVALID_CREDENTIALS/)).not.toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(login).not.toHaveBeenCalled()
  })

  it('shows a rate limit message on 429', async () => {
    authApi.login.mockRejectedValue({ message: 'Too many', status: 429 })
    renderForm()
    await openPasswordModal()
    fillAndSubmit('a@b.com', 'x')
    expect(await screen.findByText(/Bạn thử quá nhiều lần, vui lòng đợi/)).toBeInTheDocument()
  })

  it('forgot link closes the modal and moves the email to the link form', async () => {
    renderForm()
    await openPasswordModal()
    const dialog = screen.getByRole('dialog')
    fireEvent.change(within(dialog).getByLabelText('Email'), { target: { value: 'a@b.com' } })
    fireEvent.click(within(dialog).getByRole('button', { name: /Quên mật khẩu\? Gửi link đăng nhập/ }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveValue('a@b.com')
    expect(screen.getByText(/Bấm Login để nhận link/)).toBeInTheDocument()
  })
})

describe('LoginForm magic link (unchanged)', () => {
  it('sends the link and confirms', async () => {
    authApi.authOptions.mockResolvedValue({ data: { passwordLoginAvailable: false } })
    authApi.join.mockResolvedValue({ data: { action: 'INVITED_SEND' } })
    renderForm()
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'a@b.com' } })
    fireEvent.click(screen.getByRole('button', { name: 'Login' }))
    expect(await screen.findByText(/Check your inbox!/)).toBeInTheDocument()
    expect(authApi.join).toHaveBeenCalledWith('a@b.com')
  })
})
