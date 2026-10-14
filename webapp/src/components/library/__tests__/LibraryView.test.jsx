import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import LibraryView from '../LibraryView.jsx'
import { collectionsApi } from '../../../api/collections.js'

let auth
vi.mock('../../../hooks/useAuth.js', () => ({ useAuth: () => auth }))
vi.mock('../../../api/collections.js', () => ({
  collectionsApi: {
    mine: vi.fn(), publicList: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn(),
    videos: vi.fn(), videoIds: vi.fn(), addVideo: vi.fn(), removeVideo: vi.fn(), membership: vi.fn(),
  },
}))
vi.mock('../AddVideosModal.jsx', () => ({ default: ({ collection }) => <div data-testid="add-videos">{collection.name}</div> }))
vi.mock('../../video/VideoSwiper.jsx', () => ({ default: () => <div data-testid="swiper" /> }))

const pub = { id: 'p1', name: 'Hài tuyển chọn', visibility: 'PUBLIC', itemCount: 3, coverFileId: 'abc', canManage: false }
const watchLater = { id: 'w', name: 'Xem sau', isDefault: true, visibility: 'PRIVATE', itemCount: 1, canManage: true }
const other = { id: 'o', name: 'Của tôi', visibility: 'PRIVATE', itemCount: 2, canManage: true }

function renderView(props = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <LibraryView {...props} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  auth = { isLoggedIn: false, user: null }
  collectionsApi.publicList.mockResolvedValue({ data: [pub] })
  collectionsApi.mine.mockResolvedValue({ data: [other, watchLater] })
  collectionsApi.videos.mockResolvedValue({
    data: { content: [{ id: 1, title: 'Clip', embedLink: 'http://v', fileId: 'f' }], totalPages: 1, totalElements: 1 },
  })
})

describe('LibraryView', () => {
  it('guest sees public collections with cover and a sign-in hint', async () => {
    renderView()
    expect(await screen.findByLabelText('Hài tuyển chọn, 3 video')).toBeTruthy()
    expect(document.querySelector('img[src="https://images.canh-labs.com/abc.jpg"]')).toBeTruthy()
    expect(screen.getByText('Đăng nhập để tạo và lưu video vào collection của bạn')).toBeTruthy()
    expect(collectionsApi.mine).not.toHaveBeenCalled()
    expect(screen.queryByText('Công khai', { selector: 'label' })).toBeNull()
  })

  it('guest can open a collection from a deep link', async () => {
    renderView({ initialCollectionId: 'p1' })
    expect(await screen.findByLabelText('Clip')).toBeTruthy()
    expect(collectionsApi.videos).toHaveBeenCalledWith('p1', 0, expect.any(Number))
    expect(screen.queryByText('Xoá')).toBeNull()
    fireEvent.click(screen.getByLabelText('Clip'))
    expect(await screen.findByTestId('swiper')).toBeTruthy()
  })

  it('pins "Xem sau" first and it cannot be deleted', async () => {
    auth = { isLoggedIn: true, user: { isAdmin: false } }
    renderView()
    await screen.findByLabelText('Xem sau, 1 video')
    const names = screen.getAllByRole('button')
      .map(b => b.getAttribute('aria-label') || '')
      .filter(l => /, \d+ video$/.test(l))
    expect(names.indexOf('Xem sau, 1 video')).toBeLessThan(names.indexOf('Của tôi, 2 video'))
    expect(screen.queryByLabelText('Xoá collection Xem sau')).toBeNull()
    expect(screen.getByLabelText('Xoá collection Của tôi')).toBeTruthy()
  })

  it('deletes a collection after confirmation', async () => {
    auth = { isLoggedIn: true, user: { isAdmin: false } }
    collectionsApi.remove.mockResolvedValue(null)
    renderView()
    fireEvent.click(await screen.findByLabelText('Xoá collection Của tôi'))
    fireEvent.click(await screen.findByRole('button', { name: 'Xoá' }))
    await waitFor(() => expect(collectionsApi.remove).toHaveBeenCalledWith('o'))
  })

  it('shows the Công khai toggle in the create modal only for admins', async () => {
    auth = { isLoggedIn: true, user: { isAdmin: false } }
    const { unmount } = renderView()
    fireEvent.click(await screen.findByRole('button', { name: /Tạo collection/ }))
    expect(screen.queryByRole('checkbox', { name: 'Công khai' })).toBeNull()
    unmount()

    auth = { isLoggedIn: true, user: { isAdmin: true } }
    renderView()
    fireEvent.click(await screen.findByRole('button', { name: /Tạo collection/ }))
    expect(screen.getByRole('checkbox', { name: 'Công khai' })).toBeTruthy()
  })

  it('"Thêm video" opens the search modal only for collections the user can manage', async () => {
    renderView({ initialCollectionId: 'p1' }) // public, guest: read-only
    await screen.findByLabelText('Clip')
    expect(screen.queryByRole('button', { name: /Thêm video/ })).toBeNull()
  })

  it('steps back a page when the current page no longer exists', async () => {
    auth = { isLoggedIn: true, user: { isAdmin: false } }
    // Page 1 emptied out (e.g. its last video was removed elsewhere): the server now reports one page
    let totalPages = 2
    collectionsApi.videos.mockImplementation((_id, page) => {
      if (page === 1) totalPages = 1
      return Promise.resolve({
        data: page === 0
          ? { content: [{ id: 1, title: 'Clip', fileId: 'f' }], totalPages, totalElements: 24 }
          : { content: [], totalPages: 1, totalElements: 24 },
      })
    })
    renderView({ initialCollectionId: 'o' })
    fireEvent.click(await screen.findByRole('button', { name: 'Sau' }))
    await waitFor(() => expect(collectionsApi.videos).toHaveBeenCalledWith('o', 1, expect.any(Number)))
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Sau' })).toBeNull())
    expect(await screen.findByLabelText('Clip')).toBeTruthy()
    expect(screen.queryByText(/chưa có video/)).toBeNull()
  })

  it('"Phát" plays the whole collection right from the card', async () => {
    renderView()
    fireEvent.click(await screen.findByRole('button', { name: 'Phát Hài tuyển chọn' }))
    expect(screen.getByRole('status', { name: 'Đang tải' })).toBeTruthy()
    expect(await screen.findByTestId('swiper')).toBeTruthy()
    expect(collectionsApi.videos).toHaveBeenCalledWith('p1', 0, 50)
    fireEvent.click(screen.getByRole('button', { name: /Quay lại/ }))
    expect(await screen.findByLabelText('Hài tuyển chọn, 3 video')).toBeTruthy()
  })

  it('"Phát" is disabled for an empty collection', async () => {
    collectionsApi.publicList.mockResolvedValue({ data: [{ ...pub, itemCount: 0 }] })
    renderView()
    expect(await screen.findByRole('button', { name: 'Phát Hài tuyển chọn' })).toBeDisabled()
  })

  it('owner can open "Thêm video" from a collection', async () => {
    auth = { isLoggedIn: true, user: { isAdmin: false } }
    renderView({ initialCollectionId: 'o' })
    fireEvent.click(await screen.findByRole('button', { name: /Thêm video/ }))
    expect(screen.getByTestId('add-videos')).toHaveTextContent('Của tôi')
  })
})
