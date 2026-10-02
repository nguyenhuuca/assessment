import React from 'react'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AdminCommentTable from '../AdminCommentTable.jsx'
import * as adminApi from '../../../api/admin.js'

vi.mock('../../../api/admin.js', () => ({
  getComments: vi.fn(),
  moderateComment: vi.fn(),
}))

const visible = {
  id: 'c1', videoId: '14', videoTitle: 'Funny cat', authorEmail: 'a@b.com', guestName: null,
  isGuest: false, content: 'hello world', createdAt: '2026-10-01T10:00:00Z', status: 'VISIBLE',
}
const removed = {
  id: 'c2', videoId: '15', videoTitle: 'Dog', authorEmail: null, guestName: 'Anonymous7',
  isGuest: true, content: 'buy now', createdAt: '2026-10-01T11:00:00Z', status: 'REMOVED',
  moderationReason: 'SPAM', moderatedBy: 'admin@x.com',
}

function renderTable() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}><AdminCommentTable /></QueryClientProvider>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  adminApi.getComments.mockResolvedValue({ data: { content: [visible, removed], totalPages: 1, totalElements: 2, number: 0 } })
  adminApi.moderateComment.mockResolvedValue({})
})
afterEach(() => vi.restoreAllMocks())

describe('AdminCommentTable', () => {
  it('renders rows with author, video and status', async () => {
    renderTable()
    expect(await screen.findByText('hello world')).toBeTruthy()
    expect(screen.getByText('a@b.com')).toBeTruthy()
    expect(screen.getByText('Guest · Anonymous7')).toBeTruthy()
    expect(screen.getByText('Funny cat')).toBeTruthy()
    expect(screen.getAllByText('VISIBLE').some(el => el.classList.contains('admin-badge'))).toBe(true)
    expect(screen.getAllByText('REMOVED').some(el => el.classList.contains('admin-badge'))).toBe(true)
  })

  it('status filter changes the query params', async () => {
    renderTable()
    await screen.findByText('hello world')
    expect(adminApi.getComments).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0, status: null }))
    fireEvent.change(screen.getByLabelText('Status filter'), { target: { value: 'REMOVED' } })
    await waitFor(() =>
      expect(adminApi.getComments).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'REMOVED', page: 0 }))
    )
  })

  it('remove requires a reason', async () => {
    renderTable()
    await screen.findByText('hello world')
    fireEvent.click(screen.getByTitle('Remove'))
    fireEvent.click(screen.getByRole('button', { name: 'Remove' }))
    expect((await screen.findByRole('alert')).textContent).toContain('chọn lý do')
    expect(adminApi.moderateComment).not.toHaveBeenCalled()
  })

  it('OTHER requires a note', async () => {
    renderTable()
    await screen.findByText('hello world')
    fireEvent.click(screen.getByTitle('Remove'))
    fireEvent.change(screen.getByLabelText('Lý do'), { target: { value: 'OTHER' } })
    fireEvent.click(screen.getByRole('button', { name: 'Remove' }))
    expect((await screen.findByRole('alert')).textContent).toContain('ghi chú')
    expect(adminApi.moderateComment).not.toHaveBeenCalled()
  })

  it('successful remove calls the API with the body', async () => {
    renderTable()
    await screen.findByText('hello world')
    fireEvent.click(screen.getByTitle('Remove'))
    fireEvent.change(screen.getByLabelText('Lý do'), { target: { value: 'SPAM' } })
    fireEvent.change(screen.getByLabelText(/Ghi chú/), { target: { value: 'link farm' } })
    fireEvent.click(screen.getByRole('button', { name: 'Remove' }))
    await waitFor(() =>
      expect(adminApi.moderateComment).toHaveBeenCalledWith('c1', { action: 'REMOVE', reason: 'SPAM', note: 'link farm' })
    )
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  })

  it('restore confirms then calls the API', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderTable()
    await screen.findByText('buy now')
    fireEvent.click(screen.getByTitle('Restore'))
    await waitFor(() => expect(adminApi.moderateComment).toHaveBeenCalledWith('c2', { action: 'RESTORE' }))
  })

  it('restore does nothing when confirm is cancelled', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    renderTable()
    await screen.findByText('buy now')
    fireEvent.click(screen.getByTitle('Restore'))
    expect(adminApi.moderateComment).not.toHaveBeenCalled()
  })
})
