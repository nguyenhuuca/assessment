import React from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import i18n from '../../i18n/index.js'
import { useLocaleSync } from '../useLocaleSync.js'
import { settingsApi } from '../../api/settings.js'

vi.mock('../../api/settings.js', () => ({
  settingsApi: { get: vi.fn(), patch: vi.fn() },
}))

let mockLoggedIn = true
vi.mock('../useAuth.js', () => ({
  useAuth: () => ({ isLoggedIn: mockLoggedIn }),
}))

function setup() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const wrapper = ({ children }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>
  return renderHook(() => useLocaleSync(), { wrapper })
}

beforeEach(() => {
  vi.clearAllMocks()
  mockLoggedIn = true
  settingsApi.patch.mockImplementation(async (body) => ({ data: { locale: body.locale } }))
})

describe('useLocaleSync', () => {
  it('applies the server locale after login without PATCHing back', async () => {
    settingsApi.get.mockResolvedValue({ data: { locale: 'en' } })
    setup()
    await waitFor(() => expect(i18n.language).toBe('en'))
    await act(async () => {})
    expect(settingsApi.patch).not.toHaveBeenCalled()
  })

  it('keeps the local language when the server has none', async () => {
    settingsApi.get.mockResolvedValue({ data: { locale: null } })
    setup()
    await waitFor(() => expect(settingsApi.get).toHaveBeenCalled())
    await act(async () => {})
    expect(i18n.language).toBe('vi')
    expect(settingsApi.patch).not.toHaveBeenCalled()
  })

  it('PATCHes exactly once when the user switches language', async () => {
    settingsApi.get.mockResolvedValue({ data: { locale: 'vi' } })
    setup()
    await waitFor(() => expect(settingsApi.get).toHaveBeenCalled())
    await act(async () => {})
    await act(async () => { await i18n.changeLanguage('en') })
    await waitFor(() => expect(settingsApi.patch).toHaveBeenCalledTimes(1))
    expect(settingsApi.patch.mock.calls[0][0]).toEqual({ locale: 'en' })
    await act(async () => {})
    expect(settingsApi.patch).toHaveBeenCalledTimes(1)
    expect(i18n.language).toBe('en')
  })

  it('does not fetch or PATCH when logged out', async () => {
    mockLoggedIn = false
    setup()
    await act(async () => { await i18n.changeLanguage('en') })
    expect(settingsApi.get).not.toHaveBeenCalled()
    expect(settingsApi.patch).not.toHaveBeenCalled()
  })
})
