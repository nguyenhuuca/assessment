import React, { useState } from 'react'
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import MoneyInput from '../MoneyInput.jsx'

function Harness({ onValue }) {
  const [v, setV] = useState('')
  return <MoneyInput aria-label="amt" placeholder="0" value={v} onChange={d => { setV(d); onValue(d) }} />
}

describe('MoneyInput', () => {
  it('formats while typing and reports digits only', () => {
    const onValue = vi.fn()
    render(<Harness onValue={onValue} />)
    const input = screen.getByLabelText('amt')
    fireEvent.change(input, { target: { value: '1234567' } })
    expect(input).toHaveValue('1.234.567')
    expect(onValue).toHaveBeenLastCalledWith('1234567')
  })
  it('handles paste with separators, leading zeros and the cap', () => {
    const onValue = vi.fn()
    render(<Harness onValue={onValue} />)
    const input = screen.getByLabelText('amt')
    fireEvent.change(input, { target: { value: '1,234,567 ₫' } })
    expect(onValue).toHaveBeenLastCalledWith('1234567')
    fireEvent.change(input, { target: { value: '007' } })
    expect(input).toHaveValue('7')
    fireEvent.change(input, { target: { value: '1234567890123456' } })
    expect(onValue).toHaveBeenLastCalledWith('123456789012')
    fireEvent.change(input, { target: { value: '' } })
    expect(input).toHaveValue('')
  })
})
