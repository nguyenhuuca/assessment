import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import LanguageSwitcher from '../LanguageSwitcher.jsx'

describe('LanguageSwitcher', () => {
  it('shows the current language as pressed', () => {
    render(<LanguageSwitcher />)
    expect(screen.getByRole('group', { name: 'Ngôn ngữ' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'VI' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'EN' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('switches language, updates html lang and persists to localStorage', async () => {
    const user = userEvent.setup()
    render(<LanguageSwitcher />)
    await user.click(screen.getByRole('button', { name: 'EN' }))

    expect(screen.getByRole('group', { name: 'Language' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'EN' })).toHaveAttribute('aria-pressed', 'true')
    expect(document.documentElement.lang).toBe('en')
    expect(localStorage.getItem('lang')).toBe('en')

    await user.click(screen.getByRole('button', { name: 'VI' }))
    expect(screen.getByRole('group', { name: 'Ngôn ngữ' })).toBeInTheDocument()
    expect(document.documentElement.lang).toBe('vi')
    expect(localStorage.getItem('lang')).toBe('vi')
  })
})
