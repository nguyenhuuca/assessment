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
const viewer = vi.hoisted(() => ({ role: 'SUPER_ADMIN' }))
vi.mock('../../../hooks/useAuth.js', () => ({ useAuth: () => ({ user: { email: 'boss@x.com', role: viewer.role } }) }))

const account = (o) => ({ id: 1, email: 'a@x.com', role: 'USER', mfaEnabled: false, permissions: 0, createdAt: null, ...o })

function renderTable(accounts) {
  adminApi.getAccounts.mockResolvedValue({ data: { content: accounts, totalPages: 1, totalElements: accounts.length } })
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={qc}><AdminAccountTable /></QueryClientProvider>)
}

const chips = async (email) => within(await screen.findByRole('group', { name: new RegExp(`^(?!AUTOPILOT).*${email}`) })).getAllByRole('button')

const roleSelect = (email) => screen.findByRole('combobox', { name: new RegExp(email) })

beforeEach(() => {
  vi.clearAllMocks()
  viewer.role = 'SUPER_ADMIN'
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
    fireEvent.change(await roleSelect('u@x.com'), { target: { value: 'ADMIN' } })
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
    fireEvent.change(await roleSelect('a@x.com'), { target: { value: 'USER' } })
    await waitFor(() => expect(adminApi.updateAccountRole).toHaveBeenCalledWith(6, 'USER'))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('SUPER_ADMIN select offers USER / CONTRIBUTOR / ADMIN; CONTRIBUTOR needs no dialog', async () => {
    renderTable([account({ id: 7, email: 'u@x.com', role: 'USER' })])
    const select = await roleSelect('u@x.com')
    expect(Array.from(select.options).map(o => o.value)).toEqual(['USER', 'CONTRIBUTOR', 'ADMIN'])
    fireEvent.change(select, { target: { value: 'CONTRIBUTOR' } })
    await waitFor(() => expect(adminApi.updateAccountRole).toHaveBeenCalledWith(7, 'CONTRIBUTOR'))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('an ADMIN viewer toggles USER to CONTRIBUTOR and back, with no select', async () => {
    viewer.role = 'ADMIN'
    renderTable([account({ id: 8, email: 'u@x.com', role: 'USER' }), account({ id: 9, email: 'c@x.com', role: 'CONTRIBUTOR' })])
    fireEvent.click(await screen.findByTitle('Nâng lên CONTRIBUTOR'))
    await waitFor(() => expect(adminApi.updateAccountRole).toHaveBeenCalledWith(8, 'CONTRIBUTOR'))
    fireEvent.click(screen.getByTitle('Hạ xuống USER'))
    await waitFor(() => expect(adminApi.updateAccountRole).toHaveBeenCalledWith(9, 'USER'))
    expect(screen.queryByRole('combobox')).toBeNull()
    expect(adminApi.updateAccountRole).not.toHaveBeenCalledWith(expect.anything(), 'ADMIN')
  })

  it('an ADMIN viewer cannot touch another ADMIN row', async () => {
    viewer.role = 'ADMIN'
    renderTable([account({ id: 3, email: 'adm@x.com', role: 'ADMIN' })])
    const locked = (await screen.findAllByTitle('Chỉ quản lý được tài khoản có vai trò thấp hơn')).find(el => el.classList.contains('admin-action-btn') && !el.classList.contains('danger'))
    expect(locked).toBeDisabled()
  })

  it('shows the CONTRIBUTOR badge', async () => {
    renderTable([account({ email: 'c@x.com', role: 'CONTRIBUTOR' })])
    const badge = await screen.findByText('CONTRIBUTOR', { selector: 'span.admin-badge' })
    expect(badge).toHaveClass('admin-badge-contributor')
  })
})

describe('AdminAccountTable autopilot toggles', () => {
  const autopilot = async (email) => within(await screen.findByRole('group', { name: new RegExp(`AUTOPILOT ${email}`) })).getAllByRole('button')

  it('SUPER_ADMIN sees two toggles on a CONTRIBUTOR row reflecting bits 32/64', async () => {
    renderTable([account({ email: 'c@x.com', role: 'CONTRIBUTOR', permissions: 33 })])
    const buttons = await autopilot('c@x.com')
    expect(buttons.map(b => [b.textContent, b.getAttribute('aria-pressed')])).toEqual([['Auto-merge', 'true'], ['Full auto', 'false']])
  })

  it('toggling sends the new mask', async () => {
    renderTable([account({ id: 4, email: 'c@x.com', role: 'CONTRIBUTOR', permissions: 1 })])
    const [autoMerge, full] = await autopilot('c@x.com')
    fireEvent.click(autoMerge)
    await waitFor(() => expect(adminApi.updateAccountPermissions).toHaveBeenCalledWith(4, 33))
    fireEvent.click(full)
    await waitFor(() => expect(adminApi.updateAccountPermissions).toHaveBeenCalledWith(4, 65))
  })

  it('shows on ADMIN rows but not on USER rows', async () => {
    renderTable([account({ id: 1, email: 'adm@x.com', role: 'ADMIN' }), account({ id: 2, email: 'u@x.com', role: 'USER' })])
    await autopilot('adm@x.com')
    expect(screen.queryByRole('group', { name: /AUTOPILOT u@x.com/ })).toBeNull()
  })

  it('is hidden from an ADMIN viewer', async () => {
    viewer.role = 'ADMIN'
    renderTable([account({ email: 'c@x.com', role: 'CONTRIBUTOR' })])
    await chips('c@x.com')
    expect(screen.queryByRole('group', { name: /AUTOPILOT/ })).toBeNull()
  })
})
