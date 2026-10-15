import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { watchHistoryApi } from '../watchHistory.js'

const ok = (status = 200, body = { data: [] }) => ({ status, ok: true, json: async () => body })

beforeEach(() => {
  localStorage.clear()
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ok()))
})
afterEach(() => vi.unstubAllGlobals())

describe('watchHistoryApi', () => {
  it('list builds the query string and omits a blank q', async () => {
    await watchHistoryApi.list('  ', 0, 20)
    expect(fetch.mock.calls[0][0]).toMatch(/\/watch-history\?page=0&size=20$/)
    await watchHistoryApi.list(' cat & dog ', 2, 10)
    expect(fetch.mock.calls[1][0]).toMatch(/\/watch-history\?q=cat\+%26\+dog&page=2&size=10$/)
  })

  it('progress sends PUT with keepalive for a logged-in user', async () => {
    localStorage.setItem('jwt', 'tok')
    fetch.mockResolvedValue(ok(204))
    await watchHistoryApi.progress(12, 30, 90)
    const [url, init] = fetch.mock.calls[0]
    expect(url).toMatch(/\/watch-history\/12\/progress$/)
    expect(init.method).toBe('PUT')
    expect(init.keepalive).toBe(true)
    expect(JSON.parse(init.body)).toEqual({ positionSec: 30, durationSec: 90 })
  })

  it('progress is a no-op for guests', async () => {
    await watchHistoryApi.progress(12, 30, 90)
    expect(fetch).not.toHaveBeenCalled()
  })

  it('other calls do not use keepalive; remove and clear hit the right paths', async () => {
    fetch.mockResolvedValue(ok(204))
    await watchHistoryApi.remove(3)
    await watchHistoryApi.clear()
    expect(fetch.mock.calls[0][0]).toMatch(/\/watch-history\/3$/)
    expect(fetch.mock.calls[0][1].method).toBe('DELETE')
    expect(fetch.mock.calls[0][1].keepalive).toBeUndefined()
    expect(fetch.mock.calls[1][0]).toMatch(/\/watch-history$/)
  })
})
