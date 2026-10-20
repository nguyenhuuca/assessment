import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useReports, useCreateReport, useRetryReport } from '../useReports.js'
import * as reportsApi from '../../api/reports.js'

vi.mock('../../api/reports.js', () => ({
  createReport: vi.fn(), listReports: vi.fn(), retryReport: vi.fn(),
}))

function makeWrapper() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>
  return { client, wrapper }
}

beforeEach(() => vi.clearAllMocks())

describe('useReports', () => {
  it('reads the page under the admin reports key', async () => {
    reportsApi.listReports.mockResolvedValue({ data: { content: [{ id: 'a' }] } })
    const { client, wrapper } = makeWrapper()
    const { result } = renderHook(() => useReports(2), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(reportsApi.listReports).toHaveBeenCalledWith({ page: 2, size: 20 })
    expect(client.getQueryData(['admin', 'reports', 2])).toEqual({ data: { content: [{ id: 'a' }] } })
  })
})

describe('report mutations', () => {
  it('useCreateReport posts the body and invalidates the list', async () => {
    reportsApi.createReport.mockResolvedValue({ data: {} })
    const { client, wrapper } = makeWrapper()
    const invalidate = vi.spyOn(client, 'invalidateQueries')
    const { result } = renderHook(() => useCreateReport(), { wrapper })

    await act(() => result.current.mutateAsync({ type: 'BUG' }))

    expect(reportsApi.createReport).toHaveBeenCalledWith({ type: 'BUG' })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['admin', 'reports'] })
  })

  it('useRetryReport retries by id and invalidates the list', async () => {
    reportsApi.retryReport.mockResolvedValue({ data: {} })
    const { client, wrapper } = makeWrapper()
    const invalidate = vi.spyOn(client, 'invalidateQueries')
    const { result } = renderHook(() => useRetryReport(), { wrapper })

    await act(() => result.current.mutateAsync('r1'))

    expect(reportsApi.retryReport).toHaveBeenCalledWith('r1')
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['admin', 'reports'] })
  })
})
