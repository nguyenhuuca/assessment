import React from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AdminVideoTable from '../AdminVideoTable.jsx'
import * as adminApi from '../../../api/admin.js'

vi.mock('../../../api/admin.js', () => ({
  getVideos: vi.fn(),
  updateVideoStatus: vi.fn(),
  updateVideoPriority: vi.fn(),
  updateVideoVisibility: vi.fn(),
  deleteVideo: vi.fn(),
}))

const video = (o) => ({ id: 1, title: 'Clip', status: 'PUBLISHED', priority: 0, viewCount: 3, hidden: false, ...o })

function renderTable() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={qc}><AdminVideoTable statusFilter={null} /></QueryClientProvider>)
}

beforeEach(() => {
  vi.clearAllMocks()
  adminApi.updateVideoVisibility.mockResolvedValue({})
})

describe('AdminVideoTable hidden column', () => {
  it('reflects the hidden flag per row', async () => {
    adminApi.getVideos.mockResolvedValue({ data: { content: [video({ id: 1, title: 'Shown' }), video({ id: 2, title: 'Gone', hidden: true })], totalPages: 1, totalElements: 2 } })
    renderTable()
    expect(await screen.findByLabelText('Ẩn Shown')).not.toBeChecked()
    expect(screen.getByLabelText('Ẩn Gone')).toBeChecked()
  })

  it('toggling hides the video through the visibility API', async () => {
    adminApi.getVideos.mockResolvedValue({ data: { content: [video({ id: 7, title: 'Shown' })], totalPages: 1, totalElements: 1 } })
    renderTable()
    fireEvent.click(await screen.findByLabelText('Ẩn Shown'))
    await waitFor(() => expect(adminApi.updateVideoVisibility).toHaveBeenCalledWith(7, true))
  })
})
