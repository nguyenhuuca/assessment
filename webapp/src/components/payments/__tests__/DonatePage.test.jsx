import React from 'react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import '../../../i18n/index.js'
import i18n from '../../../i18n/index.js'
import DonatePage from '../DonatePage.jsx'
import { paymentErrorKey } from '../../../utils/paymentErrors.js'
import { orderRefetchInterval } from '../../../hooks/usePayments.js'

let auth
vi.mock('../../../hooks/useAuth.js', () => ({ useAuth: () => auth }))
vi.mock('../../../api/auth.js', () => ({
  authApi: { me: vi.fn(() => Promise.resolve({ data: { premium: true, premiumUntil: '2099-01-01T00:00:00Z' } })) },
}))
vi.mock('../../../api/payments.js', () => ({
  getPlans: vi.fn(),
  createOrder: vi.fn(),
  getOrder: vi.fn(),
}))
import * as paymentsApi from '../../../api/payments.js'
import { authApi } from '../../../api/auth.js'

const future = () => new Date(Date.now() + 15 * 60 * 1000).toISOString()
const order = (over = {}) => ({
  code: 'FM123', type: 'DONATION', status: 'PENDING', amount: 50000, expiresAt: future(),
  qrUrl: 'https://img.vietqr.io/x.png', bankCode: 'VCB', bankAccount: '0123456789',
  accountName: 'NGUYEN VAN A', memo: 'FM123', ...over,
})

function renderPage(props) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><DonatePage {...props} /></QueryClientProvider>)
}

beforeEach(async () => {
  await i18n.changeLanguage('en')
  auth = { isLoggedIn: false, user: null, updateUser: vi.fn() }
  vi.clearAllMocks()
  paymentsApi.getPlans.mockResolvedValue({ data: [{ id: 1, code: 'M1', name: 'Premium 1 month', durationDays: 30, priceVnd: 49000 }] })
})
afterEach(() => { vi.useRealTimers() })

describe('orderRefetchInterval', () => {
  it('polls only while PENDING and before expiry', () => {
    expect(orderRefetchInterval({ status: 'PENDING', expiresAt: future() })).toBe(3000)
    expect(orderRefetchInterval({ status: 'PENDING', expiresAt: new Date(Date.now() - 1000).toISOString() })).toBe(false)
    for (const status of ['PAID', 'EXPIRED', 'UNDERPAID', 'CANCELLED', 'RESOLVED']) {
      expect(orderRefetchInterval({ status, expiresAt: future() })).toBe(false)
    }
  })
})

describe('paymentErrorKey', () => {
  it('maps backend codes and falls back to generic', () => {
    expect(paymentErrorKey({ code: 'error.payment.amountTooLow' })).toBe('errors.amountTooLow')
    expect(paymentErrorKey({ code: 'error.auth.required' })).toBe('errors.authRequired')
    expect(paymentErrorKey({ code: 'nope' })).toBe('errors.generic')
    expect(paymentErrorKey(null)).toBe('errors.generic')
  })
})

describe('DonatePage donate flow', () => {
  it('creates a donation and shows the QR with memo and bank details', async () => {
    paymentsApi.createOrder.mockResolvedValue({ data: order() })
    paymentsApi.getOrder.mockResolvedValue({ data: { code: 'FM123', status: 'PENDING', amount: 50000, expiresAt: future() } })
    renderPage()
    fireEvent.change(screen.getByLabelText(/Your name/), { target: { value: 'Lan' } })
    fireEvent.click(screen.getByRole('button', { name: 'Donate' }))

    expect(await screen.findByAltText('VietQR payment code')).toHaveAttribute('src', 'https://img.vietqr.io/x.png')
    expect(screen.getByTestId('pay-memo')).toHaveTextContent('FM123')
    expect(screen.getByTestId('pay-account')).toHaveTextContent('0123456789')
    expect(screen.getByTestId('pay-bank')).toHaveTextContent('VCB')
    expect(screen.getByTestId('pay-holder')).toHaveTextContent('NGUYEN VAN A')
    expect(screen.getByText(/Keep the transfer content unchanged/)).toBeInTheDocument()
    expect(paymentsApi.createOrder).toHaveBeenCalledWith({ type: 'DONATION', amount: 50000, donorName: 'Lan' })
  })

  it('blocks custom amounts below the minimum', () => {
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: 'Other amount' }))
    fireEvent.change(screen.getByLabelText('Amount (VND)', { selector: 'input' }), { target: { value: '5000' } })
    expect(screen.getByRole('button', { name: 'Donate' })).toBeDisabled()
    fireEvent.change(screen.getByLabelText('Amount (VND)', { selector: 'input' }), { target: { value: '10000' } })
    expect(screen.getByRole('button', { name: 'Donate' })).toBeEnabled()
  })

  it('maps a backend error code to a friendly message', async () => {
    paymentsApi.createOrder.mockRejectedValue({ code: 'error.payment.notConfigured', message: 'x' })
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: 'Donate' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Payments are not available yet')
  })

  it('shows the thank-you screen when the order is PAID and stops polling', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    paymentsApi.createOrder.mockResolvedValue({ data: order() })
    paymentsApi.getOrder
      .mockResolvedValueOnce({ data: { code: 'FM123', status: 'PENDING', amount: 50000, expiresAt: future() } })
      .mockResolvedValue({ data: { code: 'FM123', status: 'PAID', amount: 50000, expiresAt: future() } })
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: 'Donate' }))
    await screen.findByAltText('VietQR payment code')

    await act(async () => { await vi.advanceTimersByTimeAsync(3100) })
    expect(await screen.findByText('Thank you!')).toBeInTheDocument()

    const calls = paymentsApi.getOrder.mock.calls.length
    await act(async () => { await vi.advanceTimersByTimeAsync(10000) })
    expect(paymentsApi.getOrder.mock.calls.length).toBe(calls)
  })

  it('shows the expired message and stops polling when EXPIRED', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    paymentsApi.createOrder.mockResolvedValue({ data: order() })
    paymentsApi.getOrder.mockResolvedValue({ data: { code: 'FM123', status: 'EXPIRED', amount: 50000, expiresAt: future() } })
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: 'Donate' }))
    expect(await screen.findByText('This code has expired')).toBeInTheDocument()

    const calls = paymentsApi.getOrder.mock.calls.length
    await act(async () => { await vi.advanceTimersByTimeAsync(10000) })
    expect(paymentsApi.getOrder.mock.calls.length).toBe(calls)
    expect(screen.getByRole('button', { name: 'Create a new code' })).toBeInTheDocument()
  })

  it('tells the user an admin will review an UNDERPAID order', async () => {
    paymentsApi.createOrder.mockResolvedValue({ data: order() })
    paymentsApi.getOrder.mockResolvedValue({ data: { code: 'FM123', status: 'UNDERPAID', amount: 50000, expiresAt: future() } })
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: 'Donate' }))
    expect(await screen.findByText(/An admin will review/)).toBeInTheDocument()
  })
})

describe('DonatePage Premium tab', () => {
  it('shows a login prompt and no buy button for guests', async () => {
    renderPage({ initialTab: 'premium' })
    expect(await screen.findByText('Premium 1 month')).toBeInTheDocument()
    expect(screen.getByTestId('pay-login-prompt')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Buy' })).not.toBeInTheDocument()
  })

  it('lets a logged-in user buy a plan and refreshes the user when PAID', async () => {
    auth = { isLoggedIn: true, user: { email: 'a@x.com' }, updateUser: vi.fn() }
    paymentsApi.createOrder.mockResolvedValue({ data: order({ type: 'PREMIUM', amount: 49000 }) })
    paymentsApi.getOrder.mockResolvedValue({ data: { code: 'FM123', status: 'PAID', amount: 49000, expiresAt: future() } })
    renderPage({ initialTab: 'premium' })
    fireEvent.click(await screen.findByRole('button', { name: 'Buy' }))

    expect(await screen.findByText('Premium active')).toBeInTheDocument()
    expect(paymentsApi.createOrder).toHaveBeenCalledWith({ type: 'PREMIUM', planId: 1 })
    await waitFor(() => expect(auth.updateUser).toHaveBeenCalled())
    expect(authApi.me).toHaveBeenCalled()
    expect(auth.updateUser.mock.calls[0][0]).toMatchObject({ premiumUntil: '2099-01-01T00:00:00Z' })
  })
})
