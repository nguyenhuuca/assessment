import React from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AdminVideoTable from '../AdminVideoTable.jsx'
import * as adminApi from '../../../api/admin.js'

vi.mock('../../../api/admin.js', () => ({
  getVideos: vi.fn(),
  updateVideoStatus: vi.fn(),
  updateVideoPriority: vi.fn(),
  updateVideoVisibility: vi.fn(),
  deleteVideo: vi.fn(),
  enableVideoHls: vi.fn(),
  disableVideoHls: vi.fn(),
}))

let mockRole = 'ADMIN'
vi.mock('../../../hooks/useAuth.js', () => ({ useAuth: () => ({ user: { role: mockRole } }) }))

const video = (o) => ({ id: 1, title: 'Clip', status: 'PUBLISHED', priority: 0, viewCount: 3, ...o })

function renderTable() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={qc}><AdminVideoTable statusFilter={null} /></QueryClientProvider>)
}

beforeEach(() => {
  vi.clearAllMocks()
  mockRole = 'ADMIN'
  adminApi.updateVideoVisibility.mockResolvedValue({})
})

describe('AdminVideoTable title cell', () => {
  it('exposes the full title as a tooltip so the mobile ellipsis loses nothing', async () => {
    const long = 'A very long video title that is cut with an ellipsis on a phone screen'
    adminApi.getVideos.mockResolvedValue({ data: { content: [video({ id: 1, title: long })], totalPages: 1, totalElements: 1 } })
    renderTable()
    expect(await screen.findByText(long)).toHaveAttribute('title', long)
  })
})

describe('AdminVideoTable delete dialog', () => {
  async function openDialog() {
    adminApi.getVideos.mockResolvedValue({ data: { content: [video({ id: 7, title: 'Shown' })], totalPages: 1, totalElements: 1 } })
    adminApi.deleteVideo.mockResolvedValue({})
    renderTable()
    fireEvent.click(await screen.findByLabelText('Thao tác cho Shown'))
    fireEvent.click(screen.getByRole('menuitem', { name: /Xoá/ }))
    return await screen.findByRole('dialog')
  }
  const confirmBtn = (dialog) => within(dialog).getAllByRole('button', { name: 'Xoá' }).pop()

  it('has both file options unchecked and sends no flags by default', async () => {
    const dialog = await openDialog()
    const boxes = within(dialog).getAllByRole('checkbox')
    expect(boxes).toHaveLength(2)
    boxes.forEach(b => expect(b).not.toBeChecked())
    fireEvent.click(confirmBtn(dialog))
    await waitFor(() => expect(adminApi.deleteVideo).toHaveBeenCalledWith(7, {
      deleteLocalFile: false, deleteDriveFile: false,
    }))
  })

  it('passes the checked options to the API', async () => {
    const dialog = await openDialog()
    const [local, drive] = within(dialog).getAllByRole('checkbox')
    fireEvent.click(local)
    fireEvent.click(drive)
    fireEvent.click(confirmBtn(dialog))
    await waitFor(() => expect(adminApi.deleteVideo).toHaveBeenCalledWith(7, {
      deleteLocalFile: true, deleteDriveFile: true,
    }))
  })

  it('sends nothing when the dialog is cancelled', async () => {
    const dialog = await openDialog()
    fireEvent.click(within(dialog).getAllByRole('checkbox')[1])
    const cancel = within(dialog).getAllByRole('button').find(b => b.className.includes('secondary'))
    fireEvent.click(cancel)
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(adminApi.deleteVideo).not.toHaveBeenCalled()
  })
})

vi.mock('../../library/AddToCollectionModal.jsx', () => ({
  default: ({ video, onClose }) => <div role="dialog" data-testid="atc">modal:{video.id}<button onClick={onClose}>close-atc</button></div>,
}))

describe('AdminVideoTable visibility column', () => {
  const page = (...rows) => ({ data: { content: rows, totalPages: 1, totalElements: rows.length } })

  it('reflects visibility per row and treats a missing visibility as FEED', async () => {
    adminApi.getVideos.mockResolvedValue(page(
      video({ id: 1, title: 'Shown', visibility: 'FEED' }),
      video({ id: 2, title: 'Gone', visibility: 'HIDDEN' }),
      video({ id: 3, title: 'Coll', visibility: 'COLLECTION_ONLY' }),
      video({ id: 5, title: 'OldShown' }),
    ))
    renderTable()
    expect(await screen.findByLabelText('Chế độ hiển thị của Shown')).toHaveValue('FEED')
    expect(screen.getByLabelText('Chế độ hiển thị của Gone')).toHaveValue('HIDDEN')
    expect(screen.getByLabelText('Chế độ hiển thị của Coll')).toHaveValue('COLLECTION_ONLY')
    expect(screen.getByLabelText('Chế độ hiển thị của OldShown')).toHaveValue('FEED')
  })

  // Regression: a separate badge next to the select duplicated it and squeezed the title column
  it('styles only HIDDEN rows as hidden and marks COLLECTION_ONLY on the select, without a badge', async () => {
    adminApi.getVideos.mockResolvedValue(page(
      video({ id: 2, title: 'Gone', visibility: 'HIDDEN' }),
      video({ id: 3, title: 'Coll', visibility: 'COLLECTION_ONLY' }),
    ))
    renderTable()
    const gone = (await screen.findByText('Gone')).closest('tr')
    const coll = screen.getByText('Coll').closest('tr')
    expect(gone).toHaveClass('is-hidden')
    expect(coll).not.toHaveClass('is-hidden')
    expect(screen.getByLabelText('Chế độ hiển thị của Coll')).toHaveClass('is-collection-only')
    expect(screen.getByLabelText('Chế độ hiển thị của Gone')).not.toHaveClass('is-collection-only')
    expect(within(coll).queryByText('COLLECTION ONLY')).toBeNull()
  })

  it('warns in the tooltip that collection-only is not private', async () => {
    adminApi.getVideos.mockResolvedValue(page(video({ id: 3, title: 'Coll', visibility: 'COLLECTION_ONLY' })))
    renderTable()
    expect(await screen.findByLabelText('Chế độ hiển thị của Coll')).toHaveAttribute('title', expect.stringContaining('không phải riêng tư'))
  })

  it('sends the chosen visibility through the API', async () => {
    adminApi.getVideos.mockResolvedValue(page(video({ id: 7, title: 'Shown', visibility: 'FEED' })))
    renderTable()
    fireEvent.change(await screen.findByLabelText('Chế độ hiển thị của Shown'), { target: { value: 'COLLECTION_ONLY' } })
    await waitFor(() => expect(adminApi.updateVideoVisibility).toHaveBeenCalledWith(7, 'COLLECTION_ONLY'))
  })

  it('marks the row control busy while the visibility update is in flight', async () => {
    adminApi.getVideos.mockResolvedValue(page(video({ id: 7, title: 'Shown' })))
    adminApi.updateVideoVisibility.mockReturnValue(new Promise(() => {}))
    renderTable()
    const sel = await screen.findByLabelText('Chế độ hiển thị của Shown')
    fireEvent.change(sel, { target: { value: 'HIDDEN' } })
    await waitFor(() => expect(sel).toHaveAttribute('aria-busy', 'true'))
    expect(sel).toBeDisabled()
  })

  it('Add to collection opens the modal for that video only', async () => {
    adminApi.getVideos.mockResolvedValue(page(video({ id: 7, title: 'Shown' }), video({ id: 8, title: 'Other' })))
    renderTable()
    fireEvent.click(await screen.findByLabelText('Thao tác cho Other'))
    fireEvent.click(screen.getByRole('menuitem', { name: /Thêm vào collection/ }))
    expect(screen.getByTestId('atc')).toHaveTextContent('modal:8')
    fireEvent.click(screen.getByText('close-atc'))
    expect(screen.queryByTestId('atc')).toBeNull()
  })
})

describe('AdminVideoTable HLS column', () => {
  const page = (...rows) => ({ data: { content: rows, totalPages: 1, totalElements: rows.length } })

  it('shows a status badge for Drive rows and a dash for rows without hlsStatus', async () => {
    adminApi.getVideos.mockResolvedValue(page(
      video({ id: 1, title: 'Drive', hlsStatus: 'READY' }),
      video({ id: 2, title: 'Local', hlsStatus: null }),
    ))
    renderTable()
    const badge = await screen.findByText('SẴN SÀNG')
    expect(badge).toHaveClass('admin-badge', 'hls-ready')
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(1)
  })

  it('shows the localized error reason as a tooltip on ERROR', async () => {
    adminApi.getVideos.mockResolvedValue(page(video({ id: 1, hlsStatus: 'ERROR', hlsError: 'DISK_FULL' })))
    renderTable()
    const badge = await screen.findByText('LỖI')
    expect(badge).toHaveAttribute('title', 'Không đủ dung lượng đĩa cho output HLS')
  })

  it('hides the toggle from ADMIN', async () => {
    adminApi.getVideos.mockResolvedValue(page(video({ id: 1, hlsStatus: 'NONE' })))
    renderTable()
    await screen.findByText('TẮT')
    expect(screen.queryByLabelText('Bật HLS streaming cho video này')).toBeNull()
  })

  it('lets SUPER_ADMIN enable HLS', async () => {
    mockRole = 'SUPER_ADMIN'
    adminApi.getVideos.mockResolvedValue(page(video({ id: 4, hlsStatus: 'NONE' })))
    adminApi.enableVideoHls.mockResolvedValue({})
    renderTable()
    fireEvent.click(await screen.findByLabelText('Bật HLS streaming cho video này'))
    await waitFor(() => expect(adminApi.enableVideoHls).toHaveBeenCalledWith(4))
  })

  it('lets SUPER_ADMIN disable HLS when it is on', async () => {
    mockRole = 'SUPER_ADMIN'
    adminApi.getVideos.mockResolvedValue(page(video({ id: 4, hlsStatus: 'READY' })))
    adminApi.disableVideoHls.mockResolvedValue({})
    renderTable()
    fireEvent.click(await screen.findByLabelText('Tắt HLS và xoá các file đã convert'))
    await waitFor(() => expect(adminApi.disableVideoHls).toHaveBeenCalledWith(4))
  })

  it('lets SUPER_ADMIN disable HLS while the row is PROCESSING (cancel a stuck job)', async () => {
    mockRole = 'SUPER_ADMIN'
    adminApi.getVideos.mockResolvedValue(page(video({ id: 4, hlsStatus: 'PROCESSING' })))
    adminApi.disableVideoHls.mockResolvedValue({})
    renderTable()
    const box = await screen.findByLabelText('Tắt HLS và xoá các file đã convert')
    expect(box).not.toBeDisabled()
    fireEvent.click(box)
    await waitFor(() => expect(adminApi.disableVideoHls).toHaveBeenCalledWith(4))
  })

  it('polls every 5 s only while a row is PENDING or PROCESSING', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      adminApi.getVideos.mockResolvedValue(page(video({ id: 1, hlsStatus: 'PROCESSING' })))
      renderTable()
      await screen.findByText('ĐANG CONVERT')
      const calls = adminApi.getVideos.mock.calls.length
      await vi.advanceTimersByTimeAsync(5100)
      expect(adminApi.getVideos.mock.calls.length).toBeGreaterThan(calls)

      adminApi.getVideos.mockResolvedValue(page(video({ id: 1, hlsStatus: 'READY' })))
      await vi.advanceTimersByTimeAsync(5100)
      await screen.findByText('SẴN SÀNG')
      const settled = adminApi.getVideos.mock.calls.length
      await vi.advanceTimersByTimeAsync(15000)
      expect(adminApi.getVideos.mock.calls.length).toBe(settled)
    } finally {
      vi.useRealTimers()
    }
  })
})
