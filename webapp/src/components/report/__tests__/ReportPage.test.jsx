import React from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ReportPage from '../ReportPage.jsx'
import * as reportsApi from '../../../api/reports.js'
import { useAuth } from '../../../hooks/useAuth.js'
import i18n from '../../../i18n/index.js'

vi.mock('../../../api/reports.js', () => ({ createReport: vi.fn(), listReports: vi.fn(), retryReport: vi.fn() }))
vi.mock('../../../hooks/useAuth.js', () => ({ useAuth: vi.fn() }))

const row = (o) => ({ id: 'r1', type: 'BUG', title: 'Old bug', status: 'CREATED', issueNumber: 7,
  issueUrl: 'https://github.com/o/r/issues/7', reporter: 'alice', githubState: 'OPEN',
  createdAt: '2026-10-19T01:00:00Z', ...o })

function renderPage(props = {}) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={qc}><ReportPage fromPage="library" {...props} /></QueryClientProvider>)
}
const setRole = (role, extra) => useAuth.mockReturnValue({ user: { role, ...extra } })

async function fill() {
  fireEvent.change(await screen.findByLabelText('Tiêu đề'), { target: { value: 'Broken' } })
  fireEvent.change(screen.getByLabelText('Mô tả'), { target: { value: 'It breaks' } })
}
const submitBtn = () => screen.getByRole('button', { name: /Gửi báo cáo|Đang gửi/ })

beforeEach(async () => {
  vi.clearAllMocks()
  await i18n.changeLanguage('vi')
  setRole('ADMIN')
  reportsApi.listReports.mockResolvedValue({ data: { content: [], totalPages: 0 } })
})

describe('ReportPage', () => {
  it('ADMIN has no autopilot select', async () => {
    renderPage()
    await fill()
    expect(screen.queryByLabelText('Autopilot')).toBeNull()
  })

  it('SUPER_ADMIN sees the autopilot select', async () => {
    setRole('SUPER_ADMIN')
    renderPage()
    expect(await screen.findByLabelText('Autopilot')).toBeInTheDocument()
  })

  it('submit is disabled until valid, then sends type/title/description/autoLabel/context', async () => {
    setRole('SUPER_ADMIN')
    reportsApi.createReport.mockResolvedValue({ data: { id: 'n', status: 'CREATED', issueNumber: 9, issueUrl: 'https://github.com/o/r/issues/9' } })
    renderPage()
    await screen.findByLabelText('Autopilot')
    expect(submitBtn()).toBeDisabled()
    await fill()
    fireEvent.click(screen.getByRole('button', { name: 'Cải tiến' }))
    fireEvent.change(screen.getByLabelText('Autopilot'), { target: { value: 'AUTO_MERGE' } })
    fireEvent.click(submitBtn())
    await waitFor(() => expect(reportsApi.createReport).toHaveBeenCalled())
    expect(reportsApi.createReport.mock.calls[0][0]).toEqual({
      type: 'ENHANCEMENT', title: 'Broken', description: 'It breaks', autoLabel: 'AUTO_MERGE',
      context: { page: 'library', path: window.location.pathname, appVersion: __APP_BUILD__,
        userAgent: navigator.userAgent, screenWidth: window.innerWidth },
    })
    const link = await screen.findByRole('link', { name: i18n.t('report:created', { number: 9 }) })
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')
    expect(link).toHaveAttribute('href', 'https://github.com/o/r/issues/9')
  })

  it('shows the cap-reached notice', async () => {
    reportsApi.createReport.mockResolvedValue({ data: { status: 'CREATED', issueNumber: 3, issueUrl: 'https://x/3', capReached: true } })
    renderPage()
    await fill()
    fireEvent.click(submitBtn())
    expect(await screen.findByText(i18n.t('report:capReached'))).toBeInTheDocument()
  })

  it('FAILED shows the localized error and Retry calls retryReport', async () => {
    reportsApi.createReport.mockResolvedValue({ data: { id: 'f1', status: 'FAILED', error: 'github.http.502' } })
    reportsApi.retryReport.mockResolvedValue({ data: { id: 'f1', status: 'CREATED', issueNumber: 4, issueUrl: 'https://x/4' } })
    renderPage()
    await fill()
    fireEvent.click(submitBtn())
    expect(await screen.findByText(i18n.t('report:error.github.http', { status: '502' }))).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: i18n.t('report:retry') }))
    await waitFor(() => expect(reportsApi.retryReport).toHaveBeenCalledWith('f1'))
    expect(await screen.findByRole('link', { name: i18n.t('report:created', { number: 4 }) })).toBeInTheDocument()
  })

  it('list shows state badges, Deleted user and Failed badge', async () => {
    reportsApi.listReports.mockResolvedValue({ data: { totalPages: 1, content: [
      row(),
      row({ id: 'r2', title: 'Closed one', githubState: 'CLOSED', reporter: null }),
      row({ id: 'r3', title: 'Odd', githubState: 'UNKNOWN' }),
      row({ id: 'r4', title: 'Failed one', status: 'FAILED', githubState: null, issueUrl: null, issueNumber: null }),
    ] } })
    renderPage()
    expect(await screen.findByText('Old bug')).toBeInTheDocument()
    expect(screen.getByText(i18n.t('report:state.OPEN'))).toBeInTheDocument()
    expect(screen.getByText(i18n.t('report:state.CLOSED'))).toBeInTheDocument()
    expect(screen.getByText(i18n.t('report:state.UNKNOWN'))).toBeInTheDocument()
    expect(screen.getByText(i18n.t('report:list.deletedUser'))).toBeInTheDocument()
    expect(screen.getByText(i18n.t('report:list.failedBadge'))).toBeInTheDocument()
  })

  it('Retry on a list row: SUPER_ADMIN or the reporter only', async () => {
    reportsApi.listReports.mockResolvedValue({ data: { totalPages: 1, content: [
      row({ id: 'f1', status: 'FAILED', githubState: null, reporter: 'alice' }),
    ] } })
    reportsApi.retryReport.mockResolvedValue({ data: row() })
    setRole('ADMIN', { userName: 'bob' })
    const first = renderPage()
    await screen.findByText('Old bug')
    expect(screen.queryByRole('button', { name: i18n.t('report:retry') })).toBeNull()
    first.unmount()
    setRole('ADMIN', { userName: 'alice' })
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: i18n.t('report:retry') }))
    await waitFor(() => expect(reportsApi.retryReport).toHaveBeenCalledWith('f1'))
  })

  it('submit is disabled while pending', async () => {
    reportsApi.createReport.mockReturnValue(new Promise(() => {}))
    renderPage()
    await fill()
    fireEvent.click(submitBtn())
    await waitFor(() => expect(submitBtn()).toBeDisabled())
    expect(submitBtn()).toHaveTextContent(i18n.t('report:submitting'))
  })
})
