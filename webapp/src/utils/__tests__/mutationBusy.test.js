import { describe, it, expect } from 'vitest'
import { isBusyFor } from '../mutationBusy.js'

describe('isBusyFor', () => {
  it('is false when the mutation is idle', () => {
    expect(isBusyFor({ isPending: false, variables: 1 }, 1)).toBe(false)
  })
  it('matches plain id variables', () => {
    expect(isBusyFor({ isPending: true, variables: 1 }, 1)).toBe(true)
    expect(isBusyFor({ isPending: true, variables: 1 }, 2)).toBe(false)
  })
  it('matches an id picked from object variables', () => {
    expect(isBusyFor({ isPending: true, variables: { id: 7, hidden: true } }, 7, v => v?.id)).toBe(true)
  })
  it('matches ids inside array variables', () => {
    expect(isBusyFor({ isPending: true, variables: ['a', 'b'] }, 'b')).toBe(true)
    expect(isBusyFor({ isPending: true, variables: ['a', 'b'] }, 'c')).toBe(false)
  })
})
