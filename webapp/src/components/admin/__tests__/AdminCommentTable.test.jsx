import React from 'react'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AdminCommentTable from '../AdminCommentTable.jsx'
import * as adminApi from '../../../api/admin.js'

vi.mock('../../../api/admin.js', () => ({
  getComments: vi.fn(),
  moderateComment: vi.fn(),
  bulkModerateComments: vi.fn(),
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
  adminApi.bulkModerateComments.mockResolvedValue({ data: { requested: 2, updated: 1, unchanged: 1, notFound: [] } })
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

  it('bulk bar appears only when rows are selected', async () => {
    renderTable()
    await screen.findByText('hello world')
    expect(screen.queryByRole('toolbar', { name: 'Bulk actions' })).toBeNull()
    fireEvent.click(screen.getByLabelText('Select comment c1'))
    expect(screen.getByRole('toolbar', { name: 'Bulk actions' }).textContent).toContain('Đã chọn 1')
    fireEvent.click(screen.getByRole('button', { name: 'Bỏ chọn' }))
    expect(screen.queryByRole('toolbar', { name: 'Bulk actions' })).toBeNull()
  })

  it('select all selects every row on the page and toggles off', async () => {
    renderTable()
    await screen.findByText('hello world')
    fireEvent.click(screen.getByLabelText('Select all on page'))
    expect(screen.getByLabelText('Select comment c1').checked).toBe(true)
    expect(screen.getByLabelText('Select comment c2').checked).toBe(true)
    fireEvent.click(screen.getByLabelText('Select all on page'))
    expect(screen.getByLabelText('Select comment c1').checked).toBe(false)
  })

  it('bulk remove requires a reason and sends all selected ids', async () => {
    renderTable()
    await screen.findByText('hello world')
    fireEvent.click(screen.getByLabelText('Select all on page'))
    fireEvent.click(screen.getByRole('button', { name: 'Gỡ đã chọn' }))
    expect(screen.getByRole('dialog').textContent).toContain('2 bình luận đã chọn')
    fireEvent.click(screen.getByRole('button', { name: 'Remove' }))
    expect((await screen.findByRole('alert')).textContent).toContain('chọn lý do')
    expect(adminApi.bulkModerateComments).not.toHaveBeenCalled()

    fireEvent.change(screen.getByLabelText('Lý do'), { target: { value: 'SPAM' } })
    fireEvent.click(screen.getByRole('button', { name: 'Remove' }))
    await waitFor(() =>
      expect(adminApi.bulkModerateComments).toHaveBeenCalledWith({ ids: ['c1', 'c2'], action: 'REMOVE', reason: 'SPAM' })
    )
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect((await screen.findByRole('status')).textContent).toContain('Đã gỡ 1 bình luận (1 không thay đổi)')
    expect(screen.queryByRole('toolbar', { name: 'Bulk actions' })).toBeNull()
  })

  it('bulk restore confirms then calls the API', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderTable()
    await screen.findByText('buy now')
    fireEvent.click(screen.getByLabelText('Select comment c2'))
    fireEvent.click(screen.getByRole('button', { name: 'Khôi phục đã chọn' }))
    await waitFor(() =>
      expect(adminApi.bulkModerateComments).toHaveBeenCalledWith({ ids: ['c2'], action: 'RESTORE' })
    )
  })

  it('shows a reply tag only for comments with a parentId', async () => {
    adminApi.getComments.mockResolvedValue({
      data: { content: [visible, { ...visible, id: 'c9', content: 'a reply', parentId: 'c1' }], totalPages: 1, totalElements: 2, number: 0 },
    })
    renderTable()
    await screen.findByText('a reply')
    expect(screen.getAllByText('↳ Reply')).toHaveLength(1)
  })

  it('DELETED comments get the deleted badge and no Restore action', async () => {
    adminApi.getComments.mockResolvedValue({
      data: { content: [{ ...visible, id: 'c8', content: 'gone', status: 'DELETED' }], totalPages: 1, totalElements: 1, number: 0 },
    })
    renderTable()
    await screen.findByText('gone')
    expect(screen.getAllByText('DELETED').some(el => el.classList.contains('admin-badge') && el.classList.contains('deleted'))).toBe(true)
    expect(screen.queryByTitle('Restore')).toBeNull()
    expect(screen.queryByTitle('Remove')).toBeNull()
  })

  it('status filter offers DELETED', async () => {
    renderTable()
    await screen.findByText('hello world')
    fireEvent.change(screen.getByLabelText('Status filter'), { target: { value: 'DELETED' } })
    await waitFor(() =>
      expect(adminApi.getComments).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'DELETED' }))
    )
  })

  it('changing the status filter clears the selection', async () => {
    renderTable()
    await screen.findByText('hello world')
    fireEvent.click(screen.getByLabelText('Select comment c1'))
    fireEvent.change(screen.getByLabelText('Status filter'), { target: { value: 'REMOVED' } })
    await waitFor(() => expect(screen.queryByRole('toolbar', { name: 'Bulk actions' })).toBeNull())
  })
})
