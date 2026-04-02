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
