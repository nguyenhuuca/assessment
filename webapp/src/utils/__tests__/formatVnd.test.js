import { describe, it, expect } from 'vitest'
import { formatVnd } from '../formatVnd.js'

const norm = (s) => s.replace(/\s/g, ' ')

describe('formatVnd', () => {
  it('formats with dot grouping and the dong sign', () => {
    expect(norm(formatVnd(50000))).toBe('50.000 ₫')
    expect(norm(formatVnd(1250000))).toBe('1.250.000 ₫')
  })
  it('formats zero', () => {
    expect(norm(formatVnd(0))).toBe('0 ₫')
  })
  it('returns empty string for missing or invalid values', () => {
    expect(formatVnd(null)).toBe('')
    expect(formatVnd(undefined)).toBe('')
    expect(formatVnd('abc')).toBe('')
  })
})
