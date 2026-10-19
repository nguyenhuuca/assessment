import React from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AdminJobsTab from '../AdminJobsTab.jsx'
import * as adminApi from '../../../api/admin.js'
import { useAuth } from '../../../hooks/useAuth.js'
import i18n from '../../../i18n/index.js'

vi.mock('../../../api/admin.js', () => ({ getJobs: vi.fn(), updateJob: vi.fn(), runJob: vi.fn() }))
vi.mock('../../../hooks/useAuth.js', () => ({ useAuth: vi.fn() }))

const job = (o) => ({ name: 'logStats', description: 'Log stats', cron: '0 0 * * * *', zone: 'UTC', enabled: true,
  nextRunAt: '2026-10-20T01:00:00Z', lastRunAt: '2026-10-19T01:00:00Z', lastResult: 'SUCCESS', running: false, ...o })

let qc
function renderTab() {
  qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={qc}><AdminJobsTab /></QueryClientProvider>)
}
const setRole = (role) => useAuth.mockReturnValue({ user: { role } })

beforeEach(() => {
  vi.clearAllMocks()
  setRole('SUPER_ADMIN')
  adminApi.getJobs.mockResolvedValue({ data: [job(), job({ name: 'cleanup', description: 'Cleanup', zone: null, enabled: false, lastResult: 'FAILED', nextRunAt: null })] })
  adminApi.updateJob.mockResolvedValue({ data: job() })
  adminApi.runJob.mockResolvedValue({})
})

describe('AdminJobsTab', () => {
  it('shows the job columns', async () => {
    setRole('ADMIN')
    renderTab()
    expect(await screen.findByText('logStats')).toBeInTheDocument()
    expect(screen.getByText('Log stats')).toBeInTheDocument()
    expect(screen.getAllByText('0 0 * * * *')).toHaveLength(2)
    expect(screen.getByText('UTC')).toBeInTheDocument()
    expect(screen.getByText('THÀNH CÔNG')).toBeInTheDocument()
    expect(screen.getByText('THẤT BẠI')).toBeInTheDocument()
  })

  it('ADMIN is read-only: no inputs or buttons', async () => {
    setRole('ADMIN')
    renderTab()
    await screen.findByText('logStats')
    expect(screen.queryByRole('textbox')).toBeNull()
    expect(screen.queryByRole('checkbox')).toBeNull()
    expect(screen.queryByRole('button')).toBeNull()
  })

  it('SUPER_ADMIN toggles enabled', async () => {
    renderTab()
    fireEvent.click(await screen.findByLabelText('Bật cleanup'))
    await waitFor(() => expect(adminApi.updateJob).toHaveBeenCalledWith('cleanup', { enabled: true }))
  })

  it('SUPER_ADMIN edits and saves the cron', async () => {
    renderTab()
    const input = await screen.findByLabelText('Cron của logStats')
    const save = input.nextElementSibling
    expect(save).toBeDisabled()
    fireEvent.change(input, { target: { value: ' 0 */5 * * * * ' } })
    fireEvent.click(save)
    await waitFor(() => expect(adminApi.updateJob).toHaveBeenCalledWith('logStats', { cron: '0 */5 * * * *' }))
  })

  it('Run now posts and refreshes the list', async () => {
    renderTab()
    const spy = vi.spyOn(qc, 'invalidateQueries')
    fireEvent.click((await screen.findAllByText('Chạy ngay'))[0])
    await waitFor(() => expect(adminApi.runJob).toHaveBeenCalledWith('logStats'))
    await waitFor(() => expect(spy).toHaveBeenCalledWith({ queryKey: ['admin', 'jobs'] }))
  })

  it('disables Run now while the job is running', async () => {
    adminApi.getJobs.mockResolvedValue({ data: [job({ running: true })] })
    renderTab()
    expect(await screen.findByText('Chạy ngay')).toBeDisabled()
    expect(screen.getByText('ĐANG CHẠY')).toBeInTheDocument()
  })

  it('shows the translated server error', async () => {
    adminApi.updateJob.mockRejectedValue({ message: 'Cron không hợp lệ' })
    renderTab()
    fireEvent.click(await screen.findByLabelText('Bật cleanup'))
    expect(await screen.findByRole('alert')).toHaveTextContent('Cron không hợp lệ')
  })

  it('renders in English', async () => {
    await i18n.changeLanguage('en')
    renderTab()
    expect(await screen.findAllByText('Run now')).toHaveLength(2)
  })
})
