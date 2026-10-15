import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import i18n from '../../../i18n/index.js'
import WatchHistorySection from '../WatchHistorySection.jsx'
import { groupByDay, dayGroupOf, formatClock } from '../watchHistoryUtils.js'
import { watchHistoryApi } from '../../../api/watchHistory.js'
import { settingsApi } from '../../../api/settings.js'

vi.mock('../../../hooks/useAuth.js', () => ({ useAuth: () => ({ isLoggedIn: true, user: { email: 'me@x.com' } }) }))
vi.mock('../../../api/watchHistory.js', () => ({
  watchHistoryApi: { list: vi.fn(), summary: vi.fn(), remove: vi.fn(), clear: vi.fn(), progress: vi.fn() },
}))
vi.mock('../../../api/settings.js', () => ({ settingsApi: { get: vi.fn(), patch: vi.fn() } }))

const hoursAgo = (h) => new Date(Date.now() - h * 3600_000).toISOString()
const localNoon = (daysAgo) => {
  const d = new Date()
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() - daysAgo, 12, 0, 0).toISOString()
}
const item = (videoId, over = {}) => ({
  videoId, title: `Clip ${videoId}`, fileId: `f${videoId}`, viewCount: 1234, watchedAt: new Date().toISOString(),
  positionSec: 15, durationSec: 60, completed: false, ...over,
})
const page = (content, number = 0, totalPages = 1, totalElements = content.length) =>
  ({ data: { content, number, totalPages, totalElements } })

function renderSection(props = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <WatchHistorySection {...props} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  watchHistoryApi.list.mockResolvedValue(page([]))
  watchHistoryApi.remove.mockResolvedValue(null)
  watchHistoryApi.clear.mockResolvedValue(null)
  settingsApi.get.mockResolvedValue({ data: { incognitoEnabled: false } })
  settingsApi.patch.mockResolvedValue({ data: { incognitoEnabled: true } })
})

describe('WatchHistorySection', () => {
  it('groups by local day: Hôm nay, Hôm qua, Trước đó', async () => {
    watchHistoryApi.list.mockResolvedValue(page([
      item(1, { watchedAt: new Date().toISOString() }),
      item(2, { watchedAt: localNoon(1) }),
      item(3, { watchedAt: localNoon(9) }),
    ]))
    renderSection()
    const headings = await screen.findAllByRole('heading', { level: 3 })
    expect(headings.map(h => h.textContent)).toEqual(['Hôm nay', 'Hôm qua', 'Trước đó'])
    const group = (name) => headings.find(h => h.textContent === name).parentElement
    expect(within(group('Hôm qua')).getByText('Clip 2')).toBeInTheDocument()
    expect(within(group('Trước đó')).getByText('Clip 3')).toBeInTheDocument()
  })

  it('card shows views, "Đã xem {time}", a progress bar; completed shows "Đã xem hết" and no bar', async () => {
    watchHistoryApi.list.mockResolvedValue(page([
      item(1, { watchedAt: hoursAgo(2), positionSec: 15, durationSec: 60 }),
      item(2, { positionSec: 59, durationSec: 60, completed: true }),
    ]))
    renderSection()
    const open1 = await screen.findByRole('button', { name: 'Phát Clip 1' })
    expect(within(open1).getByText('1,2 N lượt xem')).toBeInTheDocument()
    expect(within(open1).getByText(/Đã xem .*giờ trước/)).toBeInTheDocument()
    expect(within(open1).getByText('1:00')).toBeInTheDocument()
    const bar = within(open1).getByRole('progressbar')
    expect(bar).toHaveAttribute('aria-valuenow', '25')
    expect(bar.firstChild).toHaveStyle({ width: '25%' })

    const open2 = screen.getByRole('button', { name: 'Phát Clip 2' })
    expect(within(open2).getByText('Đã xem hết')).toBeInTheDocument()
    expect(within(open2).queryByRole('progressbar')).toBeNull()
    expect(open1.querySelector('img')).toHaveAttribute('src', expect.stringContaining('f1.jpg'))
  })

  it('clicking a card resumes at the saved position, or from 0 when completed', async () => {
    watchHistoryApi.list.mockResolvedValue(page([
      item(1, { positionSec: 12 }),
      item(2, { positionSec: 59, completed: true }),
    ]))
    const onPlayVideo = vi.fn()
    renderSection({ onPlayVideo })
    fireEvent.click(await screen.findByRole('button', { name: 'Phát Clip 1' }))
    expect(onPlayVideo).toHaveBeenLastCalledWith(1, { startAt: 12 })
    fireEvent.click(screen.getByRole('button', { name: 'Phát Clip 2' }))
    expect(onPlayVideo).toHaveBeenLastCalledWith(2, { startAt: 0 })
  })

  it('the ⋮ menu removes one entry', async () => {
    watchHistoryApi.list.mockResolvedValue(page([item(7)]))
    renderSection()
    fireEvent.click(await screen.findByRole('button', { name: 'Tuỳ chọn cho Clip 7' }))
    fireEvent.click(screen.getByRole('menuitem', { name: /Xoá khỏi lịch sử/ }))
    await waitFor(() => expect(watchHistoryApi.remove).toHaveBeenCalledWith(7))
    expect(screen.queryByRole('menu')).toBeNull()
  })

  it('"Xoá toàn bộ" asks for confirmation; cancel keeps, confirm clears', async () => {
    watchHistoryApi.list.mockResolvedValue(page([item(1)], 0, 1, 3))
    renderSection()
    await screen.findByText('Clip 1')
    const clearBtn = screen.getByRole('button', { name: /Xoá toàn bộ/ })

    fireEvent.click(clearBtn)
    let dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('3 video sẽ bị xoá')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Hủy' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(watchHistoryApi.clear).not.toHaveBeenCalled()

    fireEvent.click(clearBtn)
    dialog = await screen.findByRole('dialog')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Xoá toàn bộ' }))
    await waitFor(() => expect(watchHistoryApi.clear).toHaveBeenCalledTimes(1))
  })

  it('the pause switch PATCHes incognitoEnabled and shows the paused banner', async () => {
    let stored = false // the server keeps the value, so the refetch after the PATCH sees it
    settingsApi.get.mockImplementation(async () => ({ data: { incognitoEnabled: stored } }))
    settingsApi.patch.mockImplementation(async (body) => { stored = body.incognitoEnabled; return { data: body } })
    renderSection()
    const sw = await screen.findByRole('switch', { name: 'Tạm dừng lịch sử' })
    await waitFor(() => expect(settingsApi.get).toHaveBeenCalled())
    expect(sw).toHaveAttribute('aria-checked', 'false')
    expect(screen.queryByText(/Lịch sử đang tạm dừng/)).toBeNull()
    fireEvent.click(sw)
    await waitFor(() => expect(settingsApi.patch).toHaveBeenCalled())
    expect(settingsApi.patch.mock.calls[0][0]).toEqual({ incognitoEnabled: true })
    expect(await screen.findByText(/Lịch sử đang tạm dừng/)).toBeInTheDocument()
    expect(screen.getByRole('switch', { name: 'Tạm dừng lịch sử' })).toHaveAttribute('aria-checked', 'true')
  })

  it('the paused banner shows when the setting is already on', async () => {
    settingsApi.get.mockResolvedValue({ data: { incognitoEnabled: true } })
    renderSection()
    expect(await screen.findByText(/Lịch sử đang tạm dừng/)).toBeInTheDocument()
  })

  it('search is debounced by 300 ms and searches the trimmed term', async () => {
    renderSection()
    await waitFor(() => expect(watchHistoryApi.list).toHaveBeenCalledWith('', 0, 20))
    const input = screen.getByRole('searchbox', { name: 'Tìm kiếm trong lịch sử xem' })
    fireEvent.change(input, { target: { value: 'c' } })
    fireEvent.change(input, { target: { value: 'ca' } })
    fireEvent.change(input, { target: { value: ' cat ' } })
    expect(watchHistoryApi.list).toHaveBeenCalledTimes(1) // nothing yet
    await waitFor(() => expect(watchHistoryApi.list).toHaveBeenCalledWith('cat', 0, 20))
    expect(watchHistoryApi.list).toHaveBeenCalledTimes(2) // 'c' and 'ca' were never sent
    expect(await screen.findByText('Không có video nào khớp với tìm kiếm.')).toBeInTheDocument()
  })

  it('shows the empty, loading and error states', async () => {
    renderSection()
    expect(screen.getByRole('status')).toHaveTextContent('Đang tải')
    expect(await screen.findByText(/Bạn chưa xem video nào/)).toBeInTheDocument()
  })

  it('shows an error alert when the list fails', async () => {
    watchHistoryApi.list.mockRejectedValue(new Error('boom'))
    renderSection()
    expect(await screen.findByRole('alert')).toHaveTextContent('Không tải được lịch sử xem')
  })

  it('"Xem thêm" appends the next page', async () => {
    watchHistoryApi.list
      .mockResolvedValueOnce(page([item(1)], 0, 2, 2))
      .mockResolvedValueOnce(page([item(2)], 1, 2, 2))
    renderSection()
    await screen.findByText('Clip 1')
    fireEvent.click(screen.getByRole('button', { name: 'Xem thêm' }))
    expect(await screen.findByText('Clip 2')).toBeInTheDocument()
    expect(watchHistoryApi.list).toHaveBeenLastCalledWith('', 1, 20)
    expect(screen.queryByRole('button', { name: 'Xem thêm' })).toBeNull()
  })

  it('renders in English', async () => {
    await i18n.changeLanguage('en')
    watchHistoryApi.list.mockResolvedValue(page([item(1, { watchedAt: hoursAgo(2) }), item(2, { completed: true })], 0, 1, 1))
    renderSection()
    expect(await screen.findByRole('heading', { level: 3, name: 'Today' })).toBeInTheDocument()
    expect(screen.getByRole('switch', { name: 'Pause history' })).toBeInTheDocument()
    expect(screen.getAllByText('1.2K views')).toHaveLength(2)
    expect(screen.getByText('Watched')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Clear all/ }))
    expect(await screen.findByRole('dialog')).toHaveTextContent('1 video will be removed')
  })
})

describe('watchHistoryUtils', () => {
  const now = new Date(2026, 9, 15, 10, 0, 0)
  it('buckets by local calendar day', () => {
    expect(dayGroupOf(new Date(2026, 9, 15, 0, 0, 1), now)).toBe('today')
    expect(dayGroupOf(new Date(2026, 9, 14, 23, 59, 59), now)).toBe('yesterday')
    expect(dayGroupOf(new Date(2026, 9, 14, 0, 0, 0), now)).toBe('yesterday')
    expect(dayGroupOf(new Date(2026, 9, 13, 23, 59, 59), now)).toBe('earlier')
  })
  it('keeps order and drops empty groups', () => {
    const items = [{ watchedAt: new Date(2026, 9, 15, 9).toISOString(), id: 1 }, { watchedAt: new Date(2026, 8, 1).toISOString(), id: 2 }]
    expect(groupByDay(items, now).map(g => [g.key, g.items.map(i => i.id)])).toEqual([['today', [1]], ['earlier', [2]]])
  })
  it('formats clock', () => {
    expect([formatClock(83), formatClock(3725), formatClock(null)]).toEqual(['1:23', '1:02:05', '0:00'])
  })
})
