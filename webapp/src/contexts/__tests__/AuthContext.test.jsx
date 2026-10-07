import React from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, act, waitFor } from '@testing-library/react'
import { AuthProvider, useAuth } from '../AuthContext.jsx'

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
      <button onClick={() => auth.login('jwt123', { email: 'test@test.com' })}>login</button>
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
