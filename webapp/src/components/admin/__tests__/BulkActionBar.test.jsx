import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import BulkActionBar from '../BulkActionBar.jsx'

describe('BulkActionBar', () => {
  it('renders nothing when count is 0', () => {
    const { container } = render(<BulkActionBar count={0} onClear={() => {}}><button>x</button></BulkActionBar>)
    expect(container).toBeEmptyDOMElement()
  })

  it('shows count, children and a working clear button', () => {
    const onClear = vi.fn()
    render(<BulkActionBar count={3} onClear={onClear}><button>Do it</button></BulkActionBar>)
    expect(screen.getByRole('toolbar')).toBeInTheDocument()
    expect(screen.getByText(/3/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Do it' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /bỏ chọn|clear/i }))
    expect(onClear).toHaveBeenCalledTimes(1)
  })
})
