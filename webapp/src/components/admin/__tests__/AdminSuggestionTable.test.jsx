import React from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AdminSuggestionTable from '../AdminSuggestionTable.jsx'
import { suggestionsApi } from '../../../api/suggestions.js'
import i18n from '../../../i18n/index.js'

vi.mock('../../../api/suggestions.js', () => ({
  suggestionsApi: { adminList: vi.fn(), approve: vi.fn(), reject: vi.fn(), reopen: vi.fn(), pendingCount: vi.fn() },
}))

const pending = {
  id: 1, sourceUrl: 'https://youtu.be/abc', platform: 'YOUTUBE', title: 'Funny cat', note: 'so good',
  status: 'PENDING', suggesterName: 'a***@b.com', createdAt: '2026-10-01T10:00:00Z',
}
const rejected = {
  id: 2, sourceUrl: 'https://youtu.be/def', platform: 'YOUTUBE', title: null, note: null, status: 'REJECTED',
  rejectReason: 'DUPLICATE', suggesterName: 'x***@y.com', createdAt: '2026-10-01T11:00:00Z',
}
const approved = {
  id: 3, sourceUrl: 'https://youtu.be/ghi', platform: 'YOUTUBE', title: 'Dog', status: 'APPROVED',
  live: true, importJobId: 42, suggesterName: 'z', createdAt: '2026-10-01T12:00:00Z',
}

function renderTable() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={qc}><AdminSuggestionTable /></QueryClientProvider>)
}

beforeEach(async () => {
  vi.clearAllMocks()
  await i18n.changeLanguage('vi')
  suggestionsApi.adminList.mockResolvedValue({ data: { content: [pending, rejected, approved], totalPages: 1, totalElements: 3 } })
  suggestionsApi.approve.mockResolvedValue({ data: {} })
  suggestionsApi.reject.mockResolvedValue({ data: {} })
})

describe('AdminSuggestionTable', () => {
  it('renders rows, defaults to PENDING and links open in a new tab safely', async () => {
    renderTable()
    expect(await screen.findByText('Funny cat')).toBeTruthy()
    expect(suggestionsApi.adminList).toHaveBeenLastCalledWith('PENDING', 0, 20)
    const link = screen.getByText('https://youtu.be/abc').closest('a')
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')
    expect(screen.getByText('a***@b.com')).toBeTruthy()
    expect(screen.getByText('Trùng lặp')).toBeTruthy()
    expect(screen.getByText('Job #42')).toBeTruthy()
    // only the pending row has actions
    expect(screen.getAllByLabelText('Duyệt')).toHaveLength(1)
  })

  it('status filter changes the query', async () => {
    renderTable()
    await screen.findByText('Funny cat')
    fireEvent.change(screen.getByLabelText('Lọc theo trạng thái'), { target: { value: 'REJECTED' } })
    await waitFor(() => expect(suggestionsApi.adminList).toHaveBeenLastCalledWith('REJECTED', 0, 20))
    fireEvent.change(screen.getByLabelText('Lọc theo trạng thái'), { target: { value: 'ALL' } })
    await waitFor(() => expect(suggestionsApi.adminList).toHaveBeenLastCalledWith('', 0, 20))
  })

  it('approve sends the edited title override', async () => {
    renderTable()
    await screen.findByText('Funny cat')
    fireEvent.click(screen.getByLabelText('Duyệt'))
    const dialog = screen.getByRole('dialog')
    const input = within(dialog).getByLabelText(/Tiêu đề/)
    expect(input).toHaveValue('Funny cat')
    fireEvent.change(input, { target: { value: 'Better title' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Duyệt' }))
    await waitFor(() => expect(suggestionsApi.approve).toHaveBeenCalledWith(1, 'Better title'))
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  })

  it('reject requires a reason, and a note for OTHER', async () => {
    renderTable()
    await screen.findByText('Funny cat')
    fireEvent.click(screen.getByLabelText('Từ chối'))
    const dialog = screen.getByRole('dialog')
    const submit = within(dialog).getByRole('button', { name: 'Từ chối' })
    fireEvent.click(submit)
    expect(await within(dialog).findByText('Vui lòng chọn lý do')).toBeTruthy()
    fireEvent.change(within(dialog).getByLabelText('Lý do'), { target: { value: 'OTHER' } })
    fireEvent.click(submit)
    expect(await within(dialog).findByText(/cần có ghi chú/)).toBeTruthy()
    expect(suggestionsApi.reject).not.toHaveBeenCalled()
    fireEvent.change(within(dialog).getByLabelText(/Ghi chú/), { target: { value: 'spam' } })
    fireEvent.click(submit)
    await waitFor(() => expect(suggestionsApi.reject).toHaveBeenCalledWith(1, 'OTHER', 'spam'))
  })

  it('reject without note for a non-OTHER reason', async () => {
    renderTable()
    await screen.findByText('Funny cat')
    fireEvent.click(screen.getByLabelText('Từ chối'))
    const dialog = screen.getByRole('dialog')
    fireEvent.change(within(dialog).getByLabelText('Lý do'), { target: { value: 'DUPLICATE' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Từ chối' }))
    await waitFor(() => expect(suggestionsApi.reject).toHaveBeenCalledWith(1, 'DUPLICATE', ''))
  })

  it('shows the translated message when import is disabled (503)', async () => {
    suggestionsApi.approve.mockRejectedValue({ status: 503, code: 'error.import.disabled', message: 'x' })
    renderTable()
    await screen.findByText('Funny cat')
    fireEvent.click(screen.getByLabelText('Duyệt'))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Duyệt' }))
    expect(await screen.findByText('Import đang tắt (VIDEO_IMPORT_ENABLED)')).toBeTruthy()
  })

  it('falls back to the API message for other errors', async () => {
    suggestionsApi.approve.mockRejectedValue({ status: 500, message: 'boom' })
    renderTable()
    await screen.findByText('Funny cat')
    fireEvent.click(screen.getByLabelText('Duyệt'))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Duyệt' }))
    expect(await screen.findByText('boom')).toBeTruthy()
  })

  it('renders in English with the notPending message', async () => {
    await i18n.changeLanguage('en')
    suggestionsApi.reject.mockRejectedValue({ status: 409, code: 'error.suggestion.notPending', message: 'x' })
    renderTable()
    await screen.findByText('Funny cat')
    fireEvent.click(screen.getByLabelText('Reject'))
    const dialog = screen.getByRole('dialog')
    fireEvent.change(within(dialog).getByLabelText('Reason'), { target: { value: 'DUPLICATE' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Reject' }))
    expect(await screen.findByText('This suggestion has already been reviewed')).toBeTruthy()
  })

  describe('reopen', () => {
    const reopenable = { ...rejected, canReopen: true }
    const listOf = rows => ({ data: { content: rows, totalPages: 1, totalElements: rows.length } })

    it('shows Reopen only on rows with canReopen', async () => {
      suggestionsApi.adminList.mockResolvedValue(listOf([pending, reopenable, approved]))
      renderTable()
      await screen.findByText('Funny cat')
      expect(screen.getAllByLabelText('Mở lại')).toHaveLength(1)
    })

    it('confirms, POSTs reopen and refetches the list', async () => {
      suggestionsApi.adminList.mockResolvedValue(listOf([reopenable]))
      suggestionsApi.reopen.mockResolvedValue({ data: {} })
      renderTable()
      fireEvent.click(await screen.findByLabelText('Mở lại'))
      expect(suggestionsApi.reopen).not.toHaveBeenCalled()
      const calls = suggestionsApi.adminList.mock.calls.length
      fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Mở lại' }))
      await waitFor(() => expect(suggestionsApi.reopen).toHaveBeenCalledWith(2))
      await waitFor(() => expect(suggestionsApi.adminList.mock.calls.length).toBeGreaterThan(calls))
    })

    it('disables the button while pending and sends a single request', async () => {
      suggestionsApi.adminList.mockResolvedValue(listOf([reopenable]))
      suggestionsApi.reopen.mockReturnValue(new Promise(() => {}))
      renderTable()
      const button = await screen.findByLabelText('Mở lại')
      fireEvent.click(button)
      fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Mở lại' }))
      await waitFor(() => expect(button).toBeDisabled())
      expect(button).toHaveAttribute('aria-busy', 'true')
      fireEvent.click(button)
      expect(screen.queryByRole('dialog')).toBeNull()
      expect(suggestionsApi.reopen).toHaveBeenCalledTimes(1)
      expect(suggestionsApi.reopen).toHaveBeenCalledWith(2)
    })

    it('does nothing when the confirmation is cancelled', async () => {
      suggestionsApi.adminList.mockResolvedValue(listOf([reopenable]))
      renderTable()
      fireEvent.click(await screen.findByLabelText('Mở lại'))
      const dialog = screen.getByRole('dialog')
      fireEvent.click(within(dialog).getAllByRole('button').find(b => /Huỷ|Hủy/.test(b.textContent)))
      await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
      expect(suggestionsApi.reopen).not.toHaveBeenCalled()
    })

    it('shows the translated 409 error', async () => {
      await i18n.changeLanguage('en')
      suggestionsApi.adminList.mockResolvedValue(listOf([reopenable]))
      suggestionsApi.reopen.mockRejectedValue({ status: 409, code: 'error.suggestion.cannotReopen', message: 'x' })
      renderTable()
      fireEvent.click(await screen.findByLabelText('Reopen'))
      fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Reopen' }))
      expect(await screen.findByText("This suggestion can't be reopened (its import is running or finished)")).toBeTruthy()
    })
  })
})
