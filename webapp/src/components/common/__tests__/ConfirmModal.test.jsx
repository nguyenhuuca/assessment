import React from 'react'
import i18n from '../../../i18n/index.js'
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { useConfirm } from '../../../hooks/useConfirm.jsx'

function Harness({ onResult }) {
  const [confirm, dialog] = useConfirm()
  return (
    <>
      <button onClick={async () => onResult(await confirm({ title: 'Xóa video', message: 'Chắc chưa?', confirmLabel: 'Xóa', danger: true }))}>open</button>
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
    expect(screen.getByRole('dialog', { name: 'Xóa video' })).toHaveTextContent('Chắc chưa?')
    expect(screen.getByRole('button', { name: 'Xóa' })).toHaveClass('danger')
    fireEvent.click(screen.getByRole('button', { name: 'Xóa' }))
    await waitFor(() => expect(onResult).toHaveBeenCalledWith(true))
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it.each([
    ['cancel button', () => fireEvent.click(screen.getByRole('button', { name: 'Hủy' }))],
    ['close icon', () => fireEvent.click(screen.getByLabelText('Đóng'))],
    ['Escape key', () => fireEvent.keyDown(window, { key: 'Escape' })],
  ])('resolves false via %s', async (_, dismiss) => {
    const onResult = open()
    dismiss()
    await waitFor(() => expect(onResult).toHaveBeenCalledWith(false))
    expect(screen.queryByRole('dialog')).toBeNull()
  })
})

describe('ConfirmModal in English', () => {
  it('uses English default labels', async () => {
    await i18n.changeLanguage('en')
    const Plain = () => {
      const [confirm, dialog] = useConfirm()
      return <><button onClick={() => confirm({ title: 'T' })}>open</button>{dialog}</>
    }
    render(<Plain />)
    fireEvent.click(screen.getByText('open'))
    expect(screen.getByRole('button', { name: 'Confirm' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeInTheDocument()
    expect(screen.getByLabelText('Close')).toBeInTheDocument()
  })
})
