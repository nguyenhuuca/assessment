import { describe, it, expect } from 'vitest'
import { formatBytes } from '../formatBytes.js'

describe('formatBytes', () => {
  it('formats bytes without decimals', () => {
    expect(formatBytes(0)).toBe('0 B')
    expect(formatBytes(1023)).toBe('1023 B')
  })
  it('formats larger units with one decimal', () => {
    expect(formatBytes(1048576)).toBe('1.0 MB')
    expect(formatBytes(1.5 * 1024 ** 3)).toBe('1.5 GB')
  })
  it('returns a dash for null and undefined', () => {
    expect(formatBytes(null)).toBe('—')
    expect(formatBytes(undefined)).toBe('—')
  })
})
