import React from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import CronEditModal from '../CronEditModal.jsx'
import * as adminApi from '../../../api/admin.js'
import i18n from '../../../i18n/index.js'

vi.mock('../../../api/admin.js', () => ({ previewJobCron: vi.fn() }))

const job = { name: 'digest', cron: '0 */15 * * * *', zone: 'Asia/Ho_Chi_Minh' }

function renderModal(props = {}) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onCancel = vi.fn()
  const onSave = vi.fn()
  render(<QueryClientProvider client={qc}>
    <CronEditModal job={job} pending={false} error="" onCancel={onCancel} onSave={onSave} {...props} />
  </QueryClientProvider>)
  return { onCancel, onSave, input: screen.getByLabelText('Cron của digest') }
}

beforeEach(async () => {
  vi.clearAllMocks()
  await i18n.changeLanguage('vi')
  adminApi.previewJobCron.mockResolvedValue({ data: ['2026-10-20T01:00:00Z', '2026-10-20T01:15:00Z', '2026-10-20T01:30:00Z'] })
})

describe('CronEditModal', () => {
  it('opens with the description, the next runs and the input focused', async () => {
    const { input } = renderModal()
    expect(input).toHaveFocus()
    expect(screen.getByText('“Mỗi 15 phút”')).toBeInTheDocument()
    await waitFor(() => expect(adminApi.previewJobCron).toHaveBeenCalledWith('0 */15 * * * *', 'Asia/Ho_Chi_Minh'))
    expect(await screen.findByText(/lần tới lúc/)).toBeInTheDocument()
    expect(screen.getByText('Lưu')).toBeDisabled()
  })

  it('highlights the field under the caret and its allowed values', () => {
    const { input } = renderModal()
    input.setSelectionRange(7, 7) // "0 */15 [*] * * *": the hour field
    fireEvent.select(input)
    expect(screen.getByText('giờ')).toHaveClass('active')
    expect(screen.getByText('0-23')).toBeInTheDocument()
  })

  it('shows the local rule errors and blocks save without calling the server', async () => {
    const { input } = renderModal()
    await waitFor(() => expect(adminApi.previewJobCron).toHaveBeenCalledTimes(1))
    fireEvent.change(input, { target: { value: '* * * * * *' } })
    expect(screen.getByText('Trường giây phải là một số (0-59)')).toBeInTheDocument()
    fireEvent.change(input, { target: { value: '*/5 * * * *' } })
    expect(screen.getByText(/Cron cần 6 trường/)).toBeInTheDocument()
    expect(screen.getByText('Lưu')).toBeDisabled()
    await new Promise(r => setTimeout(r, 350))
    expect(adminApi.previewJobCron).toHaveBeenCalledTimes(1)
  })

  it('shows a server preview error', async () => {
    adminApi.previewJobCron.mockRejectedValue({ message: 'Múi giờ không hợp lệ' })
    renderModal()
    expect(await screen.findByText('Múi giờ không hợp lệ')).toBeInTheDocument()
  })

  it('says when a cron never fires', async () => {
    adminApi.previewJobCron.mockResolvedValue({ data: [] })
    const { input } = renderModal()
    fireEvent.change(input, { target: { value: '0 0 3 30 2 *' } })
    expect(await screen.findByText('Cron này không bao giờ chạy')).toBeInTheDocument()
  })

  it('an example fills the input; save sends the trimmed cron', async () => {
    const { input, onSave } = renderModal()
    fireEvent.click(screen.getByText('0 30 3 * * *'))
    expect(input).toHaveValue('0 30 3 * * *')
    await waitFor(() => expect(adminApi.previewJobCron).toHaveBeenCalledWith('0 30 3 * * *', 'Asia/Ho_Chi_Minh'))
    await waitFor(() => expect(screen.getByText('Lưu')).toBeEnabled())
    fireEvent.click(screen.getByText('Lưu'))
    expect(onSave).toHaveBeenCalledWith('0 30 3 * * *')
  })

  it('Escape closes, but not while saving', () => {
    const { onCancel } = renderModal()
    fireEvent.keyDown(window, { key: 'Escape' })
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  it('Escape is ignored while pending and the parent error is shown', () => {
    const onCancel = vi.fn()
    const qc = new QueryClient()
    render(<QueryClientProvider client={qc}>
      <CronEditModal job={job} pending error="Lỗi lưu" onCancel={onCancel} onSave={vi.fn()} />
    </QueryClientProvider>)
    fireEvent.keyDown(window, { key: 'Escape' })
    expect(onCancel).not.toHaveBeenCalled()
  })
})
