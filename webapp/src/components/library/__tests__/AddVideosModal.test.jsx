import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AddVideosModal from '../AddVideosModal.jsx'
import { normalizeForSearch } from '../collectionUtils.js'
import { collectionsApi } from '../../../api/collections.js'
import { useVideos } from '../../../hooks/useVideos.js'

vi.mock('../../../hooks/useVideos.js', () => ({ useVideos: vi.fn() }))
vi.mock('../../../api/collections.js', () => ({
  collectionsApi: {
    mine: vi.fn(), publicList: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn(),
    videos: vi.fn(), addVideo: vi.fn(), removeVideo: vi.fn(), membership: vi.fn(),
  },
}))

const VIDEOS = [
  { id: 1, title: 'Mèo hài hước', fileId: 'f1' },
  { id: 2, title: 'Chó chạy', fileId: 'f2' },
  { id: 3, title: 'Hài kịch Tết', fileId: 'f3' },
]
const collection = { id: 'c1', name: 'Tuyển tập' }

function renderModal(props = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const onClose = vi.fn()
  render(
    <QueryClientProvider client={client}>
      <AddVideosModal collection={collection} onClose={onClose} {...props} />
    </QueryClientProvider>,
  )
  return onClose
}

beforeEach(() => {
  vi.clearAllMocks()
  useVideos.mockReturnValue({ data: VIDEOS, isLoading: false })
  collectionsApi.addVideo.mockResolvedValue(null)
  collectionsApi.removeVideo.mockResolvedValue(null)
})

describe('AddVideosModal', () => {
  it('normalizes accents and đ for search', () => {
    expect(normalizeForSearch('Hài Hước Đà Nẵng')).toBe('hai huoc da nang')
  })

  it('filters by title without accents, all words must match', () => {
    renderModal()
    fireEvent.change(screen.getByLabelText('Tìm video'), { target: { value: 'hai' } })
    expect(screen.getByText('Mèo hài hước')).toBeInTheDocument()
    expect(screen.getByText('Hài kịch Tết')).toBeInTheDocument()
    expect(screen.queryByText('Chó chạy')).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Tìm video'), { target: { value: 'hai tet' } })
    expect(screen.queryByText('Mèo hài hước')).not.toBeInTheDocument()
    expect(screen.getByText('Hài kịch Tết')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Tìm video'), { target: { value: 'zzz' } })
    expect(screen.getByRole('status')).toHaveTextContent('Không tìm thấy video nào')
  })

  it('adds a video, then removing it again calls the remove API', async () => {
    renderModal()
    fireEvent.click(screen.getByRole('button', { name: 'Thêm "Chó chạy"' }))
    await waitFor(() => expect(collectionsApi.addVideo).toHaveBeenCalledWith('c1', 2))
    const added = await screen.findByRole('button', { name: 'Bỏ "Chó chạy"' })
    expect(added).toHaveAttribute('aria-pressed', 'true')

    fireEvent.click(added)
    await waitFor(() => expect(collectionsApi.removeVideo).toHaveBeenCalledWith('c1', 2))
    expect(await screen.findByRole('button', { name: 'Thêm "Chó chạy"' })).toBeInTheDocument()
  })

  it('marks videos already in the collection', () => {
    renderModal({ existingIds: [1] })
    expect(screen.getByRole('button', { name: 'Bỏ "Mèo hài hước"' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Thêm "Hài kịch Tết"' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('shows the API error and keeps the video un-added', async () => {
    collectionsApi.addVideo.mockRejectedValue({ message: 'Collection is full (500 videos)', status: 409 })
    renderModal()
    fireEvent.click(screen.getByRole('button', { name: 'Thêm "Chó chạy"' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Collection is full')
    expect(screen.getByRole('button', { name: 'Thêm "Chó chạy"' })).toBeInTheDocument()
  })

  it('closes on Xong and Escape', () => {
    const onClose = renderModal()
    fireEvent.click(screen.getByRole('button', { name: 'Xong' }))
    fireEvent.keyDown(window, { key: 'Escape' })
    expect(onClose).toHaveBeenCalledTimes(2)
  })
})
