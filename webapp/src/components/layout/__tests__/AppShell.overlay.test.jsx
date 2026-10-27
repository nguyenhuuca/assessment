import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AppShell from '../AppShell.jsx'

vi.mock('../../../hooks/useAuth.js', () => ({
  useAuth: () => ({ user: null, jwt: null, isLoggedIn: false, logout: vi.fn() }),
}))
vi.mock('../../../hooks/useLocaleSync.js', () => ({ useLocaleSync: () => {} }))
vi.mock('../../../hooks/useTheme.js', () => ({ useTheme: () => ({ theme: 'dark', toggle: vi.fn() }) }))
vi.mock('../../auth/LoginForm.jsx', () => ({ default: () => null }))
vi.mock('../../auth/MagicLinkHandler.jsx', () => ({ default: () => null }))
vi.mock('../../explore/ExploreView.jsx', () => ({ default: () => null }))
vi.mock('../../library/LibraryView.jsx', () => ({
  default: ({ onPlayerChange }) => (
    <>
      <button onClick={() => onPlayerChange(true)}>play-collection</button>
      <button onClick={() => onPlayerChange(false)}>back-to-grid</button>
    </>
  ),
}))
vi.mock('../../video/VideoFeed.jsx', () => ({ PublicFeed: () => <div data-testid="public-feed" /> }))

function renderShell() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AppShell />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  window.history.replaceState({}, '', '/')
})

describe('AppShell desktop header over the Home reel', () => {
  it('Home: header is transparent and the main area runs full height behind it', () => {
    renderShell()
    const header = document.querySelector('header')
    const main = document.querySelector('main')
    expect(header).toHaveClass('topnav-overlay')
    expect(header.style.background).toBe('transparent')
    expect(main).toHaveClass('main-reel')
    expect(main.style.marginTop).toBe('0px')
    expect(main.style.height).toBe('100vh')
  })

  it('other tabs keep the solid header and start below it', () => {
    renderShell()
    fireEvent.click(within(document.querySelector('.mobile-nav')).getByRole('button', { name: /Thư viện/ }))
    const header = document.querySelector('header')
    const main = document.querySelector('main')
    expect(header).not.toHaveClass('topnav-overlay')
    expect(header.style.background).toBe('var(--bg)')
    expect(main).not.toHaveClass('main-reel')
    expect(main.style.marginTop).toBe('var(--topnav-h)')
  })

  it('Library: switches to the overlay layout while a player is open, and back on leaving it', () => {
    renderShell()
    fireEvent.click(within(document.querySelector('.mobile-nav')).getByRole('button', { name: /Thư viện/ }))
    const header = document.querySelector('header')
    const main = document.querySelector('main')

    fireEvent.click(screen.getByText('play-collection'))
    expect(header).toHaveClass('topnav-overlay')
    expect(main).toHaveClass('main-reel')
    expect(main.style.height).toBe('100vh')

    fireEvent.click(screen.getByText('back-to-grid'))
    expect(header).not.toHaveClass('topnav-overlay')
    expect(main).not.toHaveClass('main-reel')
  })
})
