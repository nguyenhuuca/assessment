import React from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AdminAccountTable from '../AdminAccountTable.jsx'
import * as adminApi from '../../../api/admin.js'

vi.mock('../../../api/admin.js', () => ({
  getAccounts: vi.fn(),
  updateAccountRole: vi.fn(),
  updateAccountPermissions: vi.fn(),
  deleteAccount: vi.fn(),
}))
vi.mock('../../../hooks/useAuth.js', () => ({ useAuth: () => ({ user: { email: 'boss@x.com', role: 'SUPER_ADMIN' } }) }))

const account = (o) => ({ id: 1, email: 'a@x.com', role: 'USER', mfaEnabled: false, permissions: 0, createdAt: null, ...o })

function renderTable(accounts) {
  adminApi.getAccounts.mockResolvedValue({ data: { content: accounts, totalPages: 1, totalElements: accounts.length } })
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={qc}><AdminAccountTable /></QueryClientProvider>)
}

const chips = async (email) => within(await screen.findByRole('group', { name: new RegExp(email) })).getAllByRole('button')

beforeEach(() => {
  vi.clearAllMocks()
  adminApi.updateAccountPermissions.mockResolvedValue({})
})

describe('AdminAccountTable permissions column', () => {
  it('shows one chip per bit, pressed when the bit is set', async () => {
    renderTable([account({ email: 'admin@x.com', role: 'ADMIN', permissions: 17 })])
    const buttons = await chips('admin@x.com')
    expect(buttons.map(b => [b.textContent, b.getAttribute('aria-pressed')])).toEqual([
      ['READ', 'true'], ['WRITE', 'false'], ['EXEC', 'false'], ['DELETE', 'false'], ['ADMIN', 'true'],
    ])
  })

  it('toggling a chip sends the new mask', async () => {
    renderTable([account({ id: 9, email: 'admin@x.com', role: 'ADMIN', permissions: 1 })])
    const admin = (await chips('admin@x.com')).find(b => b.textContent === 'ADMIN')
    fireEvent.click(admin)
    await waitFor(() => expect(adminApi.updateAccountPermissions).toHaveBeenCalledWith(9, 17))
  })

  it('locks your own row', async () => {
    renderTable([account({ email: 'boss@x.com', role: 'SUPER_ADMIN', permissions: 31 })])
    for (const b of await chips('boss@x.com')) expect(b).toBeDisabled()
  })
})

describe('AdminAccountTable role change', () => {
  beforeEach(() => adminApi.updateAccountRole.mockResolvedValue({}))

  const openPromote = async () => {
    renderTable([account({ id: 5, email: 'u@x.com', role: 'USER' })])
    fireEvent.click(await screen.findByTitle('Nâng lên ADMIN'))
    return screen.findByRole('dialog')
  }

  it('promote lists what an admin can do and calls the API only after Confirm', async () => {
    const dialog = await openPromote()
    expect(within(dialog).getByText(/u@x.com/)).toBeInTheDocument()
    expect(within(dialog).getAllByRole('listitem')).toHaveLength(5)
    expect(adminApi.updateAccountRole).not.toHaveBeenCalled()
    fireEvent.click(within(dialog).getByRole('button', { name: 'Cấp ADMIN' }))
    await waitFor(() => expect(adminApi.updateAccountRole).toHaveBeenCalledTimes(1))
    expect(adminApi.updateAccountRole).toHaveBeenCalledWith(5, 'ADMIN')
  })

  it('Cancel makes no API call', async () => {
    const dialog = await openPromote()
    fireEvent.click(within(dialog).getAllByRole('button').find(b => b.classList.contains('secondary')))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(adminApi.updateAccountRole).not.toHaveBeenCalled()
  })

  it('Escape makes no API call', async () => {
    await openPromote()
    fireEvent.keyDown(window, { key: 'Escape' })
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(adminApi.updateAccountRole).not.toHaveBeenCalled()
  })

  it('demote has no dialog', async () => {
    renderTable([account({ id: 6, email: 'a@x.com', role: 'ADMIN' })])
    fireEvent.click(await screen.findByTitle('Hạ xuống USER'))
    await waitFor(() => expect(adminApi.updateAccountRole).toHaveBeenCalledWith(6, 'USER'))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
