import React, { useState } from 'react'
import { describe, it, expect } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import PasswordInput from '../PasswordInput.jsx'

function Harness(props) {
  const [v, setV] = useState('secret')
  return <PasswordInput label="Mật khẩu mới" value={v} onChange={e => setV(e.target.value)} {...props} />
}

describe('PasswordInput', () => {
  it('is masked by default and toggles visibility with aria-pressed', () => {
    render(<Harness />)
    const input = screen.getByLabelText('Mật khẩu mới')
    expect(input).toHaveAttribute('type', 'password')

    const toggle = screen.getByRole('button', { name: 'Hiện mật khẩu' })
    expect(toggle).toHaveAttribute('aria-pressed', 'false')
    fireEvent.click(toggle)

    expect(input).toHaveAttribute('type', 'text')
    const hide = screen.getByRole('button', { name: 'Ẩn mật khẩu' })
    expect(hide).toHaveAttribute('aria-pressed', 'true')
    fireEvent.click(hide)
    expect(input).toHaveAttribute('type', 'password')
  })

  it('passes autocomplete through and does not submit forms from the toggle', () => {
    render(<Harness autoComplete="new-password" />)
    expect(screen.getByLabelText('Mật khẩu mới')).toHaveAttribute('autocomplete', 'new-password')
    expect(screen.getByRole('button', { name: 'Hiện mật khẩu' })).toHaveAttribute('type', 'button')
  })
})
