import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import i18n from '../../../i18n/index.js'
import SuggestVideoModal from '../SuggestVideoModal.jsx'
import { validateSuggestionUrl, errorKeyFor } from '../suggestionUtils.js'
import { suggestionsApi } from '../../../api/suggestions.js'

vi.mock('../../../api/suggestions.js', () => ({
  suggestionsApi: { submit: vi.fn(), mine: vi.fn() },
}))

const page = (content) => ({ data: { content, totalPages: 1, totalElements: content.length, number: 0, size: 5 } })

function renderModal(props = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const handlers = { onClose: vi.fn(), onPlayVideo: vi.fn(), onOpenHistory: vi.fn() }
  render(
    <QueryClientProvider client={client}>
      <SuggestVideoModal {...handlers} {...props} />
    </QueryClientProvider>,
  )
  return handlers
}

const URL_OK = 'https://www.youtube.com/watch?v=abc'

beforeEach(() => {
  vi.clearAllMocks()
  suggestionsApi.mine.mockResolvedValue(page([]))
})

describe('suggestion url validation', () => {
  it('accepts https YouTube/Facebook hosts only', () => {
    expect(validateSuggestionUrl('')).toBe('urlRequired')
    expect(validateSuggestionUrl('not a url')).toBe('urlInvalid')
    expect(validateSuggestionUrl('http://youtube.com/watch?v=1')).toBe('urlInvalid')
    expect(validateSuggestionUrl('https://vimeo.com/1')).toBe('urlInvalid')
    expect(validateSuggestionUrl('https://evilyoutube.com/1')).toBe('urlInvalid')
    for (const ok of ['https://youtu.be/x', 'https://m.youtube.com/watch?v=1', 'https://www.facebook.com/a/videos/1', 'https://fb.watch/abc']) {
      expect(validateSuggestionUrl(ok)).toBe('')
    }
  })

  it('maps known error codes', () => {
    expect(errorKeyFor('error.suggestion.duplicate')).toBe('errorDuplicate')
    expect(errorKeyFor('error.suggestion.dailyLimit')).toBe('errorDailyLimit')
    expect(errorKeyFor('error.suggestion.invalidUrl')).toBe('urlInvalid')
    expect(errorKeyFor('other')).toBeNull()
  })
})

describe('SuggestVideoModal', () => {
  it('requires a url and rejects non-allowed hosts without calling the API', async () => {
    renderModal()
    fireEvent.click(screen.getByRole('button', { name: 'Gửi đề xuất' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Vui lòng nhập liên kết video.')

    fireEvent.change(screen.getByLabelText('Liên kết video *'), { target: { value: 'https://vimeo.com/1' } })
    fireEvent.click(screen.getByRole('button', { name: 'Gửi đề xuất' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Chỉ hỗ trợ liên kết https')
    expect(suggestionsApi.submit).not.toHaveBeenCalled()
  })

  it('submits, shows success, clears the form and refreshes the recent list', async () => {
    let resolve
    suggestionsApi.submit.mockReturnValue(new Promise(r => { resolve = r }))
    renderModal()
    fireEvent.change(screen.getByLabelText('Liên kết video *'), { target: { value: URL_OK } })
    fireEvent.change(screen.getByLabelText('Tiêu đề'), { target: { value: 'Clip hay' } })
    fireEvent.change(screen.getByLabelText('Ghi chú'), { target: { value: 'rất vui' } })
    suggestionsApi.mine.mockResolvedValue(page([{ id: 1, sourceUrl: URL_OK, title: 'Clip hay', status: 'PENDING', live: false }]))
    fireEvent.click(screen.getByRole('button', { name: 'Gửi đề xuất' }))

    expect(screen.getByRole('button', { name: 'Gửi đề xuất' })).toHaveAttribute('aria-busy', 'true')
    resolve({ data: { id: 1 } })

    expect(await screen.findByText(/Đã gửi đề xuất/)).toBeInTheDocument()
    expect(suggestionsApi.submit).toHaveBeenCalledWith({ url: URL_OK, title: 'Clip hay', note: 'rất vui' })
    expect(screen.getByLabelText('Liên kết video *')).toHaveValue('')
    expect(await screen.findByText('Chờ duyệt')).toBeInTheDocument()
  })

  it.each([
    ['error.suggestion.duplicate', 'Video này đã được đề xuất'],
    ['error.suggestion.dailyLimit', 'giới hạn đề xuất hôm nay'],
    ['error.suggestion.invalidUrl', 'Chỉ hỗ trợ liên kết https'],
  ])('translates %s', async (code, text) => {
    suggestionsApi.submit.mockRejectedValue({ message: 'server text', status: 409, code })
    renderModal()
    fireEvent.change(screen.getByLabelText('Liên kết video *'), { target: { value: URL_OK } })
    fireEvent.click(screen.getByRole('button', { name: 'Gửi đề xuất' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(text)
  })

  it('falls back to the server message for unknown codes', async () => {
    suggestionsApi.submit.mockRejectedValue({ message: 'Máy chủ bận', status: 500, code: 'error.x' })
    renderModal()
    fireEvent.change(screen.getByLabelText('Liên kết video *'), { target: { value: URL_OK } })
    fireEvent.click(screen.getByRole('button', { name: 'Gửi đề xuất' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Máy chủ bận')
  })

  it('lists recent suggestions with chips, reject reason, and plays a live one', async () => {
    suggestionsApi.mine.mockResolvedValue(page([
      { id: 1, sourceUrl: 'https://youtu.be/1', title: 'Một', status: 'PENDING', live: false },
      { id: 2, sourceUrl: 'https://youtu.be/2', title: 'Hai', status: 'REJECTED', rejectReason: 'COPYRIGHT' },
      { id: 3, sourceUrl: 'https://youtu.be/3', title: 'Ba', status: 'APPROVED', live: false },
      { id: 4, sourceUrl: 'https://youtu.be/4', title: 'Bốn', status: 'APPROVED', live: true, liveVideoId: 42 },
    ]))
    const { onPlayVideo, onOpenHistory } = renderModal()
    expect(await screen.findByText('Chờ duyệt')).toBeInTheDocument()
    expect(screen.getByText('Bị từ chối')).toBeInTheDocument()
    expect(screen.getByText('Lý do: Vi phạm bản quyền')).toBeInTheDocument()
    expect(screen.getByText('Đã duyệt')).toBeInTheDocument()
    expect(screen.getByText('Đã lên app')).toBeInTheDocument()
    expect(suggestionsApi.mine).toHaveBeenCalledWith('', 0, 5)

    expect(screen.getAllByRole('button', { name: /^Xem video/ })).toHaveLength(1)
    fireEvent.click(screen.getByRole('button', { name: 'Xem video Bốn' }))
    expect(onPlayVideo).toHaveBeenCalledWith(42)

    fireEvent.click(screen.getByRole('button', { name: 'Xem tất cả' }))
    expect(onOpenHistory).toHaveBeenCalled()
  })

  it('renders in English', async () => {
    await i18n.changeLanguage('en')
    renderModal()
    expect(await screen.findByText('Recent suggestions')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Send suggestion' })).toBeInTheDocument()
    expect(screen.getByText('You have no suggestions yet.')).toBeInTheDocument()
  })
})
