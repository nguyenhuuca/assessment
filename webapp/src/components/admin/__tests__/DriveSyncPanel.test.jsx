import React from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import DriveSyncPanel from '../DriveSyncPanel.jsx'
import { TIME_PRESETS } from '../../../utils/timeRange.js'
import * as adminApi from '../../../api/admin.js'
import { useAuth } from '../../../hooks/useAuth.js'

vi.mock('../../../api/admin.js', () => ({
  getDriveSyncFiles: vi.fn(),
  getDriveSyncRun: vi.fn(),
  startDriveSyncRun: vi.fn(),
}))
vi.mock('../../../hooks/useAuth.js', () => ({ useAuth: vi.fn() }))

const file = (o) => ({ fileId: 'f1', name: 'a.mp4', sizeBytes: 1048576, createdAt: '2026-10-01T10:00:00Z',
  localSizeBytes: null, videoSourceId: null, dbTitle: null, state: 'MISSING_LOCAL', ...o })
const FILES = [
  file({ fileId: 'f1', name: 'one.mp4' }),
  file({ fileId: 'f2', name: 'two.mp4', state: 'SYNCED', dbTitle: 'Two' }),
  file({ fileId: 'f3', name: 'three.mp4', state: 'MISSING_DB' }),
]

function renderPanel() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={qc}><DriveSyncPanel /></QueryClientProvider>)
}
const setRole = (role) => useAuth.mockReturnValue({ user: { role } })
const confirmSync = () => fireEvent.click(within(screen.getByRole('dialog')).getByText('Force sync'))

beforeEach(() => {
  vi.clearAllMocks()
  setRole('SUPER_ADMIN')
  adminApi.getDriveSyncFiles.mockResolvedValue({ data: { files: FILES, orphans: [], maxBatch: 50 } })
  adminApi.getDriveSyncRun.mockResolvedValue({ data: null })
  adminApi.startDriveSyncRun.mockResolvedValue({ data: { runId: 'r1', active: true, items: [] } })
})

describe('DriveSyncPanel', () => {
  it('ADMIN sees the table but controls are disabled with a reason', async () => {
    setRole('ADMIN')
    renderPanel()
    expect(await screen.findByText('one.mp4')).toBeInTheDocument()
    expect(screen.getByLabelText('Chọn one.mp4')).toBeDisabled()
    expect(screen.getByLabelText('Chọn one.mp4')).toHaveAttribute('title', 'Chỉ SUPER_ADMIN được force sync')
    expect(screen.getByText('chọn tất cả chưa sync')).toBeDisabled()
    expect(screen.getByText(/FORCE_SYNC/)).toBeDisabled()
    expect(screen.getByText(/FORCE_SYNC/)).toHaveAttribute('title', 'Chỉ SUPER_ADMIN được force sync')
  })

  it('SUPER_ADMIN selects files and POSTs the fileIds', async () => {
    renderPanel()
    fireEvent.click(await screen.findByLabelText('Chọn one.mp4'))
    fireEvent.click(screen.getByLabelText('Chọn two.mp4'))
    fireEvent.click(screen.getByText('FORCE_SYNC (2)'))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText('one.mp4')).toBeInTheDocument()
    expect(within(dialog).getByText('two.mp4')).toBeInTheDocument()
    expect(within(dialog).getByRole('alert')).toHaveTextContent('1 file đã SYNCED')
    expect(adminApi.startDriveSyncRun).not.toHaveBeenCalled()
    confirmSync()
    await waitFor(() => expect(adminApi.startDriveSyncRun).toHaveBeenCalledWith(['f1', 'f2']))
  })

  it('does not POST when confirm is cancelled', async () => {
    renderPanel()
    fireEvent.click(await screen.findByLabelText('Chọn one.mp4'))
    fireEvent.click(screen.getByText('FORCE_SYNC (1)'))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /^(Hủy|Huỷ)$/ }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(adminApi.startDriveSyncRun).not.toHaveBeenCalled()
  })

  it('select-all picks only unsynced files', async () => {
    renderPanel()
    await screen.findByText('one.mp4')
    fireEvent.click(screen.getByText('chọn tất cả chưa sync'))
    fireEvent.click(screen.getByText('FORCE_SYNC (2)'))
    confirmSync()
    await waitFor(() => expect(adminApi.startDriveSyncRun).toHaveBeenCalledWith(['f1', 'f3']))
  })

  it('filter hides SYNCED files', async () => {
    renderPanel()
    expect(await screen.findByText('two.mp4')).toBeInTheDocument()
    fireEvent.click(screen.getByLabelText('chỉ chưa sync'))
    expect(screen.queryByText('two.mp4')).not.toBeInTheDocument()
    expect(screen.getByText('one.mp4')).toBeInTheDocument()
  })

  it('caps selection at maxBatch', async () => {
    adminApi.getDriveSyncFiles.mockResolvedValue({ data: { files: FILES, orphans: [], maxBatch: 1 } })
    renderPanel()
    await screen.findByText('one.mp4')
    fireEvent.click(screen.getByText('chọn tất cả chưa sync'))
    expect(screen.getByText('FORCE_SYNC (1)')).toBeInTheDocument()
    expect(screen.getByLabelText('Chọn three.mp4')).toBeDisabled()
  })

  it('renders run progress and disables controls while active', async () => {
    adminApi.getDriveSyncRun.mockResolvedValue({ data: {
      runId: 'r1', startedAt: '2026-10-11T10:00:00Z', finishedAt: null, active: true,
      items: [
        { fileId: 'f1', name: 'one.mp4', status: 'DOWNLOADING', downloadedBytes: 524288, totalBytes: 1048576, errorCode: null },
        { fileId: 'f3', name: 'three.mp4', status: 'FAILED', downloadedBytes: 0, totalBytes: 100, errorCode: 'DRIVE_DOWNLOAD_FAILED' },
      ],
    } })
    renderPanel()
    const bar = await screen.findByLabelText('Tiến độ one.mp4')
    expect(bar).toHaveAttribute('aria-valuenow', '50')
    expect(screen.getByText('DRIVE_DOWNLOAD_FAILED')).toBeInTheDocument()
    expect(screen.getByLabelText('Chọn one.mp4')).toBeDisabled()
    expect(screen.getByText(/FORCE_SYNC/)).toBeDisabled()
    expect(screen.getByText('chọn tất cả chưa sync')).toBeDisabled()
  })

  it('shows the API error message when starting fails', async () => {
    adminApi.startDriveSyncRun.mockRejectedValue({ message: 'SYNC_RUNNING', status: 409 })
    renderPanel()
    fireEvent.click(await screen.findByLabelText('Chọn one.mp4'))
    fireEvent.click(screen.getByText('FORCE_SYNC (1)'))
    confirmSync()
    expect(await screen.findByRole('alert')).toHaveTextContent('SYNC_RUNNING')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('lists orphans read-only', async () => {
    adminApi.getDriveSyncFiles.mockResolvedValue({ data: { files: FILES, orphans: [{ videoSourceId: 9, sourceId: 'x.mp4', title: 'Lost' }], maxBatch: 50 } })
    renderPanel()
    expect(await screen.findByText(/Lost/)).toBeInTheDocument()
  })
  describe('time filter', () => {
    const lastCall = () => adminApi.getDriveSyncFiles.mock.calls.at(-1)[0]
    const fromAgo = (params) => Date.now() - Date.parse(params.from)
    const pick = (value) => fireEvent.change(screen.getByLabelText('Thời gian tạo'), { target: { value } })

    it('loads the last 12 hours by default', async () => {
      renderPanel()
      await screen.findByText('one.mp4')
      expect(adminApi.getDriveSyncFiles).toHaveBeenCalledTimes(1)
      const params = lastCall()
      expect(params.to).toBeUndefined()
      expect(Math.abs(fromAgo(params) - TIME_PRESETS['12h'])).toBeLessThan(5000)
      expect(screen.getByLabelText('Thời gian tạo')).toHaveValue('12h')
    })

    it('refetches with the new window when a preset changes', async () => {
      renderPanel()
      await screen.findByText('one.mp4')
      pick('7d')
      await waitFor(() => expect(adminApi.getDriveSyncFiles).toHaveBeenCalledTimes(2))
      expect(Math.abs(fromAgo(lastCall()) - TIME_PRESETS['7d'])).toBeLessThan(5000)
    })

    it('"All" sends no range and shows orphans', async () => {
      adminApi.getDriveSyncFiles.mockImplementation((params) => Promise.resolve({ data: params.from
        ? { files: FILES, orphans: [], orphansChecked: false, maxBatch: 50 }
        : { files: FILES, orphans: [{ videoSourceId: 9, sourceId: 'x.mp4', title: 'Lost' }], orphansChecked: true, maxBatch: 50 } }))
      renderPanel()
      expect(await screen.findByText(/Chọn "Tất cả" để xem orphan/)).toBeInTheDocument()
      expect(screen.queryByText(/Lost/)).not.toBeInTheDocument()
      pick('all')
      expect(await screen.findByText(/Lost/)).toBeInTheDocument()
      expect(lastCall()).toEqual({})
      expect(screen.queryByText(/Chọn "Tất cả" để xem orphan/)).not.toBeInTheDocument()
    })

    it('custom does not call the server until a time is picked', async () => {
      renderPanel()
      await screen.findByText('one.mp4')
      pick('custom')
      expect(screen.getByLabelText('Từ')).toBeInTheDocument()
      expect(screen.getByText('one.mp4')).toBeInTheDocument()
      expect(adminApi.getDriveSyncFiles).toHaveBeenCalledTimes(1)
    })

    it('custom range sends both bounds in UTC, to including its minute', async () => {
      renderPanel()
      await screen.findByText('one.mp4')
      pick('custom')
      fireEvent.change(screen.getByLabelText('Từ'), { target: { value: '2026-10-18T07:00' } })
      fireEvent.change(screen.getByLabelText('Đến'), { target: { value: '2026-10-18T19:00' } })
      await waitFor(() => expect(lastCall()).toEqual({ from: '2026-10-18T00:00:00.000Z', to: '2026-10-18T12:01:00.000Z' }))
    })

    it('custom from after to shows an error and does not call the server', async () => {
      renderPanel()
      await screen.findByText('one.mp4')
      pick('custom')
      fireEvent.change(screen.getByLabelText('Từ'), { target: { value: '2026-10-18T19:00' } })
      await waitFor(() => expect(lastCall()).toEqual({ from: '2026-10-18T12:00:00.000Z' }))
      const calls = adminApi.getDriveSyncFiles.mock.calls.length
      fireEvent.change(screen.getByLabelText('Đến'), { target: { value: '2026-10-18T07:00' } })
      expect(await screen.findByRole('alert')).toHaveTextContent('Thời gian bắt đầu phải trước thời gian kết thúc')
      expect(adminApi.getDriveSyncFiles).toHaveBeenCalledTimes(calls)
    })

    it('changing the filter clears the selection', async () => {
      renderPanel()
      fireEvent.click(await screen.findByLabelText('Chọn one.mp4'))
      expect(screen.getByText('FORCE_SYNC (1)')).toBeInTheDocument()
      pick('24h')
      expect(screen.getByText('FORCE_SYNC (0)')).toBeInTheDocument()
    })
  })
})
