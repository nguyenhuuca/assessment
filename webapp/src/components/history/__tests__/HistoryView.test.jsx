import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import i18n from '../../../i18n/index.js'
import HistoryView from '../HistoryView.jsx'
import { suggestionsApi } from '../../../api/suggestions.js'

let auth
vi.mock('../../../hooks/useAuth.js', () => ({ useAuth: () => auth }))
vi.mock('../../../api/suggestions.js', () => ({ suggestionsApi: { mine: vi.fn() } }))

const row = (id, over = {}) => ({
  id, sourceUrl: `https://youtu.be/${id}`, title: `Video ${id}`, note: null, status: 'PENDING', live: false,
  createdAt: '2026-10-10T10:00:00Z', ...over,
})
const page = (content, number, totalPages) => ({ data: { content, number, totalPages, totalElements: content.length } })

function renderView(props = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <HistoryView {...props} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  auth = { isLoggedIn: true, user: { email: 'me@x.com' } }
  suggestionsApi.mine.mockResolvedValue(page([], 0, 0))
})

describe('HistoryView', () => {
  it('guest sees a sign-in hint and no request is made', () => {
    auth = { isLoggedIn: false, user: null }
    renderView()
    expect(screen.getByText('Đăng nhập để xem các video bạn đã đề xuất')).toBeInTheDocument()
    expect(suggestionsApi.mine).not.toHaveBeenCalled()
  })

  it('shows the empty state', async () => {
    renderView()
    expect(await screen.findByText(/Bạn chưa đề xuất video nào/)).toBeInTheDocument()
  })

  it('renders rows: external link, note, status chip, reject reason, live play', async () => {
    suggestionsApi.mine.mockResolvedValue(page([
      row(1, { note: 'ghi chú 1' }),
      row(2, { status: 'REJECTED', rejectReason: 'DUPLICATE', rejectNote: 'đã có' }),
      row(3, { status: 'APPROVED', live: true, liveVideoId: 77 }),
    ], 0, 1))
    const onPlayVideo = vi.fn()
    renderView({ onPlayVideo })

    const link = await screen.findByRole('link', { name: 'Video 1' })
    expect(link).toHaveAttribute('href', 'https://youtu.be/1')
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'))
    expect(screen.getByText('ghi chú 1')).toBeInTheDocument()
    expect(screen.getByText('Chờ duyệt', { selector: '.admin-badge' })).toBeInTheDocument()
    expect(screen.getByText(/Lý do: Trùng với video đã có/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Xem video Video 3' }))
    expect(onPlayVideo).toHaveBeenCalledWith(77)
  })

  it('filter tabs request the matching status (LIVE included)', async () => {
    renderView()
    await waitFor(() => expect(suggestionsApi.mine).toHaveBeenCalledWith('', 0, 20))
    for (const [name, status] of [['Chờ duyệt', 'PENDING'], ['Đã duyệt', 'APPROVED'], ['Bị từ chối', 'REJECTED'], ['Đã lên app', 'LIVE']]) {
      fireEvent.click(screen.getByRole('tab', { name }))
      await waitFor(() => expect(suggestionsApi.mine).toHaveBeenCalledWith(status, 0, 20))
      expect(screen.getByRole('tab', { name })).toHaveAttribute('aria-selected', 'true')
    }
    expect(await screen.findByText('Không có đề xuất nào ở trạng thái này.')).toBeInTheDocument()
  })

  it('"Xem thêm" appends the next page and disappears on the last one', async () => {
    suggestionsApi.mine
      .mockResolvedValueOnce(page([row(1)], 0, 2))
      .mockResolvedValueOnce(page([row(2)], 1, 2))
    renderView()
    await screen.findByText('Video 1')
    fireEvent.click(screen.getByRole('button', { name: 'Xem thêm' }))
    expect(await screen.findByText('Video 2')).toBeInTheDocument()
    expect(screen.getByText('Video 1')).toBeInTheDocument()
    expect(suggestionsApi.mine).toHaveBeenLastCalledWith('', 1, 20)
    expect(screen.queryByRole('button', { name: 'Xem thêm' })).toBeNull()
  })

  it('renders in English', async () => {
    await i18n.changeLanguage('en')
    suggestionsApi.mine.mockResolvedValue(page([row(1, { status: 'REJECTED', rejectReason: 'COPYRIGHT' })], 0, 1))
    renderView()
    expect(await screen.findByText('My suggestions')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Pending' })).toBeInTheDocument()
    expect(await screen.findByText(/Reason: Copyright issue/)).toBeInTheDocument()
  })
})
