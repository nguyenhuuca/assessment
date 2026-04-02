import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import App from './App.jsx'

// Mock all API calls
vi.mock('./api/auth.js', () => ({
  authApi: { me: vi.fn().mockRejectedValue({ status: 401 }) },
}))
vi.mock('./api/videos.js', () => ({
  videosApi: { list: vi.fn().mockResolvedValue({ data: [] }) },
}))

beforeEach(() => {
  localStorage.clear()
})

describe('App smoke tests', () => {
  it('renders without crashing', async () => {
    render(<App />)
    await waitFor(() => {
      expect(document.body).toBeTruthy()
    }, { timeout: 3000 })
  })

  it('shows login form when not logged in', async () => {
    render(<App />)
    await waitFor(() => {
      expect(screen.getByPlaceholderText(/email/i)).toBeInTheDocument()
    }, { timeout: 3000 })
  })
})
