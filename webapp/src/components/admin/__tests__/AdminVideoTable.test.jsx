import React from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AdminVideoTable from '../AdminVideoTable.jsx'
import * as adminApi from '../../../api/admin.js'
import { collectionsApi } from '../../../api/collections.js'

vi.mock('../../../api/admin.js', () => ({
  getVideos: vi.fn(),
  updateVideoStatus: vi.fn(),
  updateVideoPriority: vi.fn(),
  updateVideoVisibility: vi.fn(),
  deleteVideo: vi.fn(),
  enableVideoHls: vi.fn(),
  disableVideoHls: vi.fn(),
  bulkVideos: vi.fn(),
}))

vi.mock('../../../api/collections.js', () => ({
  collectionsApi: { mine: vi.fn(), create: vi.fn(), bulkAddVideos: vi.fn(), membership: vi.fn() },
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

describe('AdminVideoTable actions column', () => {
  it('pins the ⋮ menu column to the right edge so a wide table cannot push it out of view', async () => {
    adminApi.getVideos.mockResolvedValue({ data: { content: [video({ id: 3, title: 'Wide' })], totalPages: 1, totalElements: 1 } })
    renderTable()
    const menuBtn = await screen.findByLabelText('Thao tác cho Wide')
    expect(screen.getByRole('table')).toHaveClass('sticky-actions')
    const row = menuBtn.closest('tr')
    expect(row.lastElementChild).toContainElement(menuBtn)
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

describe('AdminVideoTable bulk actions', () => {
  const rows = [video({ id: 1, title: 'One' }), video({ id: 2, title: 'Two' })]
  const page = (totalPages = 1) => ({ data: { content: rows, totalPages, totalElements: 2 } })
  const ok = (o) => ({ data: { requested: 2, updated: 2, unchanged: 0, skipped: [], notFound: [], ...o } })

  async function selectPage() {
    renderTable()
    fireEvent.click(await screen.findByLabelText('Chọn tất cả trên trang'))
  }

  beforeEach(() => {
    adminApi.getVideos.mockResolvedValue(page())
    adminApi.bulkVideos.mockResolvedValue(ok())
  })

  it('shows no bar until something is selected and marks the header partial', async () => {
    renderTable()
    fireEvent.click(await screen.findByLabelText('Chọn video 1'))
    expect(screen.getByRole('toolbar')).toBeInTheDocument()
    expect(screen.getByLabelText('Chọn tất cả trên trang').indeterminate).toBe(true)
  })

  it('changes status of the selected page, shows the notice and clears the selection', async () => {
    await selectPage()
    fireEvent.change(screen.getByLabelText('Đổi trạng thái các video đã chọn'), { target: { value: 'FLAGGED' } })
    await waitFor(() => expect(adminApi.bulkVideos).toHaveBeenCalledWith({ ids: [1, 2], action: 'STATUS', status: 'FLAGGED' }))
    expect(await screen.findByRole('status')).toHaveTextContent('2 đã cập nhật')
    expect(screen.queryByRole('toolbar')).toBeNull()
  })

  it('changes visibility of the selection', async () => {
    renderTable()
    fireEvent.click(await screen.findByLabelText('Chọn video 2'))
    fireEvent.change(screen.getByLabelText('Đổi chế độ hiển thị các video đã chọn'), { target: { value: 'HIDDEN' } })
    await waitFor(() => expect(adminApi.bulkVideos).toHaveBeenCalledWith({ ids: [2], action: 'VISIBILITY', visibility: 'HIDDEN' }))
  })

  it('delete confirm states the count, offers no Drive option and sends deleteLocalFile', async () => {
    await selectPage()
    fireEvent.click(within(screen.getByRole('toolbar')).getByRole('button', { name: 'Xoá' }))
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('2 video')
    const boxes = within(dialog).getAllByRole('checkbox')
    expect(boxes).toHaveLength(1)
    fireEvent.click(boxes[0])
    fireEvent.click(within(dialog).getAllByRole('button', { name: 'Xoá' }).pop())
    await waitFor(() => expect(adminApi.bulkVideos).toHaveBeenCalledWith({ ids: [1, 2], action: 'DELETE', deleteLocalFile: true }))
  })

  it('sends nothing when the bulk delete is cancelled', async () => {
    await selectPage()
    fireEvent.click(within(screen.getByRole('toolbar')).getByRole('button', { name: 'Xoá' }))
    const dialog = await screen.findByRole('dialog')
    fireEvent.click(within(dialog).getAllByRole('button').find(b => b.className.includes('secondary')))
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(adminApi.bulkVideos).not.toHaveBeenCalled()
  })

  it('lists skipped reasons grouped by code, with a fallback for unknown codes', async () => {
    adminApi.bulkVideos.mockResolvedValue(ok({
      updated: 0,
      skipped: [
        { id: 1, code: 'error.conflict', message: 'x' },
        { id: 2, code: 'error.conflict', message: 'y' },
        { id: 3, code: 'weird.code', message: 'z' },
      ],
    }))
    await selectPage()
    fireEvent.change(screen.getByLabelText('Đổi trạng thái các video đã chọn'), { target: { value: 'PENDING' } })
    const notice = await screen.findByRole('status')
    expect(notice).toHaveTextContent('3 bị bỏ qua')
    expect(notice).toHaveTextContent('Xung đột trạng thái, không thể thực hiện (2)')
    expect(notice).toHaveTextContent('Lý do không xác định (1)')
  })

  it('keeps the selection and shows the error when the call fails', async () => {
    adminApi.bulkVideos.mockRejectedValue(new Error('boom'))
    await selectPage()
    fireEvent.change(screen.getByLabelText('Đổi trạng thái các video đã chọn'), { target: { value: 'PENDING' } })
    expect(await screen.findByRole('alert')).toHaveTextContent('boom')
    expect(screen.getByRole('toolbar')).toBeInTheDocument()
    expect(screen.getByLabelText('Chọn video 1')).toBeChecked()
  })

  it('adds the selection to a collection, shows the notice with the skip reason and clears the selection', async () => {
    collectionsApi.mine.mockResolvedValue({ data: [{ id: 'x', name: 'Hài', visibility: 'PRIVATE', canManage: true }] })
    collectionsApi.bulkAddVideos.mockResolvedValue({ data: {
      requested: 2, updated: 1, unchanged: 0, notFound: [],
      skipped: [{ id: 2, code: 'error.collection.videoNotEligible', message: 'x' }],
    } })
    await selectPage()
    fireEvent.click(within(screen.getByRole('toolbar')).getByRole('button', { name: /Thêm vào collection/ }))
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('2 video')
    fireEvent.click(await within(dialog).findByLabelText('Hài'))
    fireEvent.click(within(dialog).getByRole('button', { name: 'Thêm 2 video' }))
    await waitFor(() => expect(collectionsApi.bulkAddVideos).toHaveBeenCalledWith('x', [1, 2]))
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    const notice = await screen.findByRole('status')
    expect(notice).toHaveTextContent('1 đã cập nhật')
    expect(notice).toHaveTextContent('1 bị bỏ qua')
    expect(notice).toHaveTextContent('Video không đủ điều kiện để thêm vào collection (đang ẩn hoặc chưa đăng) (1)')
    expect(screen.queryByRole('toolbar')).toBeNull()
  })

  it('clears the selection when the page changes', async () => {
    adminApi.getVideos.mockResolvedValue(page(2))
    await selectPage()
    expect(screen.getByRole('toolbar')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '2' }))
    await waitFor(() => expect(screen.queryByRole('toolbar')).toBeNull())
  })
})
