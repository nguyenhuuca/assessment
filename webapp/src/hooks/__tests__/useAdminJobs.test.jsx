import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useAdminJobs, useUpdateJob, useRunJob } from '../useAdmin.js'
import * as adminApi from '../../api/admin.js'

vi.mock('../../api/admin.js', () => ({
  getJobs: vi.fn(), updateJob: vi.fn(), runJob: vi.fn(),
}))

function makeWrapper() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>
  return { client, wrapper }
}

beforeEach(() => vi.clearAllMocks())

describe('useAdminJobs', () => {
  it('reads the job list under the admin jobs key', async () => {
    adminApi.getJobs.mockResolvedValue({ data: [{ name: 'log-stats', running: false }] })
    const { client, wrapper } = makeWrapper()
    const { result } = renderHook(() => useAdminJobs(), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(client.getQueryData(['admin', 'jobs'])).toEqual({ data: [{ name: 'log-stats', running: false }] })
  })
})

describe('job mutations', () => {
  it('useUpdateJob sends name + body and invalidates the job list', async () => {
    adminApi.updateJob.mockResolvedValue({ data: {} })
    const { client, wrapper } = makeWrapper()
    const invalidate = vi.spyOn(client, 'invalidateQueries')
    const { result } = renderHook(() => useUpdateJob(), { wrapper })

    await act(() => result.current.mutateAsync({ name: 'log-stats', body: { enabled: false } }))

    expect(adminApi.updateJob).toHaveBeenCalledWith('log-stats', { enabled: false })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['admin', 'jobs'] })
  })

  it('useRunJob posts the run and invalidates the job list', async () => {
    adminApi.runJob.mockResolvedValue({ data: {} })
    const { client, wrapper } = makeWrapper()
    const invalidate = vi.spyOn(client, 'invalidateQueries')
    const { result } = renderHook(() => useRunJob(), { wrapper })

    await act(() => result.current.mutateAsync('log-stats'))

    expect(adminApi.runJob).toHaveBeenCalledWith('log-stats')
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['admin', 'jobs'] })
  })
})
