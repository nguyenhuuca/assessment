import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { useConfirm } from '../../../hooks/useConfirm.jsx'

function Harness({ onResult }) {
  const [confirm, dialog] = useConfirm()
  return (
    <>
      <button onClick={async () => onResult(await confirm({ title: 'Xoá video', message: 'Chắc chưa?', confirmLabel: 'Xoá', danger: true }))}>open</button>
      {dialog}
    </>
  )
}

function open() {
  const onResult = vi.fn()
  render(<Harness onResult={onResult} />)
  fireEvent.click(screen.getByText('open'))
  return onResult
}

describe('useConfirm', () => {
  it('shows an app dialog and resolves true on confirm', async () => {
    const onResult = open()
    expect(screen.getByRole('dialog', { name: 'Xoá video' })).toHaveTextContent('Chắc chưa?')
    expect(screen.getByRole('button', { name: 'Xoá' })).toHaveClass('danger')
    fireEvent.click(screen.getByRole('button', { name: 'Xoá' }))
    await waitFor(() => expect(onResult).toHaveBeenCalledWith(true))
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it.each([
    ['cancel button', () => fireEvent.click(screen.getByRole('button', { name: 'Huỷ' }))],
    ['close icon', () => fireEvent.click(screen.getByLabelText('Close'))],
    ['Escape key', () => fireEvent.keyDown(window, { key: 'Escape' })],
  ])('resolves false via %s', async (_, dismiss) => {
    const onResult = open()
    dismiss()
    await waitFor(() => expect(onResult).toHaveBeenCalledWith(false))
    expect(screen.queryByRole('dialog')).toBeNull()
  })
})
