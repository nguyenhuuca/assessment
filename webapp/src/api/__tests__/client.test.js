import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

// Mock fetch globally
const mockFetch = vi.fn()
global.fetch = mockFetch

// Reset localStorage before each test
beforeEach(() => {
  localStorage.clear()
  mockFetch.mockReset()
})

describe('API client', () => {
  it('injects Authorization header when JWT present', async () => {
    localStorage.setItem('jwt', 'test-jwt-token')
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ data: {} }),
    })

    const { api } = await import('../client.js')
    await api.get('/test')

    const [, options] = mockFetch.mock.calls[0]
    expect(options.headers['Authorization']).toBe('test-jwt-token')
  })

  it('injects X-Guest-Token header when guestToken present', async () => {
    localStorage.setItem('guestToken', 'guest-abc')
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ data: {} }),
    })

    const { api } = await import('../client.js')
    await api.get('/test')

    const [, options] = mockFetch.mock.calls[0]
    expect(options.headers['X-Guest-Token']).toBe('guest-abc')
  })

  it('throws with message and status on non-OK response', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: false,
      status: 401,
      json: async () => ({ error: { message: 'Unauthorized' } }),
    })

    const { api } = await import('../client.js')
    await expect(api.get('/test')).rejects.toMatchObject({ message: 'Unauthorized', status: 401 })
  })
})

describe('API client TOKEN_REVOKED handling', () => {
  it('clears the session and dispatches auth:revoked on 401 TOKEN_REVOKED', async () => {
    localStorage.setItem('jwt', 'old')
    localStorage.setItem('user', '{"email":"a@b.com"}')
    mockFetch.mockResolvedValueOnce({
      ok: false, status: 401,
      json: async () => ({ status: 'FAILED', error: { status: 401, message: 'TOKEN_REVOKED' } }),
    })
    const handler = vi.fn()
    window.addEventListener('auth:revoked', handler)

    const { api } = await import('../client.js')
    await expect(api.get('/user/me')).rejects.toMatchObject({ message: 'TOKEN_REVOKED', status: 401 })

    window.removeEventListener('auth:revoked', handler)
    expect(handler).toHaveBeenCalledTimes(1)
    expect(localStorage.getItem('jwt')).toBeNull()
    expect(localStorage.getItem('user')).toBeNull()
  })

  it('leaves the session alone for other 401s', async () => {
    localStorage.setItem('jwt', 'keep')
    mockFetch.mockResolvedValueOnce({
      ok: false, status: 401,
      json: async () => ({ error: { message: 'INVALID_CREDENTIALS' } }),
    })
    const handler = vi.fn()
    window.addEventListener('auth:revoked', handler)

    const { api } = await import('../client.js')
    await expect(api.post('/user/login', {})).rejects.toMatchObject({ status: 401 })

    window.removeEventListener('auth:revoked', handler)
    expect(handler).not.toHaveBeenCalled()
    expect(localStorage.getItem('jwt')).toBe('keep')
  })
})

describe('API client 204 handling', () => {
  it('returns null for 204 No Content without parsing the body', async () => {
    const json = vi.fn().mockRejectedValue(new SyntaxError('Unexpected end of JSON input'))
    mockFetch.mockResolvedValueOnce({ ok: true, status: 204, json })

    const { api } = await import('../client.js')
    await expect(api.patch('/notifications/read-all')).resolves.toBeNull()
    expect(json).not.toHaveBeenCalled()
  })
})
