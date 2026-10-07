import React from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
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

async function openPasswordTab() {
  fireEvent.click(await screen.findByRole('tab', { name: 'Mật khẩu' }))
}

function fillAndSubmit(email, password) {
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: email } })
  fireEvent.change(screen.getByLabelText('Mật khẩu'), { target: { value: password } })
  fireEvent.click(screen.getByRole('button', { name: 'Login' }))
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  authApi.authOptions.mockResolvedValue({ data: { passwordLoginAvailable: true } })
})

describe('LoginForm tabs', () => {
  it('shows both tabs when password login is available', async () => {
    renderForm()
    expect(await screen.findByRole('tab', { name: 'Link đăng nhập' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Mật khẩu' })).toBeInTheDocument()
  })

  it('hides tabs when password login is unavailable', async () => {
    authApi.authOptions.mockResolvedValue({ data: { passwordLoginAvailable: false } })
    renderForm()
    await waitFor(() => expect(authApi.authOptions).toHaveBeenCalled())
    expect(screen.queryByRole('tab')).not.toBeInTheDocument()
  })

  it('hides tabs when auth-options fails', async () => {
    authApi.authOptions.mockRejectedValue({ message: 'boom', status: 500 })
    renderForm()
    await waitFor(() => expect(authApi.authOptions).toHaveBeenCalled())
    expect(screen.queryByRole('tab')).not.toBeInTheDocument()
  })

  it('shows helper text on the password tab', async () => {
    renderForm()
    await openPasswordTab()
    expect(screen.getByText(/Chưa có mật khẩu\? Đăng nhập bằng link/)).toBeInTheDocument()
  })
})

describe('LoginForm password login', () => {
  it('logs in with email and password', async () => {
    authApi.login.mockResolvedValue({ data: { jwt: 'jwt-1', user: { email: 'a@b.com' } } })
    renderForm()
    await openPasswordTab()
    fillAndSubmit('a@b.com', 'hunter2hunter2')
    await waitFor(() => expect(login).toHaveBeenCalledWith('jwt-1', { email: 'a@b.com' }))
    expect(authApi.login).toHaveBeenCalledWith('a@b.com', 'hunter2hunter2')
  })

  it('routes MFA_REQUIRED through onMfaRequired', async () => {
    const data = { action: 'MFA_REQUIRED', sessionToken: 's1', user: { email: 'a@b.com' } }
    authApi.login.mockResolvedValue({ data })
    const onMfaRequired = vi.fn()
    renderForm({ onMfaRequired })
    await openPasswordTab()
    fillAndSubmit('a@b.com', 'pw')
    await waitFor(() => expect(onMfaRequired).toHaveBeenCalledWith(data))
    expect(login).not.toHaveBeenCalled()
    expect(JSON.parse(localStorage.getItem('user'))).toEqual({ email: 'a@b.com' })
  })

  it('shows a generic message on 401', async () => {
    authApi.login.mockRejectedValue({ message: 'INVALID_CREDENTIALS', status: 401 })
    renderForm()
    await openPasswordTab()
    fillAndSubmit('a@b.com', 'wrong')
    expect(await screen.findByText(/Email hoặc mật khẩu không đúng/)).toBeInTheDocument()
    expect(screen.queryByText(/INVALID_CREDENTIALS/)).not.toBeInTheDocument()
    expect(login).not.toHaveBeenCalled()
  })

  it('shows a rate limit message on 429', async () => {
    authApi.login.mockRejectedValue({ message: 'Too many', status: 429 })
    renderForm()
    await openPasswordTab()
    fillAndSubmit('a@b.com', 'x')
    expect(await screen.findByText(/Bạn thử quá nhiều lần, vui lòng đợi/)).toBeInTheDocument()
  })

  it('forgot link switches to the link tab keeping the email', async () => {
    renderForm()
    await openPasswordTab()
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'a@b.com' } })
    fireEvent.click(screen.getByRole('button', { name: /Quên mật khẩu\? Gửi link đăng nhập/ }))
    expect(screen.getByRole('tab', { name: 'Link đăng nhập' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByLabelText('Email')).toHaveValue('a@b.com')
    expect(screen.queryByLabelText('Mật khẩu')).not.toBeInTheDocument()
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
