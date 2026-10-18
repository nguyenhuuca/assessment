import { describe, it, expect } from 'vitest'
import { PERMISSIONS, hasPermission, togglePermission } from '../permissions.js'

describe('permissions', () => {
  it('matches the server bit values', () => {
    expect(PERMISSIONS.map(p => [p.key, p.bit])).toEqual([['READ', 1], ['WRITE', 2], ['EXEC', 4], ['DELETE', 8], ['ADMIN', 16]])
  })

  it('reads and toggles single bits', () => {
    expect(hasPermission(17, 16)).toBe(true)
    expect(hasPermission(17, 2)).toBe(false)
    expect(hasPermission(undefined, 1)).toBe(false)
    expect(togglePermission(17, 16)).toBe(1)
    expect(togglePermission(0, 4)).toBe(4)
    expect(togglePermission(null, 1)).toBe(1)
  })
})
