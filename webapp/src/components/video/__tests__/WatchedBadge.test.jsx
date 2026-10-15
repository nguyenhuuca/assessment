import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import WatchedBadge from '../WatchedBadge.jsx'
import { watchHistoryApi } from '../../../api/watchHistory.js'

let auth
vi.mock('../../../hooks/useAuth.js', () => ({ useAuth: () => auth }))
vi.mock('../../../api/watchHistory.js', () => ({ watchHistoryApi: { summary: vi.fn() } }))

function renderBadges(ids) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      {ids.map(id => <div key={id} data-testid={`b${id}`}><WatchedBadge videoId={id} /></div>)}
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  auth = { isLoggedIn: true }
  watchHistoryApi.summary.mockResolvedValue({
    data: [
      { videoId: 1, positionSec: 15, durationSec: 60, completed: false },
      { videoId: 2, positionSec: 58, durationSec: 60, completed: true },
    ],
  })
})

describe('WatchedBadge', () => {
  it('shows a progress line for partial, "Đã xem" for completed, nothing for unseen, with one request', async () => {
    renderBadges([1, 2, 3, 4])
    await waitFor(() => expect(screen.getByText('Đã xem')).toBeInTheDocument())
    const bar = screen.getByRole('progressbar')
    expect(bar).toHaveAttribute('aria-valuenow', '25')
    expect(bar.firstChild).toHaveStyle({ width: '25%' })
    expect(screen.getByTestId('b2')).toHaveTextContent('Đã xem')
    expect(screen.getByTestId('b3')).toBeEmptyDOMElement()
    expect(watchHistoryApi.summary).toHaveBeenCalledTimes(1)
  })

  it('renders nothing and makes no request for guests', () => {
    auth = { isLoggedIn: false }
    renderBadges([1, 2])
    expect(screen.queryByText('Đã xem')).toBeNull()
    expect(screen.queryByRole('progressbar')).toBeNull()
    expect(watchHistoryApi.summary).not.toHaveBeenCalled()
  })
})
