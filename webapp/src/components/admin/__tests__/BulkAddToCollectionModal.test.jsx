import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import BulkAddToCollectionModal from '../BulkAddToCollectionModal.jsx'
import { collectionsApi } from '../../../api/collections.js'

vi.mock('../../../hooks/useAuth.js', () => ({ useAuth: () => ({ isLoggedIn: true, user: { isAdmin: false } }) }))
vi.mock('../../../api/collections.js', () => ({
  collectionsApi: { mine: vi.fn(), create: vi.fn(), bulkAddVideos: vi.fn() },
}))

const onClose = vi.fn()
const onDone = vi.fn()

function renderModal(videoIds = [4, 5, 6]) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <BulkAddToCollectionModal videoIds={videoIds} onClose={onClose} onDone={onDone} />
    </QueryClientProvider>,
  )
}

const result = { requested: 3, updated: 3, unchanged: 0, skipped: [], notFound: [] }

beforeEach(() => {
  vi.clearAllMocks()
  collectionsApi.mine.mockResolvedValue({
    data: [
      { id: 'w', name: 'Xem sau', isDefault: true, visibility: 'PRIVATE', canManage: true },
      { id: 'x', name: 'Hài', visibility: 'PRIVATE', canManage: true },
      { id: 'p', name: 'Của người khác', visibility: 'PUBLIC', canManage: false },
    ],
  })
})

describe('BulkAddToCollectionModal', () => {
  it('lists only manageable collections with the video count and a disabled submit', async () => {
    renderModal()
    expect(await screen.findByLabelText('Hài')).toBeInTheDocument()
    expect(screen.getByLabelText('Xem sau')).toBeInTheDocument()
    expect(screen.queryByLabelText('Của người khác')).toBeNull()
    expect(screen.getByText('3 video')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Thêm 3 video' })).toBeDisabled()
  })

  it('allows a single choice and sends all ids to the chosen collection', async () => {
    collectionsApi.bulkAddVideos.mockResolvedValue({ data: result })
    renderModal()
    const funny = await screen.findByLabelText('Hài')
    const later = screen.getByLabelText('Xem sau')
    fireEvent.click(later)
    fireEvent.click(funny)
    expect(funny).toBeChecked()
    expect(later).not.toBeChecked()
    fireEvent.click(screen.getByRole('button', { name: 'Thêm 3 video' }))
    await waitFor(() => expect(collectionsApi.bulkAddVideos).toHaveBeenCalledWith('x', [4, 5, 6]))
    await waitFor(() => expect(onDone).toHaveBeenCalledWith(result))
  })

  it('creates a collection first and then adds the videos to it', async () => {
    collectionsApi.create.mockResolvedValue({ data: { id: 'n', name: 'Mới' } })
    collectionsApi.bulkAddVideos.mockResolvedValue({ data: result })
    renderModal()
    await screen.findByLabelText('Hài')
    fireEvent.change(screen.getByPlaceholderText('Tên collection'), { target: { value: 'Mới' } })
    fireEvent.click(screen.getByRole('button', { name: /Tạo và thêm/ }))
    await waitFor(() => expect(collectionsApi.create).toHaveBeenCalledWith({ name: 'Mới' }))
    await waitFor(() => expect(collectionsApi.bulkAddVideos).toHaveBeenCalledWith('n', [4, 5, 6]))
    await waitFor(() => expect(onDone).toHaveBeenCalledWith(result))
  })

  it('stays open and shows the error when the add fails', async () => {
    collectionsApi.bulkAddVideos.mockRejectedValue(new Error('boom'))
    renderModal()
    fireEvent.click(await screen.findByLabelText('Hài'))
    fireEvent.click(screen.getByRole('button', { name: 'Thêm 3 video' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('boom')
    expect(onDone).not.toHaveBeenCalled()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByLabelText('Hài')).toBeChecked()
  })
})
