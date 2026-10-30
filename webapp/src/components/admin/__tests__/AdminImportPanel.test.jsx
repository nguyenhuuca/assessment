import React from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AdminImportPanel from '../AdminImportPanel.jsx'
import * as adminApi from '../../../api/admin.js'

vi.mock('../../../api/admin.js', () => ({
  listImports: vi.fn(),
  createImports: vi.fn(),
  previewImport: vi.fn(),
  runImportNow: vi.fn(),
  cancelImport: vi.fn(),
  retryImport: vi.fn(),
}))

const job = (o) => ({ id: 1, title: 'T', sourceUrl: 'https://youtu.be/a', platform: 'YOUTUBE', status: 'PENDING',
  progressPct: 0, downloadedBytes: 0, totalBytes: 0, scheduledAt: null, createdAt: '2026-10-01T10:00:00Z', ...o })

function setJobs(content) {
  adminApi.listImports.mockResolvedValue({ data: { content, totalPages: 1, totalElements: content.length } })
}
function renderPanel() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={qc}><AdminImportPanel /></QueryClientProvider>)
}
const type = (text) => fireEvent.change(screen.getByLabelText(/URL video/), { target: { value: text } })

beforeEach(() => {
  vi.clearAllMocks()
  setJobs([])
  adminApi.createImports.mockResolvedValue({ data: { results: [{ line: 1, jobId: 12 }, { line: 2, errorCode: 'DUPLICATE_ACTIVE' }] } })
  adminApi.runImportNow.mockResolvedValue({})
  adminApi.cancelImport.mockResolvedValue({})
  adminApi.retryImport.mockResolvedValue({})
})

describe('ImportForm', () => {
  it('submits items with null scheduledAt and renders per-line results', async () => {
    renderPanel()
    type('https://youtu.be/a\nhttps://youtu.be/b | Tít')
    fireEvent.click(screen.getByText('Nhập video'))
    await waitFor(() => expect(adminApi.createImports).toHaveBeenCalledWith({
      items: [{ url: 'https://youtu.be/a' }, { url: 'https://youtu.be/b', title: 'Tít' }],
      scheduledAt: null,
    }))
    expect(await screen.findByText(/Đã tạo job #12/)).toBeInTheDocument()
    expect(screen.getByText(/Video này đang được nhập/)).toBeInTheDocument()
  })

  it('shows per-line validation errors and does not submit', () => {
    renderPanel()
    type('http://youtu.be/a')
    fireEvent.click(screen.getByText('Nhập video'))
    expect(screen.getByRole('alert')).toHaveTextContent('Dòng 1')
    expect(adminApi.createImports).not.toHaveBeenCalled()
  })

  it('keeps the schedule input mounted but idle in NOW mode (no layout shift)', () => {
    renderPanel()
    const input = screen.getByLabelText(/Thời gian hẹn giờ/)
    expect(input).toBeDisabled()
    expect(input).toHaveClass('import-schedule-idle')
    fireEvent.click(screen.getByLabelText('Hẹn giờ'))
    expect(input).toBeEnabled()
    expect(input).not.toHaveClass('import-schedule-idle')
    fireEvent.click(screen.getByLabelText('Tải ngay'))
    expect(input).toBeDisabled()
    expect(input).toHaveClass('import-schedule-idle')
  })

  it('blocks a schedule in the past', () => {
    renderPanel()
    type('https://youtu.be/a')
    fireEvent.click(screen.getByLabelText('Hẹn giờ'))
    fireEvent.change(screen.getByLabelText(/Thời gian hẹn giờ/), { target: { value: '2020-01-01T10:00' } })
    fireEvent.click(screen.getByText('Nhập video'))
    expect(screen.getByRole('alert')).toHaveTextContent('tương lai')
    expect(adminApi.createImports).not.toHaveBeenCalled()
  })

  it('sends UTC ISO for a future schedule', async () => {
    renderPanel()
    type('https://youtu.be/a')
    fireEvent.click(screen.getByLabelText('Hẹn giờ'))
    // GMT+7 wall time two days from now
    const slotMs = 5 * 60000
    const local = new Date(Math.floor((Date.now() + 2 * 86400000 + 7 * 3600000) / slotMs) * slotMs).toISOString().slice(0, 16)
    fireEvent.change(screen.getByLabelText(/Thời gian hẹn giờ/), { target: { value: local } })
    fireEvent.click(screen.getByText('Nhập video'))
    await waitFor(() => expect(adminApi.createImports).toHaveBeenCalled())
    expect(adminApi.createImports.mock.calls[0][0].scheduledAt).toBe(new Date(Date.parse(`${local}:00+07:00`)).toISOString())
  })

  it('single URL: preview fills the editable title and it is submitted', async () => {
    adminApi.previewImport.mockResolvedValue({ data: { title: 'Fetched', platform: 'YOUTUBE', durationSec: 5 } })
    renderPanel()
    type('https://youtu.be/a')
    fireEvent.click(screen.getByText('Lấy tiêu đề'))
    await waitFor(() => expect(screen.getByLabelText('Tiêu đề')).toHaveValue('Fetched'))
    expect(adminApi.previewImport).toHaveBeenCalledWith('https://youtu.be/a')
    fireEvent.click(screen.getByText('Nhập video'))
    await waitFor(() => expect(adminApi.createImports).toHaveBeenCalledWith({
      items: [{ url: 'https://youtu.be/a', title: 'Fetched' }], scheduledAt: null,
    }))
  })
})

describe('ImportForm collection only', () => {
  it('is off by default and sends no visibility', async () => {
    renderPanel()
    expect(screen.getByLabelText(/Collection only/)).not.toBeChecked()
    type('https://youtu.be/a')
    fireEvent.click(screen.getByText('Nhập video'))
    await waitFor(() => expect(adminApi.createImports).toHaveBeenCalled())
    expect(adminApi.createImports.mock.calls[0][0]).not.toHaveProperty('visibility')
  })

  it('sends COLLECTION_ONLY when checked', async () => {
    renderPanel()
    fireEvent.click(screen.getByLabelText(/Collection only/))
    type('https://youtu.be/a')
    fireEvent.click(screen.getByText('Nhập video'))
    await waitFor(() => expect(adminApi.createImports).toHaveBeenCalledWith({
      items: [{ url: 'https://youtu.be/a' }], scheduledAt: null, visibility: 'COLLECTION_ONLY',
    }))
  })

  it('shows a server error through the existing error path', async () => {
    adminApi.createImports.mockRejectedValue(new Error('boom'))
    renderPanel()
    type('https://youtu.be/a')
    fireEvent.click(screen.getByText('Nhập video'))
    expect(await screen.findByRole('alert')).toBeInTheDocument()
  })
})

describe('ImportTable', () => {
  it('badges COLLECTION_ONLY jobs only', async () => {
    setJobs([job({ id: 1, title: 'Co', visibility: 'COLLECTION_ONLY' }), job({ id: 2, title: 'Fe', visibility: 'FEED' })])
    renderPanel()
    await screen.findByText('Co')
    expect(screen.getAllByText('COLLECTION ONLY')).toHaveLength(1)
    expect(screen.getByText('COLLECTION ONLY').closest('tr')).toHaveTextContent('Co')
  })

  it('renders progress, ingestion state and mapped errors', async () => {
    setJobs([
      job({ id: 1, status: 'DOWNLOADING', phase: 'DOWNLOAD', progressPct: 42, downloadedBytes: 10485760, totalBytes: 20971520 }),
      job({ id: 2, status: 'DONE', ingested: true, title: 'Done1' }),
      job({ id: 3, status: 'DONE', ingested: false, title: 'Done2' }),
      job({ id: 4, status: 'FAILED', errorCode: 'LOGIN_REQUIRED', title: 'F' }),
    ])
    renderPanel()
    expect(await screen.findByText(/42% · 10.0 MB \/ 20.0 MB/)).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '42')
    expect(screen.getByText('Đã lên app')).toBeInTheDocument()
    expect(screen.getByText(/Chờ đồng bộ/)).toBeInTheDocument()
    expect(screen.getByText(/yêu cầu đăng nhập/)).toBeInTheDocument()
  })

  it('actions call the right API per status', async () => {
    setJobs([
      job({ id: 1, status: 'PENDING', title: 'P' }),
      job({ id: 2, status: 'FAILED', errorCode: 'TIMEOUT', title: 'F' }),
    ])
    renderPanel()
    fireEvent.click(await screen.findByText('Chạy ngay'))
    await waitFor(() => expect(adminApi.runImportNow).toHaveBeenCalledWith(1))
    fireEvent.click(screen.getByText('Huỷ'))
    await waitFor(() => expect(adminApi.cancelImport).toHaveBeenCalledWith(1))
    fireEvent.click(screen.getByText('Thử lại'))
    await waitFor(() => expect(adminApi.retryImport).toHaveBeenCalledWith(2))
  })

  it('passes the status filter to the API', async () => {
    renderPanel()
    await waitFor(() => expect(adminApi.listImports).toHaveBeenCalled())
    fireEvent.change(screen.getByLabelText('Lọc theo trạng thái'), { target: { value: 'FAILED' } })
    await waitFor(() => expect(adminApi.listImports).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'FAILED' })))
  })
})
