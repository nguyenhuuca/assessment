import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import PremiumSection from '../PremiumSection.jsx'
import { formatDate } from '../../../i18n/format.js'
import i18n from '../../../i18n/index.js'

describe('PremiumSection', () => {
  it('shows the localized expiry when premium is active', () => {
    i18n.changeLanguage('en')
    const until = new Date(Date.now() + 30 * 86400000).toISOString()
    render(<PremiumSection premiumUntil={until} />)
    expect(screen.getByText(`Premium until ${formatDate(until)}`)).toBeInTheDocument()
  })

  it('shows Free when null or expired', () => {
    i18n.changeLanguage('en')
    const { rerender } = render(<PremiumSection premiumUntil={null} />)
    expect(screen.getByText('Free')).toBeInTheDocument()
    rerender(<PremiumSection premiumUntil={new Date(Date.now() - 1000).toISOString()} />)
    expect(screen.getByText('Free')).toBeInTheDocument()
  })

  it('renders the buy button only when onBuy is given', () => {
    i18n.changeLanguage('en')
    const onBuy = vi.fn()
    const { rerender } = render(<PremiumSection premiumUntil={null} />)
    expect(screen.queryByRole('button')).toBeNull()
    rerender(<PremiumSection premiumUntil={null} onBuy={onBuy} />)
    fireEvent.click(screen.getByRole('button', { name: 'Get Premium' }))
    expect(onBuy).toHaveBeenCalled()
  })
})
