import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AdminView from '../AdminView.jsx'

vi.mock('../../../hooks/useAdmin.js', () => ({ useAdminStats: () => ({ data: { data: {} } }) }))
vi.mock('../AdminVideoTable.jsx', () => ({ default: () => <div>videos</div> }))
vi.mock('../AdminAccountTable.jsx', () => ({ default: () => <div>accounts</div> }))
vi.mock('../AdminCommentTable.jsx', () => ({ default: () => <div>comments</div> }))
vi.mock('../AdminImportPanel.jsx', () => ({ default: () => <div>imports</div> }))
vi.mock('../DriveSyncPanel.jsx', () => ({ default: () => <div>drive sync</div> }))

function renderView() {
  const qc = new QueryClient()
  const spy = vi.spyOn(qc, 'invalidateQueries')
  render(<QueryClientProvider client={qc}><AdminView /></QueryClientProvider>)
  return spy
}

describe('AdminView refresh', () => {
  it('refetches the active tab data and the stats', () => {
    const spy = renderView()
    fireEvent.click(screen.getByLabelText('Refresh'))
    expect(spy).toHaveBeenCalledWith({ queryKey: ['admin', 'videos'] })
    expect(spy).toHaveBeenCalledWith({ queryKey: ['admin', 'stats'] })
  })

  it('targets the tab that is currently open', () => {
    const spy = renderView()
    fireEvent.click(screen.getByText('DRIVE_SYNC'))
    fireEvent.click(screen.getByLabelText('Refresh'))
    expect(spy).toHaveBeenCalledWith({ queryKey: ['admin', 'drive-sync'] })
    expect(spy).not.toHaveBeenCalledWith({ queryKey: ['admin', 'videos'] })
  })
})
