import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import i18n from '../../../i18n/index.js'
import HistoryView from '../HistoryView.jsx'
import { suggestionsApi } from '../../../api/suggestions.js'
import { watchHistoryApi } from '../../../api/watchHistory.js'

let auth
vi.mock('../../../hooks/useAuth.js', () => ({ useAuth: () => auth }))
vi.mock('../../../api/suggestions.js', () => ({ suggestionsApi: { mine: vi.fn(), submit: vi.fn() } }))
vi.mock('../../../api/watchHistory.js', () => ({
  watchHistoryApi: { list: vi.fn(), summary: vi.fn(), remove: vi.fn(), clear: vi.fn(), progress: vi.fn() },
}))
vi.mock('../../../api/settings.js', () => ({ settingsApi: { get: vi.fn(), patch: vi.fn() } }))

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

/** The suggestions section lives on the second tab; the watch history tab is the default. */
function renderSuggestions(props = {}) {
  renderView(props)
  fireEvent.click(screen.getByRole('tab', { name: /Đề xuất của tôi/ }))
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.removeItem('historyTab')
  auth = { isLoggedIn: true, user: { email: 'me@x.com' } }
  suggestionsApi.mine.mockResolvedValue(page([], 0, 0))
  watchHistoryApi.list.mockResolvedValue(page([], 0, 0))
})

describe('HistoryView tabs', () => {
  it('opens on "Lịch sử xem" with counts on both pills and no suggestions request', async () => {
    watchHistoryApi.list.mockResolvedValue({ data: { content: [], totalPages: 1, totalElements: 4 } })
    suggestionsApi.mine.mockResolvedValue({ data: { content: [], totalPages: 1, totalElements: 2 } })
    renderView()
    const watch = screen.getByRole('tab', { name: /Lịch sử xem/ })
    expect(watch).toHaveAttribute('aria-selected', 'true')
    expect(await screen.findByText('4')).toBeInTheDocument()
    expect(await screen.findByText('2')).toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: 'Liên kết video *' })).toBeNull()
  })

  it('remembers the chosen tab in localStorage', async () => {
    renderView()
    fireEvent.click(screen.getByRole('tab', { name: /Đề xuất của tôi/ }))
    expect(localStorage.getItem('historyTab')).toBe('suggestions')
    expect(await screen.findByRole('textbox', { name: 'Liên kết video *' })).toBeInTheDocument()
  })

  it('restores the remembered tab', () => {
    localStorage.setItem('historyTab', 'suggestions')
    renderView()
    expect(screen.getByRole('tab', { name: /Đề xuất của tôi/ })).toHaveAttribute('aria-selected', 'true')
  })

  it('survives a localStorage that throws', () => {
    const get = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('denied') })
    const set = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('denied') })
    try {
      renderView()
      expect(screen.getByRole('tab', { name: /Lịch sử xem/ })).toHaveAttribute('aria-selected', 'true')
      fireEvent.click(screen.getByRole('tab', { name: /Đề xuất của tôi/ }))
      expect(screen.getByRole('tab', { name: /Đề xuất của tôi/ })).toHaveAttribute('aria-selected', 'true')
    } finally {
      get.mockRestore()
      set.mockRestore()
    }
  })
})

describe('HistoryView', () => {
  it('guest sees a sign-in hint and no request is made', () => {
    auth = { isLoggedIn: false, user: null }
    renderView()
    expect(screen.getByText('Đăng nhập để xem lịch sử xem và các video bạn đã đề xuất')).toBeInTheDocument()
    expect(suggestionsApi.mine).not.toHaveBeenCalled()
    expect(watchHistoryApi.list).not.toHaveBeenCalled()
  })

  it('shows the empty state', async () => {
    renderSuggestions()
    expect(await screen.findByText(/Bạn chưa đề xuất video nào/)).toBeInTheDocument()
  })

  it('renders rows: external link, note, status chip, reject reason, live play', async () => {
    suggestionsApi.mine.mockResolvedValue(page([
      row(1, { note: 'ghi chú 1' }),
      row(2, { status: 'REJECTED', rejectReason: 'DUPLICATE', rejectNote: 'đã có' }),
      row(3, { status: 'APPROVED', live: true, liveVideoId: 77, liveFileId: 'drive-77' }),
    ], 0, 1))
    const onPlayVideo = vi.fn()
    renderSuggestions({ onPlayVideo })

    expect(await screen.findByText('Video 1')).toBeInTheDocument()
    const link = screen.getByRole('link', { name: 'https://youtu.be/1' })
    expect(link).toHaveAttribute('href', 'https://youtu.be/1')
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'))
    expect(screen.getByText('ghi chú 1')).toBeInTheDocument()
    expect(screen.getByText('Chờ duyệt', { selector: '.admin-badge' })).toBeInTheDocument()
    expect(screen.getByText(/Lý do: Trùng với video đã có/)).toBeInTheDocument()

    expect(screen.getByText('3 đề xuất')).toBeInTheDocument()

    const liveThumb = screen.getByRole('button', { name: 'Xem video Video 3' })
    // A live video shows its real thumbnail instead of the status placeholder
    expect(liveThumb.querySelector('img')).toHaveAttribute('src', expect.stringContaining('drive-77'))
    fireEvent.error(liveThumb.querySelector('img'))
    expect(liveThumb.querySelector('img')).toBeNull()
    expect(liveThumb).toHaveTextContent('Đã lên app')
    fireEvent.click(liveThumb)
    expect(onPlayVideo).toHaveBeenCalledWith(77)
    expect(screen.getAllByRole('button', { name: /Xem trên app/ })).toHaveLength(1)
    fireEvent.click(screen.getByRole('button', { name: /Xem trên app/ }))
    expect(onPlayVideo).toHaveBeenCalledTimes(2)
  })

  it('inline card validates the link, then submits and clears it', async () => {
    suggestionsApi.submit.mockResolvedValue({ data: row(9) })
    renderSuggestions()
    const input = screen.getByRole('textbox', { name: 'Liên kết video *' })
    fireEvent.change(input, { target: { value: 'http://example.com/x' } })
    fireEvent.click(screen.getByRole('button', { name: /Gửi link/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Chỉ hỗ trợ liên kết https')
    expect(suggestionsApi.submit).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: /Thêm tiêu đề và ghi chú/ }))
    fireEvent.change(screen.getByRole('textbox', { name: 'Tiêu đề' }), { target: { value: ' Mèo ' } })
    fireEvent.change(input, { target: { value: 'https://youtu.be/abc' } })
    fireEvent.click(screen.getByRole('button', { name: /Gửi link/ }))
    await waitFor(() => expect(suggestionsApi.submit).toHaveBeenCalledWith(
      expect.objectContaining({ url: 'https://youtu.be/abc', title: 'Mèo', note: '' })))
    expect(await screen.findByText(/Đã gửi đề xuất/)).toBeInTheDocument()
    expect(input).toHaveValue('')
  })

  it('inline card shows the duplicate error from the API', async () => {
    suggestionsApi.submit.mockRejectedValue(Object.assign(new Error('dup'), { code: 'error.suggestion.duplicate' }))
    renderSuggestions()
    fireEvent.change(screen.getByRole('textbox', { name: 'Liên kết video *' }), { target: { value: 'https://youtu.be/abc' } })
    fireEvent.click(screen.getByRole('button', { name: /Gửi link/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Video này đã được đề xuất')
  })

  it('filter tabs request the matching status (LIVE included)', async () => {
    renderSuggestions()
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
    renderSuggestions()
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
    fireEvent.click(screen.getByRole('tab', { name: /My suggestions/ }))
    expect(await screen.findByRole('heading', { name: 'My suggestions' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Pending' })).toBeInTheDocument()
    expect(await screen.findByText(/Reason: Copyright issue/)).toBeInTheDocument()
  })
})
