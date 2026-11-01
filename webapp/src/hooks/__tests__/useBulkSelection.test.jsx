import { describe, it, expect } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useBulkSelection } from '../useBulkSelection.js'

function setup(initial) {
  return renderHook(props => useBulkSelection(props), { initialProps: initial })
}

describe('useBulkSelection', () => {
  it('toggles single ids', () => {
    const { result } = setup({ pageIds: [1, 2, 3], resetKey: 'a' })
    act(() => result.current.toggle(2))
    expect(result.current.isSelected(2)).toBe(true)
    expect(result.current.count).toBe(1)
    expect(result.current.someSelected).toBe(true)
    expect(result.current.allSelected).toBe(false)
    act(() => result.current.toggle(2))
    expect(result.current.count).toBe(0)
  })

  it('toggleAll selects all then clears', () => {
    const { result } = setup({ pageIds: [1, 2, 3], resetKey: 'a' })
    act(() => result.current.toggleAll())
    expect(result.current.selectedIds).toEqual([1, 2, 3])
    expect(result.current.allSelected).toBe(true)
    act(() => result.current.toggleAll())
    expect(result.current.count).toBe(0)
  })

  it('toggleAll respects max', () => {
    const { result } = setup({ pageIds: [1, 2, 3, 4], resetKey: 'a', max: 2 })
    act(() => result.current.toggleAll())
    expect(result.current.selectedIds).toEqual([1, 2])
  })

  it('toggleAll only selects selectable ids', () => {
    const { result } = setup({ pageIds: [1, 2, 3, 4], resetKey: 'a', isSelectable: id => id % 2 === 0 })
    act(() => result.current.toggleAll())
    expect(result.current.selectedIds).toEqual([2, 4])
    expect(result.current.allSelected).toBe(true)
  })

  it('clears when resetKey changes and keeps otherwise', () => {
    const { result, rerender } = setup({ pageIds: [1, 2], resetKey: 'a' })
    act(() => result.current.toggle(1))
    rerender({ pageIds: [1, 2], resetKey: 'a' })
    expect(result.current.count).toBe(1)
    rerender({ pageIds: [3, 4], resetKey: 'b' })
    expect(result.current.count).toBe(0)
  })

  it('clear empties the selection', () => {
    const { result } = setup({ pageIds: [1, 2], resetKey: 'a' })
    act(() => result.current.toggleAll())
    act(() => result.current.clear())
    expect(result.current.count).toBe(0)
  })
})
