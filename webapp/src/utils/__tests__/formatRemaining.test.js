import { describe, it, expect } from 'vitest'
import { formatRemaining } from '../formatRemaining.js'

describe('formatRemaining', () => {
  it('shows hours once an hour or more is left (was 1439:25 for a 24 h order)', () => {
    expect(formatRemaining((23 * 3600 + 59 * 60 + 25) * 1000)).toBe('23:59:25')
    expect(formatRemaining(3600 * 1000)).toBe('1:00:00')
  })

  it('shows MM:SS under an hour and never goes negative', () => {
    expect(formatRemaining(59 * 60 * 1000 + 59 * 1000)).toBe('59:59')
    expect(formatRemaining(5 * 1000 + 999)).toBe('00:05')
    expect(formatRemaining(-1000)).toBe('00:00')
  })
})
