import React from 'react'
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import UpdateBanner from '../../components/layout/UpdateBanner.jsx'
import i18n from '../../i18n/index.js'

function mockVersion(build) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({ ok: true, json: async () => ({ build }) })
}

function becomeVisible() {
  act(() => { document.dispatchEvent(new Event('visibilitychange')) })
}

describe('UpdateBanner / useNewVersion', () => {
  beforeEach(() => { vi.stubEnv('PROD', true) })
  afterEach(() => {
    vi.unstubAllEnvs()
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  it('shows the banner when the deployed build differs', async () => {
    const fetchMock = mockVersion('abc1234')
    render(<UpdateBanner />)
    expect(screen.queryByRole('status')).toBeNull()
    becomeVisible()
    expect(await screen.findByRole('status')).toHaveTextContent('Có phiên bản mới')
    const [url, opts] = fetchMock.mock.calls[0]
    expect(url).toMatch(/^\/version\.json\?t=\d+$/)
    expect(opts).toEqual({ cache: 'no-store' })
  })

  it('stays hidden when the build is the same', async () => {
    const fetchMock = mockVersion(__APP_BUILD__)
    render(<UpdateBanner />)
    becomeVisible()
    await waitFor(() => expect(fetchMock).toHaveBeenCalled())
    expect(screen.queryByRole('status')).toBeNull()
  })

  it('ignores network and JSON errors', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('offline'))
    render(<UpdateBanner />)
    becomeVisible()
    await waitFor(() => expect(fetchMock).toHaveBeenCalled())
    fetchMock.mockResolvedValue({ ok: true, json: async () => { throw new Error('bad json') } })
    becomeVisible()
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    expect(screen.queryByRole('status')).toBeNull()
  })

  it('polls every 15 minutes', async () => {
    vi.useFakeTimers()
    const fetchMock = mockVersion(__APP_BUILD__)
    render(<UpdateBanner />)
    expect(fetchMock).not.toHaveBeenCalled()
    await act(async () => { await vi.advanceTimersByTimeAsync(15 * 60 * 1000) })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('Reload calls location.reload', async () => {
    mockVersion('abc1234')
    const reload = vi.fn()
    vi.spyOn(window, 'location', 'get').mockReturnValue({ ...window.location, reload })
    render(<UpdateBanner />)
    becomeVisible()
    fireEvent.click(await screen.findByRole('button', { name: 'Tải lại' }))
    expect(reload).toHaveBeenCalled()
  })

  it('dismiss hides the banner for that build', async () => {
    const fetchMock = mockVersion('abc1234')
    render(<UpdateBanner />)
    becomeVisible()
    fireEvent.click(await screen.findByRole('button', { name: 'Đóng' }))
    expect(screen.queryByRole('status')).toBeNull()
    becomeVisible()
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    expect(screen.queryByRole('status')).toBeNull()
  })

  it('does not poll or listen outside PROD and cleans up on unmount', () => {
    vi.stubEnv('PROD', false)
    const fetchMock = mockVersion('abc1234')
    const { unmount } = render(<UpdateBanner />)
    becomeVisible()
    expect(fetchMock).not.toHaveBeenCalled()
    unmount()

    vi.stubEnv('PROD', true)
    const remove = vi.spyOn(document, 'removeEventListener')
    const clear = vi.spyOn(globalThis, 'clearInterval')
    render(<UpdateBanner />).unmount()
    expect(remove).toHaveBeenCalledWith('visibilitychange', expect.any(Function))
    expect(clear).toHaveBeenCalled()
  })

  it('has english copy', async () => {
    await i18n.changeLanguage('en')
    mockVersion('abc1234')
    render(<UpdateBanner />)
    becomeVisible()
    expect(await screen.findByText('A new version is available')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reload' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Dismiss' })).toBeInTheDocument()
  })
})
