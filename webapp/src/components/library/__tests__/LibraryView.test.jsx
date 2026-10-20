import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import i18n from '../../../i18n/index.js'
import LibraryView from '../LibraryView.jsx'
import { collectionsApi } from '../../../api/collections.js'

let auth
vi.mock('../../../hooks/useAuth.js', () => ({ useAuth: () => auth }))
vi.mock('../../../api/collections.js', () => ({
  collectionsApi: {
    mine: vi.fn(), publicList: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn(),
    videos: vi.fn(), videoIds: vi.fn(), addVideo: vi.fn(), removeVideo: vi.fn(), moveVideo: vi.fn(), membership: vi.fn(),
  },
}))
// Capture DndContext's props so a drop can be simulated without real pointer events
let dnd
vi.mock('@dnd-kit/core', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, DndContext: (props) => { dnd = props; return <actual.DndContext {...props} /> } }
})
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
    // The title is visible on the tile, not only in the tooltip
    expect(screen.getByText('Clip')).toHaveClass('explore-reel-title')
    expect(collectionsApi.videos).toHaveBeenCalledWith('p1', 0, expect.any(Number))
    expect(screen.queryByText('Xoá')).toBeNull()
    fireEvent.click(screen.getByLabelText('Clip'))
    expect(await screen.findByTestId('swiper')).toBeTruthy()
  })

  it('styles the back button with the contrasting "back" class, not "secondary"', async () => {
    renderView({ initialCollectionId: 'p1' })
    await screen.findByLabelText('Clip')
    const back = document.querySelector('button.app-btn.back')
    expect(back).toBeTruthy()
    expect(back).not.toHaveClass('secondary')
    expect(back.textContent).toContain('arrow_back')
    const others = [...document.querySelectorAll('button.app-btn.secondary')]
    expect(others.length).toBeGreaterThan(0)
    expect(others).not.toContain(back)
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

describe('LibraryView reorder mode', () => {
  const three = {
    data: { content: [1, 2, 3].map(id => ({ id, title: `V${id}`, fileId: `f${id}` })), totalPages: 1, totalElements: 3 },
  }

  it('hides "Sắp xếp" from guests and from viewers of a public collection', async () => {
    renderView({ initialCollectionId: 'p1' })
    await screen.findByLabelText('Clip')
    expect(screen.queryByRole('button', { name: /Sắp xếp/ })).toBeNull()
  })

  it('shows a handle per video in sort mode and "Xong" leaves it', async () => {
    auth = { isLoggedIn: true, user: { isAdmin: false } }
    collectionsApi.videos.mockResolvedValue(three)
    renderView({ initialCollectionId: 'o' })
    fireEvent.click(await screen.findByRole('button', { name: /Sắp xếp/ }))
    expect(await screen.findByLabelText('Kéo để sắp xếp "V2"')).toBeTruthy()
    expect(screen.getAllByLabelText(/^Kéo để sắp xếp/)).toHaveLength(3)
    expect(screen.queryByLabelText('V1')).toBeNull() // tiles are inert while sorting
    fireEvent.click(screen.getByRole('button', { name: /Xong/ }))
    expect(await screen.findByLabelText('V1')).toBeTruthy()
  })

  it('a drop saves the position relative to the new neighbour', async () => {
    auth = { isLoggedIn: true, user: { isAdmin: false } }
    collectionsApi.videos.mockResolvedValue(three)
    collectionsApi.moveVideo.mockResolvedValue(null)
    renderView({ initialCollectionId: 'o' })
    fireEvent.click(await screen.findByRole('button', { name: /Sắp xếp/ }))
    await screen.findByLabelText('Kéo để sắp xếp "V1"')
    act(() => { dnd.onDragEnd({ active: { id: 1 }, over: { id: 3 } }) })
    await waitFor(() => expect(collectionsApi.moveVideo).toHaveBeenCalledWith('o', 1, 3))
    act(() => { dnd.onDragEnd({ active: { id: 3 }, over: { id: 3 } }) }) // dropped in place
    expect(collectionsApi.moveVideo).toHaveBeenCalledTimes(1)
  })

  it('shows an error and restores the order when saving fails', async () => {
    auth = { isLoggedIn: true, user: { isAdmin: false } }
    collectionsApi.videos.mockResolvedValue(three)
    collectionsApi.moveVideo.mockRejectedValue(new Error('boom'))
    renderView({ initialCollectionId: 'o' })
    fireEvent.click(await screen.findByRole('button', { name: /Sắp xếp/ }))
    await screen.findByLabelText('Kéo để sắp xếp "V1"')
    act(() => { dnd.onDragEnd({ active: { id: 3 }, over: { id: 1 } }) })
    expect(await screen.findByText('Không lưu được thứ tự mới. Vui lòng thử lại.')).toBeTruthy()
    const handles = screen.getAllByLabelText(/^Kéo để sắp xếp/).map(h => h.getAttribute('aria-label'))
    expect(handles).toEqual(['Kéo để sắp xếp "V1"', 'Kéo để sắp xếp "V2"', 'Kéo để sắp xếp "V3"'])
  })
})

describe('LibraryView (English)', () => {
  it('pluralizes video counts and shows the default collection as "Watch later"', async () => {
    await i18n.changeLanguage('en')
    auth = { isLoggedIn: true, user: { email: 'a@b.c' } }
    renderView()
    expect(await screen.findByLabelText('Hài tuyển chọn, 3 videos')).toBeTruthy()
    expect(await screen.findByLabelText('Watch later, 1 video')).toBeTruthy()
    expect(screen.getByLabelText('Của tôi, 2 videos')).toBeTruthy()
    expect(screen.getByText('Featured')).toBeTruthy()
    expect(screen.queryByLabelText(/Xem sau/)).toBeNull()
  })
})
