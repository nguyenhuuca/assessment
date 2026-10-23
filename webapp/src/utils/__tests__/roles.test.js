import { describe, it, expect } from 'vitest'
import { canReport, userCanReport, isAdminRole } from '../roles.js'

describe('roles', () => {
  it('canReport is true for CONTRIBUTOR and above only', () => {
    expect(canReport('USER')).toBe(false)
    expect(canReport(undefined)).toBe(false)
    expect(canReport('CONTRIBUTOR')).toBe(true)
    expect(canReport('ADMIN')).toBe(true)
    expect(canReport('SUPER_ADMIN')).toBe(true)
  })

  it('CONTRIBUTOR is not an admin role', () => {
    expect(isAdminRole('CONTRIBUTOR')).toBe(false)
  })

  it('userCanReport falls back to the isAdmin flag when no role is stored', () => {
    expect(userCanReport({ role: 'CONTRIBUTOR' })).toBe(true)
    expect(userCanReport({ isAdmin: true })).toBe(true)
    expect(userCanReport({ role: 'USER', isAdmin: false })).toBe(false)
    expect(userCanReport(null)).toBe(false)
  })
})
