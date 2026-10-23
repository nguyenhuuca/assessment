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
const openRecent = () => fireEvent.click(screen.getByRole('tab', { name: i18n.t('report:list.title') }))
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

  it('CONTRIBUTOR with no autopilot bits has no autopilot select', async () => {
    setRole('CONTRIBUTOR', { permissions: 1 })
    renderPage()
    await fill()
    expect(screen.queryByLabelText('Autopilot')).toBeNull()
  })

  it('CONTRIBUTOR with the auto-merge bit sees only None and Auto-merge', async () => {
    setRole('CONTRIBUTOR', { permissions: 32 })
    renderPage()
    const select = await screen.findByLabelText('Autopilot')
    expect(Array.from(select.options).map(o => o.value)).toEqual(['', 'AUTO_MERGE'])
  })

  it('CONTRIBUTOR with the auto bit sees both labels and sends the pick', async () => {
    setRole('CONTRIBUTOR', { permissions: 64 })
    reportsApi.createReport.mockResolvedValue({ data: { id: 'n', status: 'CREATED', issueNumber: 9, issueUrl: 'https://github.com/o/r/issues/9' } })
    renderPage()
    const select = await screen.findByLabelText('Autopilot')
    expect(Array.from(select.options).map(o => o.value)).toEqual(['', 'AUTO_MERGE', 'AUTO'])
    await fill()
    fireEvent.change(select, { target: { value: 'AUTO' } })
    fireEvent.click(submitBtn())
    await waitFor(() => expect(reportsApi.createReport).toHaveBeenCalled())
    expect(reportsApi.createReport.mock.calls[0][0].autoLabel).toBe('AUTO')
  })

  it('CONTRIBUTOR can retry their own FAILED row', async () => {
    setRole('CONTRIBUTOR', { userName: 'alice' })
    reportsApi.listReports.mockResolvedValue({ data: { totalPages: 1, content: [
      row({ id: 'f1', status: 'FAILED', githubState: null, reporter: 'alice' }),
    ] } })
    reportsApi.retryReport.mockResolvedValue({ data: row() })
    renderPage()
    openRecent()
    fireEvent.click(await screen.findByRole('button', { name: i18n.t('report:retry') }))
    await waitFor(() => expect(reportsApi.retryReport).toHaveBeenCalledWith('f1'))
  })

  it('the public-repo notice is an info alert, not an error alert', async () => {
    renderPage()
    await fill()
    const note = screen.getByRole('note')
    expect(note).toHaveTextContent(i18n.t('report:publicWarning'))
    expect(note).toHaveClass('info')
    expect(note).not.toHaveClass('error')
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

  it('screen select: default keeps fromPage, a pick overrides it, reset after success', async () => {
    reportsApi.createReport.mockResolvedValue({ data: { status: 'CREATED', issueNumber: 9, issueUrl: 'https://github.com/o/r/issues/9' } })
    renderPage()
    await fill()
    fireEvent.click(submitBtn())
    await waitFor(() => expect(reportsApi.createReport).toHaveBeenCalledTimes(1))
    expect(reportsApi.createReport.mock.calls[0][0].context.page).toBe('library')

    await fill()
    fireEvent.change(screen.getByLabelText(i18n.t('report:screenLabel')), { target: { value: 'explore' } })
    fireEvent.click(submitBtn())
    await waitFor(() => expect(reportsApi.createReport).toHaveBeenCalledTimes(2))
    expect(reportsApi.createReport.mock.calls[1][0].context.page).toBe('explore')
    await waitFor(() => expect(screen.getByLabelText(i18n.t('report:screenLabel'))).toHaveValue(''))
  })

  it.each([['report', 'report'], ['other', null]])('screen select %s posts page %s despite fromPage', async (pick, expected) => {
    reportsApi.createReport.mockResolvedValue({ data: { status: 'CREATED', issueNumber: 9, issueUrl: 'https://github.com/o/r/issues/9' } })
    renderPage()
    await fill()
    fireEvent.change(screen.getByLabelText(i18n.t('report:screenLabel')), { target: { value: pick } })
    fireEvent.click(submitBtn())
    await waitFor(() => expect(reportsApi.createReport).toHaveBeenCalledTimes(1))
    expect(reportsApi.createReport.mock.calls[0][0].context.page).toBe(expected)
    await waitFor(() => expect(screen.getByLabelText(i18n.t('report:screenLabel'))).toHaveValue(''))
  })

  it('screen select keeps the choice when the report FAILED', async () => {
    reportsApi.createReport.mockResolvedValue({ data: { id: 'f1', status: 'FAILED', error: 'github.timeout' } })
    renderPage()
    await fill()
    fireEvent.change(screen.getByLabelText(i18n.t('report:screenLabel')), { target: { value: 'history' } })
    fireEvent.click(submitBtn())
    await screen.findByText(i18n.t('report:failed'))
    expect(screen.getByLabelText(i18n.t('report:screenLabel'))).toHaveValue('history')
  })

  it('shows the cap-reached notice', async () => {
    reportsApi.createReport.mockResolvedValue({ data: { status: 'CREATED', issueNumber: 3, issueUrl: 'https://github.com/o/r/issues/3', capReached: true } })
    renderPage()
    await fill()
    fireEvent.click(submitBtn())
    expect(await screen.findByText(i18n.t('report:capReached'))).toBeInTheDocument()
  })

  it('FAILED shows the localized error and Retry calls retryReport', async () => {
    reportsApi.createReport.mockResolvedValue({ data: { id: 'f1', status: 'FAILED', error: 'github.http.502' } })
    reportsApi.retryReport.mockResolvedValue({ data: { id: 'f1', status: 'CREATED', issueNumber: 4, issueUrl: 'https://github.com/o/r/issues/4' } })
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
    openRecent()
    expect(await screen.findByText('Old bug')).toBeInTheDocument()
    expect(screen.getByText(i18n.t('report:state.OPEN'))).toBeInTheDocument()
    expect(screen.getByText(i18n.t('report:state.CLOSED'))).toBeInTheDocument()
    expect(screen.getByText(i18n.t('report:state.UNKNOWN'))).toBeInTheDocument()
    expect(screen.getByText(i18n.t('report:list.deletedUser'))).toBeInTheDocument()
    expect(screen.getByText(i18n.t('report:list.failedBadge'))).toBeInTheDocument()
  })

  it('CLOSED badge shows the webhook reason; no reason shows the state only', async () => {
    reportsApi.listReports.mockResolvedValue({ data: { totalPages: 1, content: [
      row({ id: 'w1', title: 'Not planned one', githubState: 'CLOSED', githubStateReason: 'not_planned' }),
      row({ id: 'w2', title: 'Plain closed', githubState: 'CLOSED' }),
      row({ id: 'w3', title: 'New reason', githubState: 'CLOSED', githubStateReason: 'brand_new' }),
    ] } })
    renderPage()
    openRecent()
    const closed = i18n.t('report:state.CLOSED')
    expect(await screen.findByText(`${closed} · ${i18n.t('report:stateReason.not_planned')}`)).toBeInTheDocument()
    expect(screen.getByText(closed)).toBeInTheDocument()
    expect(screen.getByText(`${closed} · brand_new`)).toBeInTheDocument()
  })

  it('Retry on a list row: SUPER_ADMIN or the reporter only', async () => {
    reportsApi.listReports.mockResolvedValue({ data: { totalPages: 1, content: [
      row({ id: 'f1', status: 'FAILED', githubState: null, reporter: 'alice' }),
    ] } })
    reportsApi.retryReport.mockResolvedValue({ data: row() })
    setRole('ADMIN', { userName: 'bob' })
    const first = renderPage()
    openRecent()
    await screen.findByText('Old bug')
    expect(screen.queryByRole('button', { name: i18n.t('report:retry') })).toBeNull()
    first.unmount()
    setRole('ADMIN', { userName: 'alice' })
    renderPage()
    openRecent()
    fireEvent.click(await screen.findByRole('button', { name: i18n.t('report:retry') }))
    await waitFor(() => expect(reportsApi.retryReport).toHaveBeenCalledWith('f1'))
  })

  it('renders a non-GitHub issue URL as plain text, not a link', async () => {
    reportsApi.createReport.mockResolvedValue({ data: { status: 'CREATED', issueNumber: 5, issueUrl: 'javascript:alert(1)' } })
    renderPage()
    await fill()
    fireEvent.click(submitBtn())
    expect(await screen.findByText(i18n.t('report:created', { number: 5 }))).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: i18n.t('report:created', { number: 5 }) })).toBeNull()
  })

  it('sends at most 300 characters of user agent', async () => {
    const original = navigator.userAgent
    Object.defineProperty(navigator, 'userAgent', { value: 'U'.repeat(400), configurable: true })
    try {
      reportsApi.createReport.mockResolvedValue({ data: { status: 'CREATED', issueNumber: 6, issueUrl: 'https://github.com/o/r/issues/6' } })
      renderPage()
      await fill()
      fireEvent.click(submitBtn())
      await waitFor(() => expect(reportsApi.createReport).toHaveBeenCalled())
      expect(reportsApi.createReport.mock.calls[0][0].context.userAgent).toHaveLength(300)
    } finally {
      Object.defineProperty(navigator, 'userAgent', { value: original, configurable: true })
    }
  })

  it('a failed retry shows an error', async () => {
    reportsApi.createReport.mockResolvedValue({ data: { id: 'f1', status: 'FAILED', error: 'github.timeout' } })
    reportsApi.retryReport.mockRejectedValue(new Error('Too many requests'))
    renderPage()
    await fill()
    fireEvent.click(submitBtn())
    fireEvent.click(await screen.findByRole('button', { name: i18n.t('report:retry') }))
    expect(await screen.findByText('Too many requests')).toBeInTheDocument()
  })

  it('submit is disabled while pending', async () => {
    reportsApi.createReport.mockReturnValue(new Promise(() => {}))
    renderPage()
    await fill()
    fireEvent.click(submitBtn())
    await waitFor(() => expect(submitBtn()).toBeDisabled())
    expect(submitBtn()).toHaveTextContent(i18n.t('report:submitting'))
  })

  it('opens on New report: form shown, list not fetched until the Recent tab', async () => {
    renderPage()
    const newTab = screen.getByRole('tab', { name: i18n.t('report:tabs.new') })
    expect(newTab).toHaveAttribute('aria-selected', 'true')
    expect(await screen.findByLabelText('Tiêu đề')).toBeVisible()
    expect(screen.queryByRole('table')).toBeNull()
    expect(reportsApi.listReports).not.toHaveBeenCalled()

    openRecent()
    expect(screen.getByRole('tab', { name: i18n.t('report:list.title') })).toHaveAttribute('aria-selected', 'true')
    expect(await screen.findByRole('table')).toBeInTheDocument()
    await waitFor(() => expect(reportsApi.listReports).toHaveBeenCalledTimes(1))
    expect(screen.getByRole('tabpanel')).toHaveAttribute('id', 'report-panel-recent')
  })

  it('the narrow-screen select switches tabs too', async () => {
    renderPage()
    fireEvent.change(screen.getByRole('combobox', { name: i18n.t('report:tabs.label') }), { target: { value: 'recent' } })
    expect(await screen.findByRole('table')).toBeInTheDocument()
    await waitFor(() => expect(reportsApi.listReports).toHaveBeenCalled())
  })

  it('a typed draft survives switching tabs', async () => {
    renderPage()
    await fill()
    openRecent()
    fireEvent.click(screen.getByRole('tab', { name: i18n.t('report:tabs.new') }))
    expect(screen.getByLabelText('Tiêu đề')).toHaveValue('Broken')
    expect(screen.getByLabelText('Mô tả')).toHaveValue('It breaks')
  })

  it('submitting stays on New report and shows the created link', async () => {
    reportsApi.createReport.mockResolvedValue({ data: { status: 'CREATED', issueNumber: 9, issueUrl: 'https://github.com/o/r/issues/9' } })
    renderPage()
    await fill()
    fireEvent.click(submitBtn())
    expect(await screen.findByRole('link', { name: i18n.t('report:created', { number: 9 }) })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: i18n.t('report:tabs.new') })).toHaveAttribute('aria-selected', 'true')
  })

  it('a failed row Retry shows its error on the Recent tab, cleared on tab change', async () => {
    reportsApi.listReports.mockResolvedValue({ data: { totalPages: 1, content: [
      row({ id: 'f2', status: 'FAILED', githubState: null }),
    ] } })
    reportsApi.retryReport.mockRejectedValue(new Error('Too many requests'))
    setRole('SUPER_ADMIN')
    renderPage()
    openRecent()
    fireEvent.click(await screen.findByRole('button', { name: i18n.t('report:retry') }))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Too many requests')
    expect(screen.getByRole('tabpanel')).toContainElement(alert)

    fireEvent.click(screen.getByRole('tab', { name: i18n.t('report:tabs.new') }))
    openRecent()
    expect(screen.queryByText('Too many requests')).toBeNull()
  })

  it('Recent tab shows a loading row, not the empty text, until the list resolves', async () => {
    let resolve
    reportsApi.listReports.mockReturnValue(new Promise(r => { resolve = r }))
    renderPage()
    openRecent()
    expect(await screen.findByText(i18n.t('report:list.loading'))).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent(i18n.t('report:list.loading'))
    expect(screen.queryByText(i18n.t('report:list.empty'))).toBeNull()

    resolve({ data: { totalPages: 1, content: [row({ title: 'Loaded bug' })] } })
    expect(await screen.findByText('Loaded bug')).toBeInTheDocument()
    expect(screen.queryByText(i18n.t('report:list.loading'))).toBeNull()
  })

  it('shows the empty text after loading an empty list', async () => {
    renderPage()
    openRecent()
    expect(await screen.findByText(i18n.t('report:list.empty'))).toBeInTheDocument()
    expect(screen.queryByText(i18n.t('report:list.loading'))).toBeNull()
  })

  it('a page change shows the loading row above the previous rows', async () => {
    reportsApi.listReports.mockResolvedValueOnce({ data: { totalPages: 2, content: [row({ title: 'Page one bug' })] } })
    renderPage()
    openRecent()
    await screen.findByText('Page one bug')
    expect(screen.queryByText(i18n.t('report:list.loading'))).toBeNull()

    let resolve
    reportsApi.listReports.mockReturnValueOnce(new Promise(r => { resolve = r }))
    fireEvent.click(screen.getByRole('button', { name: i18n.t('report:list.next') }))
    expect(await screen.findByText(i18n.t('report:list.loading'))).toBeInTheDocument()
    expect(screen.getByText('Page one bug')).toBeInTheDocument()

    resolve({ data: { totalPages: 2, content: [row({ id: 'r2', title: 'Page two bug' })] } })
    expect(await screen.findByText('Page two bug')).toBeInTheDocument()
    expect(screen.queryByText(i18n.t('report:list.loading'))).toBeNull()
  })
})
