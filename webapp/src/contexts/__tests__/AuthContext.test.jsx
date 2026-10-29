import React from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, act, waitFor } from '@testing-library/react'
import { AuthProvider } from '../AuthContext.jsx'
import { useAuth } from '../../hooks/useAuth.js'
import { userCanReport } from '../../utils/roles.js'

// Mock authApi
vi.mock('../../api/auth.js', () => ({
  authApi: {
    me: vi.fn(),
  },
}))

function TestConsumer() {
  const auth = useAuth()
  return (
    <div>
      <span data-testid="logged">{String(auth.isLoggedIn)}</span>
      <span data-testid="email">{auth.user?.email || ''}</span>
      <span data-testid="admin">{String(!!auth.user?.isAdmin)}</span>
      <button onClick={() => auth.login('jwt123', { email: 'test@test.com' })}>login</button>
      <button onClick={() => auth.login('jwt-admin', { email: 'a@test.com', role: 'ADMIN' })}>login-admin</button>
      <button onClick={() => auth.login('jwt-super', { email: 's@test.com', role: 'SUPER_ADMIN' })}>login-super</button>
      <button onClick={auth.logout}>logout</button>
    </div>
  )
}

beforeEach(() => {
  localStorage.clear()
  vi.resetAllMocks()
})

describe('AuthContext', () => {
  it('starts logged out when no JWT in localStorage', async () => {
    const { authApi } = await import('../../api/auth.js')
    authApi.me.mockResolvedValue({})

    render(<AuthProvider><TestConsumer /></AuthProvider>)
    expect(screen.getByTestId('logged').textContent).toBe('false')
  })

  it('login stores JWT and updates isLoggedIn', async () => {
    const { authApi } = await import('../../api/auth.js')
    authApi.me.mockResolvedValue({})

    render(<AuthProvider><TestConsumer /></AuthProvider>)
    act(() => screen.getByText('login').click())
    await waitFor(() => {
      expect(screen.getByTestId('logged').textContent).toBe('true')
      expect(screen.getByTestId('email').textContent).toBe('test@test.com')
    })
    expect(localStorage.getItem('jwt')).toBe('jwt123')
  })

  it('login with role ADMIN shows admin immediately without calling /user/me', async () => {
    const { authApi } = await import('../../api/auth.js')
    render(<AuthProvider><TestConsumer /></AuthProvider>)
    act(() => screen.getByText('login-admin').click())
    expect(screen.getByTestId('admin').textContent).toBe('true')
    expect(authApi.me).not.toHaveBeenCalled()
  })

  it('SUPER_ADMIN counts as admin', async () => {
    render(<AuthProvider><TestConsumer /></AuthProvider>)
    act(() => screen.getByText('login-super').click())
    expect(screen.getByTestId('admin').textContent).toBe('true')
  })

  it('login without role resolves admin via /user/me', async () => {
    const { authApi } = await import('../../api/auth.js')
    authApi.me.mockResolvedValue({ data: { role: 'ADMIN' } })
    render(<AuthProvider><TestConsumer /></AuthProvider>)
    act(() => screen.getByText('login').click())
    await waitFor(() => expect(screen.getByTestId('admin').textContent).toBe('true'))
    expect(JSON.parse(localStorage.getItem('user')).isAdmin).toBe(true)
  })

  it('keeps permissions from /user/me on reload', async () => {
    localStorage.setItem('jwt', 'jwt-c')
    localStorage.setItem('user', JSON.stringify({ email: 'c@test.com', role: 'USER' }))
    const { authApi } = await import('../../api/auth.js')
    authApi.me.mockResolvedValue({ data: { role: 'CONTRIBUTOR', permissions: 33 } })
    function Probe() {
      const { user } = useAuth()
      return <span data-testid="probe">{`${user?.permissions}|${userCanReport(user)}`}</span>
    }
    render(<AuthProvider><Probe /></AuthProvider>)
    await waitFor(() => expect(screen.getByTestId('probe').textContent).toBe('33|true'))
    expect(JSON.parse(localStorage.getItem('user')).permissions).toBe(33)
  })

  it('keeps permissions from login', async () => {
    function Probe() {
      const { user, login } = useAuth()
      return <>
        <span data-testid="probe">{`${user?.permissions}|${userCanReport(user)}`}</span>
        <button onClick={() => login('j', { email: 'c@test.com', role: 'CONTRIBUTOR', permissions: 32 })}>go</button>
      </>
    }
    render(<AuthProvider><Probe /></AuthProvider>)
    act(() => screen.getByText('go').click())
    expect(screen.getByTestId('probe').textContent).toBe('32|true')
  })

  it('logout clears JWT and resets state', async () => {
    localStorage.setItem('jwt', 'existing-jwt')
    localStorage.setItem('user', JSON.stringify({ email: 'user@test.com' }))
    const { authApi } = await import('../../api/auth.js')
    authApi.me.mockResolvedValue({})

    render(<AuthProvider><TestConsumer /></AuthProvider>)
    await waitFor(() => {})
    act(() => screen.getByText('logout').click())
    await waitFor(() => {
      expect(screen.getByTestId('logged').textContent).toBe('false')
    })
    expect(localStorage.getItem('jwt')).toBeNull()
  })

  it('logs out and sets a notice when auth:revoked fires', async () => {
    localStorage.setItem('jwt', 'jwt-x')
    localStorage.setItem('user', JSON.stringify({ email: 'user@test.com' }))
    const { authApi } = await import('../../api/auth.js')
    authApi.me.mockResolvedValue({})

    function Notice() {
      const { sessionNotice } = useAuth()
      return <span data-testid="notice">{sessionNotice || ''}</span>
    }
    render(<AuthProvider><TestConsumer /><Notice /></AuthProvider>)
    await waitFor(() => expect(screen.getByTestId('logged').textContent).toBe('true'))
    act(() => { window.dispatchEvent(new Event('auth:revoked')) })
    await waitFor(() => expect(screen.getByTestId('logged').textContent).toBe('false'))
    expect(screen.getByTestId('notice').textContent).toBe('Phiên đăng nhập đã hết hiệu lực, vui lòng đăng nhập lại')
  })

  it('clears session on 401 from /user/me', async () => {
    localStorage.setItem('jwt', 'expired-jwt')
    localStorage.setItem('user', JSON.stringify({ email: 'user@test.com' }))
    const { authApi } = await import('../../api/auth.js')
    authApi.me.mockRejectedValue({ status: 401, message: 'Unauthorized' })

    render(<AuthProvider><TestConsumer /></AuthProvider>)
    await waitFor(() => {
      expect(screen.getByTestId('logged').textContent).toBe('false')
    })
    expect(localStorage.getItem('jwt')).toBeNull()
  })
})
