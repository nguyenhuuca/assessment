import React from 'react'
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import PremiumBadge from '../PremiumBadge.jsx'

const future = new Date(Date.now() + 86400000).toISOString()
const past = new Date(Date.now() - 86400000).toISOString()

describe('PremiumBadge', () => {
  it('shows when premiumUntil is in the future', () => {
    render(<PremiumBadge premiumUntil={future} />)
    expect(screen.getByRole('img', { name: 'Premium' })).toBeInTheDocument()
    expect(screen.getByText('workspace_premium')).toBeInTheDocument()
  })

  it('hidden when expired, null or undefined', () => {
    const { container, rerender } = render(<PremiumBadge premiumUntil={past} />)
    expect(container).toBeEmptyDOMElement()
    rerender(<PremiumBadge premiumUntil={null} />)
    expect(container).toBeEmptyDOMElement()
    rerender(<PremiumBadge />)
    expect(container).toBeEmptyDOMElement()
  })
})
