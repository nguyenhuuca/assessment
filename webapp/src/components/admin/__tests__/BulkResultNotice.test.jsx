import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import '../../../i18n/index.js'
import BulkResultNotice from '../BulkResultNotice.jsx'

describe('BulkResultNotice', () => {
  it('renders the summary as a status region without a list when there are no reasons', () => {
    render(<BulkResultNotice notice={{ summary: '2 updated', reasons: [] }} style={{ margin: 12 }} />)
    const el = screen.getByRole('status')
    expect(el).toHaveTextContent('2 updated')
    expect(el.querySelector('ul')).toBeNull()
    expect(el.style.margin).toBe('12px')
  })

  it('lists each reason with its label and count', () => {
    render(<BulkResultNotice notice={{ summary: '1 skipped', reasons: [{ code: 'a', label: 'Label A', count: 3 }] }} />)
    expect(screen.getByRole('listitem')).toHaveTextContent(/Label A \(3\)/)
  })
})
