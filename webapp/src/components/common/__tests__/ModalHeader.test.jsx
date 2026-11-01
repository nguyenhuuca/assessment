import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import ModalHeader from '../ModalHeader.jsx'

describe('ModalHeader', () => {
  it('renders title, subtitle and a close button that calls onClose', () => {
    const onClose = vi.fn()
    render(<ModalHeader icon="bookmark_add" title="Lưu vào collection" subtitle="Mèo con" onClose={onClose} />)
    expect(screen.getByText('Lưu vào collection')).toBeTruthy()
    expect(screen.getByText('Mèo con')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Đóng' }))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('has no close button without onClose and tints the icon for danger', () => {
    const { container } = render(<ModalHeader icon="delete" tone="danger" title="Xóa" />)
    expect(screen.queryByRole('button')).toBeNull()
    expect(container.querySelector('.app-modal-icon.danger')).toBeTruthy()
  })

  it('disables the close button while pending', () => {
    render(<ModalHeader title="X" onClose={vi.fn()} closeDisabled />)
    expect(screen.getByRole('button', { name: 'Đóng' }).disabled).toBe(true)
  })
})
