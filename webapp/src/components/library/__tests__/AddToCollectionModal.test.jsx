import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AddToCollectionModal from '../AddToCollectionModal.jsx'
import { collectionsApi } from '../../../api/collections.js'

let auth
vi.mock('../../../hooks/useAuth.js', () => ({ useAuth: () => auth }))
vi.mock('../../../api/collections.js', () => ({
  collectionsApi: {
    mine: vi.fn(), create: vi.fn(), addVideo: vi.fn(), removeVideo: vi.fn(), membership: vi.fn(),
  },
}))

function renderModal() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AddToCollectionModal video={{ id: 9 }} onClose={vi.fn()} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  auth = { isLoggedIn: true, user: { isAdmin: false } }
  collectionsApi.mine.mockResolvedValue({
    data: [
      { id: 'w', name: 'Xem sau', isDefault: true, visibility: 'PRIVATE', canManage: true },
      { id: 'x', name: 'Hài', visibility: 'PRIVATE', canManage: true },
    ],
  })
  collectionsApi.membership.mockResolvedValue({ data: ['x'] })
})

describe('AddToCollectionModal', () => {
  it('shows a sign-in hint for guests and does not fetch', () => {
    auth = { isLoggedIn: false, user: null }
    renderModal()
    expect(screen.getByText('Đăng nhập để lưu video vào collection')).toBeTruthy()
    expect(collectionsApi.mine).not.toHaveBeenCalled()
  })

  it('checks saved collections and toggles add / remove', async () => {
    collectionsApi.addVideo.mockResolvedValue(null)
    collectionsApi.removeVideo.mockResolvedValue(null)
    renderModal()
    const watchLater = await screen.findByLabelText('Xem sau')
    const funny = screen.getByLabelText('Hài')
    await waitFor(() => expect(funny.checked).toBe(true))
    expect(watchLater.checked).toBe(false)

    fireEvent.click(watchLater)
    await waitFor(() => expect(collectionsApi.addVideo).toHaveBeenCalledWith('w', 9))
    fireEvent.click(funny)
    await waitFor(() => expect(collectionsApi.removeVideo).toHaveBeenCalledWith('x', 9))
  })

  it('creates a collection inline and adds the video', async () => {
    collectionsApi.create.mockResolvedValue({ data: { id: 'n' } })
    collectionsApi.addVideo.mockResolvedValue(null)
    renderModal()
    await screen.findByLabelText('Xem sau')
    fireEvent.change(screen.getByLabelText('Tạo collection mới'), { target: { value: '  Mới  ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Tạo và lưu' }))
    await waitFor(() => expect(collectionsApi.create).toHaveBeenCalledWith({ name: 'Mới' }))
    await waitFor(() => expect(collectionsApi.addVideo).toHaveBeenCalledWith('n', 9))
  })

  it('rejects an empty name and shows API errors', async () => {
    renderModal()
    await screen.findByLabelText('Xem sau')
    fireEvent.click(screen.getByRole('button', { name: 'Tạo và lưu' }))
    expect(await screen.findByText('Vui lòng nhập tên collection')).toBeTruthy()
    expect(collectionsApi.create).not.toHaveBeenCalled()

    collectionsApi.create.mockRejectedValue({ message: 'Tên đã tồn tại', status: 409 })
    fireEvent.change(screen.getByLabelText('Tạo collection mới'), { target: { value: 'Hài' } })
    fireEvent.click(screen.getByRole('button', { name: 'Tạo và lưu' }))
    const alert = await screen.findByText('Tên đã tồn tại')
    expect(alert.className).toContain('app-alert')
  })

  it('shows the public badge and toggle only for admins', async () => {
    auth = { isLoggedIn: true, user: { isAdmin: true } }
    collectionsApi.mine.mockResolvedValue({
      data: [{ id: 'p', name: 'Nổi bật', visibility: 'PUBLIC', canManage: true }],
    })
    renderModal()
    await screen.findByText('Nổi bật')
    expect(screen.getAllByLabelText('Công khai').length).toBeGreaterThan(0)
    expect(screen.getByRole('checkbox', { name: 'Công khai' })).toBeTruthy()
  })
})
