import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import RowActionsMenu from '../RowActionsMenu.jsx'

function setup() {
  const onA = vi.fn()
  const onB = vi.fn()
  render(
    <div>
      <span>outside</span>
      <RowActionsMenu label="Actions for X" items={[
        { key: 'a', icon: 'playlist_add', label: 'Add', onSelect: onA },
        { key: 'b', icon: 'delete', label: 'Remove', danger: true, onSelect: onB },
      ]} />
    </div>,
  )
  return { onA, onB, btn: screen.getByLabelText('Actions for X') }
}

describe('RowActionsMenu', () => {
  it('is closed until the ⋮ button is clicked, then lists every item', () => {
    const { btn } = setup()
    expect(screen.queryByRole('menu')).toBeNull()
    expect(btn).toHaveAttribute('aria-expanded', 'false')
    fireEvent.click(btn)
    expect(btn).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getAllByRole('menuitem').map(b => b.textContent)).toEqual(['playlist_addAdd', 'deleteRemove'])
    expect(screen.getByRole('menuitem', { name: /Remove/ })).toHaveClass('danger')
  })

  it('runs the chosen item once and closes', () => {
    const { btn, onA, onB } = setup()
    fireEvent.click(btn)
    fireEvent.click(screen.getByRole('menuitem', { name: /Add/ }))
    expect(onA).toHaveBeenCalledTimes(1)
    expect(onB).not.toHaveBeenCalled()
    expect(screen.queryByRole('menu')).toBeNull()
  })

  it('closes on Escape, on an outside click and on scroll', () => {
    const { btn } = setup()
    fireEvent.click(btn)
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('menu')).toBeNull()
    fireEvent.click(btn)
    fireEvent.mouseDown(screen.getByText('outside'))
    expect(screen.queryByRole('menu')).toBeNull()
    fireEvent.click(btn)
    fireEvent.scroll(window)
    expect(screen.queryByRole('menu')).toBeNull()
  })

  it('places the popover fixed under the button so the table scroll box cannot clip it', () => {
    const { btn } = setup()
    btn.getBoundingClientRect = () => ({ bottom: 100, right: 300, top: 80, left: 270, width: 30, height: 20 })
    fireEvent.click(btn)
    const menu = screen.getByRole('menu')
    expect(menu.style.top).toBe('104px')
    expect(menu.style.right).toBe(`${window.innerWidth - 300}px`)
  })
})
