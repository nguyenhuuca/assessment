import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import i18n from '../../../i18n/index.js'
import NotificationBell from '../NotificationBell.jsx'
import { useNotificationStream } from '../../../hooks/useNotifications.js'
import { notificationsApi } from '../../../api/notifications.js'
import { openStream } from '../../../api/sseStream.js'

vi.mock('../../../api/sseStream.js', () => ({ openStream: vi.fn() }))
vi.mock('../../../api/notifications.js', () => ({
  NOTIFICATIONS_STREAM_URL: () => 'http://api/notifications/stream',
  notificationsApi: {
    list: vi.fn(), unreadCount: vi.fn(), markRead: vi.fn(), markAllRead: vi.fn(),
  },
}))
const auth = vi.hoisted(() => ({ user: null }))
vi.mock('../../../hooks/useAuth.js', () => ({
  useAuth: () => ({ jwt: 'jwt-1', isLoggedIn: true, user: auth.user }),
}))

const reply = (over = {}) => ({
  id: 'n1', type: 'COMMENT_REPLY', videoId: 7, commentId: 'c9', actorDisplay: 'bob', actorCount: 1,
  snippet: 'hello <b>there</b>', reason: null, read: false, updatedAt: new Date().toISOString(), ...over,
})
const removed = (over = {}) => ({
  id: 'n2', type: 'COMMENT_REMOVED', videoId: 7, commentId: 'c1', actorDisplay: null, actorCount: 1,
  snippet: null, reason: 'SPAM', read: true, updatedAt: new Date().toISOString(), ...over,
})

function Harness({ onOpen }) {
  useNotificationStream()
  return <NotificationBell onOpenNotification={onOpen} />
}

function renderBell(onOpen = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <Harness onOpen={onOpen} />
    </QueryClientProvider>,
  )
  return { client, onOpen }
}

let emit
beforeEach(() => {
  vi.clearAllMocks()
  auth.user = null
  emit = null
  openStream.mockImplementation(({ onEvent, signal }) => {
    emit = onEvent
    return new Promise((_, reject) => signal.addEventListener('abort', () => reject(new Error('aborted'))))
  })
  notificationsApi.unreadCount.mockResolvedValue({ data: { count: 0 } })
  notificationsApi.list.mockResolvedValue({ data: { content: [], totalPages: 0, number: 0 } })
  notificationsApi.markRead.mockResolvedValue(null)
  notificationsApi.markAllRead.mockResolvedValue(null)
})

describe('NotificationBell badge', () => {
  it('shows no badge at zero and updates from a stream event', async () => {
    renderBell()
    expect(await screen.findByRole('button', { name: 'Thông báo, 0 chưa đọc' })).toBeTruthy()
    expect(document.querySelector('.notif-badge')).toBeNull()

    await waitFor(() => expect(emit).toBeTruthy())
    act(() => emit({ event: 'unread', data: '{"unread": 3}' }))
    expect(await screen.findByRole('button', { name: 'Thông báo, 3 chưa đọc' })).toBeTruthy()
    expect(document.querySelector('.notif-badge').textContent).toBe('3')
  })

  it('caps the badge text at 9+ but keeps the real count in the label', async () => {
    renderBell()
    await waitFor(() => expect(emit).toBeTruthy())
    act(() => emit({ event: 'unread', data: '{"unread": 25}' }))
    expect(await screen.findByRole('button', { name: 'Thông báo, 25 chưa đọc' })).toBeTruthy()
    expect(document.querySelector('.notif-badge').textContent).toBe('9+')
  })

  it('shows exactly 9 without the plus', async () => {
    renderBell()
    await waitFor(() => expect(emit).toBeTruthy())
    act(() => emit({ event: 'unread', data: '{"unread": 9}' }))
    await screen.findByRole('button', { name: 'Thông báo, 9 chưa đọc' })
    expect(document.querySelector('.notif-badge').textContent).toBe('9')
  })
})

describe('NotificationDropdown', () => {
  it('shows the empty state', async () => {
    renderBell()
    fireEvent.click(await screen.findByRole('button', { name: /Thông báo/ }))
    expect(await screen.findByText('Chưa có thông báo')).toBeTruthy()
  })

  it('renders reply/removed texts and escapes snippets as plain text', async () => {
    notificationsApi.list.mockResolvedValue({
      data: {
        content: [
          reply(),
          reply({ id: 'n3', actorDisplay: 'amy', actorCount: 3, snippet: 'xin chào' }),
          removed(),
        ],
      },
    })
    renderBell()
    fireEvent.click(await screen.findByRole('button', { name: /Thông báo/ }))

    expect(await screen.findByText('bob đã trả lời bình luận của bạn')).toBeTruthy()
    expect(screen.getByText('amy và 2 người khác đã trả lời bình luận của bạn')).toBeTruthy()
    expect(screen.getByText('Bình luận của bạn đã bị gỡ: Spam, quảng cáo, link lặp lại')).toBeTruthy()
    expect(screen.getByText('hello <b>there</b>')).toBeTruthy()
    expect(document.querySelector('.notif-item-snippet b')).toBeNull()
    expect(document.querySelectorAll('.notif-item.unread')).toHaveLength(2)
  })

  it('limits the list to the latest 10', async () => {
    notificationsApi.list.mockResolvedValue({
      data: { content: Array.from({ length: 15 }, (_, i) => reply({ id: `n${i}`, actorDisplay: `u${i}` })) },
    })
    renderBell()
    fireEvent.click(await screen.findByRole('button', { name: /Thông báo/ }))
    await screen.findByText('u0 đã trả lời bình luận của bạn')
    expect(document.querySelectorAll('.notif-item')).toHaveLength(10)
  })

  it('mark-all-read calls the API and zeroes the badge', async () => {
    notificationsApi.unreadCount.mockResolvedValue({ data: { count: 2 } })
    notificationsApi.list.mockResolvedValue({ data: { content: [reply(), reply({ id: 'n3' })] } })
    renderBell()
    fireEvent.click(await screen.findByRole('button', { name: 'Thông báo, 2 chưa đọc' }))
    await screen.findAllByText('bob đã trả lời bình luận của bạn')
    notificationsApi.unreadCount.mockResolvedValue({ data: { count: 0 } })
    fireEvent.click(screen.getByText('Đánh dấu đã đọc tất cả'))

    await waitFor(() => expect(notificationsApi.markAllRead).toHaveBeenCalledTimes(1))
    expect(await screen.findByRole('button', { name: 'Thông báo, 0 chưa đọc' })).toBeTruthy()
  })

  it('clicking an unread item marks it read, deep-links and closes', async () => {
    notificationsApi.unreadCount.mockResolvedValue({ data: { count: 1 } })
    notificationsApi.list.mockResolvedValue({ data: { content: [reply()] } })
    const { onOpen } = renderBell()
    fireEvent.click(await screen.findByRole('button', { name: 'Thông báo, 1 chưa đọc' }))
    fireEvent.click(await screen.findByText('bob đã trả lời bình luận của bạn'))

    await waitFor(() => expect(notificationsApi.markRead).toHaveBeenCalledWith('n1'))
    expect(onOpen).toHaveBeenCalledWith(expect.objectContaining({ videoId: 7, commentId: 'c9' }))
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('clicking an already-read item deep-links without calling markRead', async () => {
    notificationsApi.list.mockResolvedValue({ data: { content: [removed()] } })
    const { onOpen } = renderBell()
    fireEvent.click(await screen.findByRole('button', { name: /Thông báo/ }))
    fireEvent.click(await screen.findByText(/Bình luận của bạn đã bị gỡ/))
    expect(notificationsApi.markRead).not.toHaveBeenCalled()
    expect(onOpen).toHaveBeenCalledWith(expect.objectContaining({ commentId: 'c1' }))
  })

  it('closes on Escape and on outside click', async () => {
    renderBell()
    const bell = await screen.findByRole('button', { name: /Thông báo/ })
    fireEvent.click(bell)
    expect(await screen.findByRole('dialog')).toBeTruthy()
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('dialog')).toBeNull()

    fireEvent.click(bell)
    expect(await screen.findByRole('dialog')).toBeTruthy()
    fireEvent.mouseDown(document.body)
    expect(screen.queryByRole('dialog')).toBeNull()
  })
})

describe('NotificationDropdown (English)', () => {
  it('renders titles in English with plurals', async () => {
    await i18n.changeLanguage('en')
    notificationsApi.list.mockResolvedValue({
      data: {
        content: [
          reply({ actorDisplay: null }),
          reply({ id: 'n3', actorDisplay: 'amy', actorCount: 3 }),
          reply({ id: 'n4', actorDisplay: 'zed', actorCount: 2 }),
          removed(),
        ],
      },
    })
    renderBell()
    fireEvent.click(await screen.findByRole('button', { name: /Notifications/ }))
    expect(await screen.findByText('Someone replied to your comment')).toBeTruthy()
    expect(screen.getByText('amy and 2 others replied to your comment')).toBeTruthy()
    expect(screen.getByText('zed and 1 other replied to your comment')).toBeTruthy()
    expect(screen.getByText('Your comment was removed: Spam, advertising, repeated links')).toBeTruthy()
  })
})

describe('NotificationDropdown tabs (admins)', () => {
  const closed = (over = {}) => ({
    id: 'a1', type: 'REPORT_CLOSED', videoId: null, commentId: null, actorDisplay: null, actorCount: 1,
    snippet: 'Khó đọc', reason: 'completed', read: false, updatedAt: new Date().toISOString(), ...over,
  })
  const byCategory = (general, admin) => (page, size, category) =>
    Promise.resolve({ data: { content: category === 'ADMIN' ? admin : general } })

  it('a non-admin sees no tabs and requests every type', async () => {
    notificationsApi.list.mockResolvedValue({ data: { content: [reply()] } })
    renderBell()
    fireEvent.click(await screen.findByRole('button', { name: /Thông báo/ }))
    expect(await screen.findByText('bob đã trả lời bình luận của bạn')).toBeTruthy()
    expect(screen.queryByRole('tablist')).toBeNull()
    expect(notificationsApi.list).toHaveBeenCalledWith(0, 20, undefined)
  })

  it('an admin sees General first and Admin on click, each from its category', async () => {
    auth.user = { isAdmin: true }
    notificationsApi.list.mockImplementation(byCategory([reply({ read: true })], [closed()]))
    renderBell()
    fireEvent.click(await screen.findByRole('button', { name: /Thông báo/ }))

    const general = await screen.findByRole('tab', { name: /Chung/ })
    expect(general).toHaveAttribute('aria-selected', 'true')
    expect(await screen.findByText('bob đã trả lời bình luận của bạn')).toBeTruthy()
    expect(screen.queryByText(/Report của bạn đã được đóng/)).toBeNull()
    expect(notificationsApi.list).toHaveBeenCalledWith(0, 20, 'GENERAL')
    expect(notificationsApi.list).toHaveBeenCalledWith(0, 20, 'ADMIN')

    fireEvent.click(screen.getByRole('tab', { name: /Admin/ }))
    expect(await screen.findByText('Report của bạn đã được đóng: Khó đọc (Đã xong)')).toBeTruthy()
    expect(screen.queryByText('bob đã trả lời bình luận của bạn')).toBeNull()
  })

  it('a contributor also gets the Admin tab', async () => {
    auth.user = { role: 'CONTRIBUTOR', isAdmin: false }
    notificationsApi.list.mockImplementation(byCategory([reply({ read: true })], [closed()]))
    renderBell()
    fireEvent.click(await screen.findByRole('button', { name: /Thông báo/ }))
    fireEvent.click(await screen.findByRole('tab', { name: /Admin/ }))
    expect(await screen.findByText('Report của bạn đã được đóng: Khó đọc (Đã xong)')).toBeTruthy()
  })

  it('a tab with an unread item shows a dot; mark-all follows the total unread count', async () => {
    auth.user = { isAdmin: true }
    notificationsApi.unreadCount.mockResolvedValue({ data: { count: 1 } })
    notificationsApi.list.mockImplementation(byCategory([reply({ read: true })], [closed()]))
    renderBell()
    fireEvent.click(await screen.findByRole('button', { name: /Thông báo/ }))

    const adminTab = await screen.findByRole('tab', { name: /Admin/ })
    await waitFor(() => expect(adminTab.querySelector('.notif-tab-dot')).not.toBeNull())
    expect(screen.getByRole('tab', { name: /Chung/ }).querySelector('.notif-tab-dot')).toBeNull()

    // General has nothing unread, but mark-all is global and stays enabled
    const markAll = screen.getByRole('button', { name: 'Đánh dấu đã đọc tất cả' })
    await waitFor(() => expect(markAll).not.toBeDisabled())
    fireEvent.click(markAll)
    await waitFor(() => expect(notificationsApi.markAllRead).toHaveBeenCalled())
  })
})
