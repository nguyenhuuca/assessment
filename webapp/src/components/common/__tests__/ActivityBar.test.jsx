import React from 'react'
import { describe, it, expect } from 'vitest'
import { render, screen, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider, useMutation } from '@tanstack/react-query'
import ActivityBar from '../ActivityBar.jsx'

function Trigger({ fn }) {
  const m = useMutation({ mutationFn: fn })
  return <button onClick={() => m.mutate()}>go</button>
}

describe('ActivityBar', () => {
  it('shows while a mutation is pending and hides when it settles', async () => {
    let finish
    const fn = () => new Promise(r => { finish = r })
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ActivityBar delayMs={20} />
        <Trigger fn={fn} />
      </QueryClientProvider>
    )
    expect(screen.queryByRole('progressbar')).toBeNull()

    await act(async () => { screen.getByText('go').click() })
    expect(await screen.findByRole('progressbar')).toBeInTheDocument()

    await act(async () => { finish() })
    await waitFor(() => expect(screen.queryByRole('progressbar')).toBeNull())
  })

  it('never shows for a mutation that settles within the delay', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ActivityBar delayMs={200} />
        <Trigger fn={() => Promise.resolve()} />
      </QueryClientProvider>
    )
    await act(async () => { screen.getByText('go').click() })
    await new Promise(r => setTimeout(r, 250))
    expect(screen.queryByRole('progressbar')).toBeNull()
  })
})
