import React from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AdminPaymentsTab from '../AdminPaymentsTab.jsx'
import { validatePlan } from '../../../utils/validatePlan.js'
import * as api from '../../../api/adminPayments.js'

vi.mock('../../../api/adminPayments.js', () => ({
  getSummary: vi.fn(), getOrders: vi.fn(), getUnmatched: vi.fn(), linkTransaction: vi.fn(), ignoreTransaction: vi.fn(),
  resolveOrder: vi.fn(), markOrderPaid: vi.fn(), cancelOrder: vi.fn(), checkOrderOnSepay: vi.fn(), getPlans: vi.fn(), createPlan: vi.fn(), updatePlan: vi.fn(), setPlanActive: vi.fn(),
}))

const page = (content) => ({ data: { content, totalElements: content.length, totalPages: 1, number: 0, size: 10 } })
const order = (o) => ({ id: 1, code: 'FM1', type: 'PREMIUM', status: 'UNDERPAID', expectedAmount: 50000, paidAmount: 30000, surplus: 0, ...o })
const plan = { id: 7, code: 'MONTH', name: 'Month', durationDays: 30, priceVnd: 50000, active: true, sortOrder: 1 }

let qc
function renderTab() {
  qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const spy = vi.spyOn(qc, 'invalidateQueries')
  render(<QueryClientProvider client={qc}><AdminPaymentsTab /></QueryClientProvider>)
  return spy
}
const openMenu = async (code) => fireEvent.click(await screen.findByRole('button', { name: `Thao tác cho đơn ${code}` }))
const invalidated = (spy) => spy.mock.calls.some(([arg]) => JSON.stringify(arg.queryKey) === JSON.stringify(['admin', 'payments']))

beforeEach(() => {
  vi.clearAllMocks()
  api.getSummary.mockResolvedValue({ data: { donationTotal: 100000, donation30d: 50000, premiumTotal: 200000, premium30d: 0, pendingOrders: 2, underpaidOrders: 1, unmatchedTransactions: 3 } })
  api.getOrders.mockResolvedValue(page([order(), order({ id: 2, code: 'FM2', type: 'DONATION', status: 'PAID', paidAmount: 60000, expectedAmount: 50000, surplus: 10000 })]))
  api.getUnmatched.mockResolvedValue(page([{ sepayId: 99, amount: 50000, content: 'FM1 pay', code: null, gateway: 'VCB', transactionDate: '2026-11-01T00:00:00Z' }]))
  api.getPlans.mockResolvedValue({ data: [plan] })
  api.linkTransaction.mockResolvedValue({ data: {} })
  api.ignoreTransaction.mockResolvedValue({ data: null })
  api.resolveOrder.mockResolvedValue({ data: {} })
  api.markOrderPaid.mockResolvedValue({ data: {} })
  api.cancelOrder.mockResolvedValue({ data: {} })
  api.checkOrderOnSepay.mockResolvedValue({ data: { found: 0, processed: 0, order: { status: 'PENDING' } } })
  api.createPlan.mockResolvedValue({ data: {} })
  api.updatePlan.mockResolvedValue({ data: {} })
  api.setPlanActive.mockResolvedValue({ data: {} })
})

describe('validatePlan', () => {
  it('accepts a valid plan and flags each bad field', () => {
    expect(validatePlan({ code: 'MONTH_1', durationDays: 30, priceVnd: 1 })).toEqual({})
    expect(validatePlan({ code: 'ab', durationDays: 0, priceVnd: -1 })).toEqual({ code: 'codeInvalid', priceVnd: 'priceInvalid', durationDays: 'daysInvalid' })
    expect(validatePlan({ code: 'lower', durationDays: 1, priceVnd: 1 }).code).toBe('codeInvalid')
  })
})

describe('AdminPaymentsTab', () => {
  it('renders summary, orders with surplus and the filters query', async () => {
    renderTab()
    expect(await screen.findByText('FM2')).toBeInTheDocument()
    expect(within(screen.getByTestId('pay-summary')).getByText('3')).toBeInTheDocument()
    expect(screen.getAllByText(/10\.000/).length).toBeGreaterThan(0)
    fireEvent.change(screen.getByLabelText('Lọc theo trạng thái'), { target: { value: 'PAID' } })
    await waitFor(() => expect(api.getOrders).toHaveBeenLastCalledWith({ type: '', status: 'PAID', page: 0, size: 10 }))
    fireEvent.change(screen.getByLabelText('Lọc theo loại'), { target: { value: 'DONATION' } })
    await waitFor(() => expect(api.getOrders).toHaveBeenLastCalledWith({ type: 'DONATION', status: 'PAID', page: 0, size: 10 }))
  })

  it('links an unmatched transfer to an order and invalidates', async () => {
    const spy = renderTab()
    fireEvent.click(await screen.findByText('Gắn vào đơn'))
    fireEvent.change(screen.getByLabelText('Mã đơn'), { target: { value: ' FM1 ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Gắn' }))
    await waitFor(() => expect(api.linkTransaction).toHaveBeenCalledWith(99, 'FM1'))
    await waitFor(() => expect(invalidated(spy)).toBe(true))
  })

  it('shows the server error when linking fails', async () => {
    api.linkTransaction.mockRejectedValue({ message: 'Order cancelled', status: 409 })
    renderTab()
    fireEvent.click(await screen.findByText('Gắn vào đơn'))
    fireEvent.change(screen.getByLabelText('Mã đơn'), { target: { value: 'FM1' } })
    fireEvent.click(screen.getByRole('button', { name: 'Gắn' }))
    expect(await screen.findByText('Order cancelled')).toBeInTheDocument()
  })

  it('ignores an unmatched transfer after confirmation and invalidates', async () => {
    const spy = renderTab()
    fireEvent.click(await screen.findByRole('button', { name: 'Bỏ qua' }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText(/Bỏ qua chuyển khoản 50\.000/)).toBeInTheDocument()
    fireEvent.click(within(dialog).getByRole('button', { name: 'Bỏ qua' }))
    await waitFor(() => expect(api.ignoreTransaction).toHaveBeenCalledWith(99))
    await waitFor(() => expect(invalidated(spy)).toBe(true))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('does not ignore when the confirmation is dismissed', async () => {
    renderTab()
    fireEvent.click(await screen.findByRole('button', { name: 'Bỏ qua' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Hủy' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(api.ignoreTransaction).not.toHaveBeenCalled()
  })

  it('shows the server error when ignoring fails', async () => {
    api.ignoreTransaction.mockRejectedValue({ message: 'Already matched', status: 409 })
    renderTab()
    fireEvent.click(await screen.findByRole('button', { name: 'Bỏ qua' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Bỏ qua' }))
    expect(await screen.findByText('Already matched')).toBeInTheDocument()
  })

  it('resolves an UNDERPAID premium order granting premium by default', async () => {
    const spy = renderTab()
    await openMenu('FM1')
    fireEvent.click(screen.getByRole('menuitem', { name: /Xử lý/ }))
    const dialog = screen.getByRole('dialog')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Xử lý' }))
    await waitFor(() => expect(api.resolveOrder).toHaveBeenCalledWith(1, true))
    await waitFor(() => expect(invalidated(spy)).toBe(true))
  })

  it('can keep the underpaid order as a donation', async () => {
    renderTab()
    await openMenu('FM1')
    fireEvent.click(screen.getByRole('menuitem', { name: /Xử lý/ }))
    const dialog = screen.getByRole('dialog')
    fireEvent.click(within(dialog).getByLabelText('Chỉ ghi nhận là ủng hộ'))
    fireEvent.click(within(dialog).getByRole('button', { name: 'Xử lý' }))
    await waitFor(() => expect(api.resolveOrder).toHaveBeenCalledWith(1, false))
  })

  it('marks a PENDING order paid with the expected amount by default', async () => {
    api.getOrders.mockResolvedValue(page([order({ id: 3, code: 'FM3', status: 'PENDING', paidAmount: 0 })]))
    const spy = renderTab()
    await openMenu('FM3')
    fireEvent.click(screen.getByRole('menuitem', { name: /Xác nhận đã thanh toán/ }))
    const dialog = screen.getByRole('dialog')
    const input = within(dialog).getByLabelText('Số tiền đã nhận (VND)')
    expect(input).toHaveValue(50000)
    // regression: each keystroke re-rendered the modal and moved focus to the confirm button
    input.focus()
    fireEvent.change(input, { target: { value: '5000' } })
    fireEvent.change(input, { target: { value: '50000' } })
    expect(input).toHaveFocus()
    fireEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận' }))
    await waitFor(() => expect(api.markOrderPaid).toHaveBeenCalledWith(3, 50000))
    await waitFor(() => expect(invalidated(spy)).toBe(true))
  })

  it('blocks mark-paid with an empty or zero amount', async () => {
    api.getOrders.mockResolvedValue(page([order({ id: 4, code: 'FM4', type: 'DONATION', status: 'EXPIRED', expectedAmount: null, paidAmount: 0 })]))
    renderTab()
    await openMenu('FM4')
    fireEvent.click(screen.getByRole('menuitem', { name: /Xác nhận đã thanh toán/ }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Nhập số tiền nguyên lớn hơn 0')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận' }))
    fireEvent.change(within(dialog).getByLabelText('Số tiền đã nhận (VND)'), { target: { value: '0' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận' }))
    expect(api.markOrderPaid).not.toHaveBeenCalled()
    fireEvent.change(within(dialog).getByLabelText('Số tiền đã nhận (VND)'), { target: { value: '20000' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận' }))
    await waitFor(() => expect(api.markOrderPaid).toHaveBeenCalledWith(4, 20000))
  })

  it('cancels a PENDING order after confirmation', async () => {
    api.getOrders.mockResolvedValue(page([order({ id: 3, code: 'FM3', status: 'PENDING', paidAmount: 0 })]))
    renderTab()
    await openMenu('FM3')
    fireEvent.click(screen.getByRole('menuitem', { name: /Huỷ đơn/ }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Huỷ đơn' }))
    await waitFor(() => expect(api.cancelOrder).toHaveBeenCalledWith(3))
  })

  it('shows the server error when cancelling fails', async () => {
    api.getOrders.mockResolvedValue(page([order({ id: 3, code: 'FM3', status: 'PENDING', paidAmount: 0 })]))
    api.cancelOrder.mockRejectedValue({ message: 'Only pending', status: 409 })
    renderTab()
    await openMenu('FM3')
    fireEvent.click(screen.getByRole('menuitem', { name: /Huỷ đơn/ }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Huỷ đơn' }))
    expect(await screen.findByText('Only pending')).toBeInTheDocument()
  })

  it('checks a PENDING order on SePay and reports the recorded transfer', async () => {
    api.getOrders.mockResolvedValue(page([order({ id: 3, code: 'FM3', status: 'PENDING', paidAmount: 0 })]))
    api.checkOrderOnSepay.mockResolvedValue({ data: { found: 1, processed: 1, order: { status: 'PAID' } } })
    const spy = renderTab()
    await openMenu('FM3')
    fireEvent.click(screen.getByRole('menuitem', { name: /Kiểm tra SePay/ }))
    await waitFor(() => expect(api.checkOrderOnSepay).toHaveBeenCalledWith(3))
    expect(await screen.findByRole('status')).toHaveTextContent('Tìm thấy 1 giao dịch cho FM3, ghi nhận mới 1. Trạng thái: Đã thanh toán')
    await waitFor(() => expect(invalidated(spy)).toBe(true))
  })

  it('says so when SePay has no transfer for the order', async () => {
    api.getOrders.mockResolvedValue(page([order({ id: 4, code: 'FM4', status: 'EXPIRED', paidAmount: 0 })]))
    renderTab()
    await openMenu('FM4')
    fireEvent.click(screen.getByRole('menuitem', { name: /Kiểm tra SePay/ }))
    expect(await screen.findByText('Chưa có giao dịch nào mang mã FM4 trên SePay')).toBeInTheDocument()
  })

  it('offers the SePay check on an UNDERPAID order and shows a server error', async () => {
    api.checkOrderOnSepay.mockRejectedValue({ message: 'SePay down', status: 502 })
    renderTab()
    await openMenu('FM1')
    fireEvent.click(screen.getByRole('menuitem', { name: /Kiểm tra SePay/ }))
    expect(await screen.findByText('SePay down')).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('offers no row actions on a PAID order', async () => {
    renderTab()
    await screen.findByText('FM2')
    expect(screen.queryByRole('button', { name: 'Thao tác cho đơn FM2' })).not.toBeInTheDocument()
  })

  it('creates a plan after client validation', async () => {
    const spy = renderTab()
    fireEvent.click(await screen.findByText('Thêm gói'))
    fireEvent.change(screen.getByLabelText('Mã'), { target: { value: 'bad' } })
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    expect(api.createPlan).not.toHaveBeenCalled()
    expect(screen.getAllByRole('alert').length).toBeGreaterThan(0)
    fireEvent.change(screen.getByLabelText('Mã'), { target: { value: 'YEAR_1' } })
    fireEvent.change(screen.getByLabelText('Tên'), { target: { value: 'Year' } })
    fireEvent.change(screen.getByLabelText('Số ngày'), { target: { value: '365' } })
    fireEvent.change(screen.getByLabelText('Giá (VND)'), { target: { value: '500000' } })
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    await waitFor(() => expect(api.createPlan).toHaveBeenCalledWith({ code: 'YEAR_1', name: 'Year', durationDays: 365, priceVnd: 500000, active: true }))
    await waitFor(() => expect(invalidated(spy)).toBe(true))
  })

  it('edits a plan via PUT and toggles active via PATCH', async () => {
    const spy = renderTab()
    fireEvent.click(await screen.findByLabelText('Sửa gói MONTH'))
    fireEvent.change(screen.getByLabelText('Giá (VND)'), { target: { value: '60000' } })
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    await waitFor(() => expect(api.updatePlan).toHaveBeenCalledWith(7, { code: 'MONTH', name: 'Month', durationDays: 30, priceVnd: 60000, active: true, sortOrder: 1 }))
    fireEvent.click(await screen.findByLabelText('Bật/tắt gói MONTH'))
    await waitFor(() => expect(api.setPlanActive).toHaveBeenCalledWith(7, false))
    await waitFor(() => expect(invalidated(spy)).toBe(true))
  })
})
