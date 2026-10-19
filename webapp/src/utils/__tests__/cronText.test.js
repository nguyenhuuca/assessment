import { describe, it, expect } from 'vitest'
import { describeCron, cronShapeError, fieldIndexAt } from '../cronText.js'

describe('describeCron', () => {
  it('describes a 6-field cron in Vietnamese and English (24h)', () => {
    expect(describeCron('0 */5 * * * *', 'vi')).toBe('Mỗi 5 phút')
    expect(describeCron('0 30 3 * * *', 'en')).toBe('At 03:30')
    expect(describeCron('0 0 9 * * MON', 'en-US')).toContain('Monday')
  })

  it('returns null for empty or unparseable input', () => {
    expect(describeCron('', 'en')).toBeNull()
    expect(describeCron('nope', 'en')).toBeNull()
  })
})

describe('cronShapeError', () => {
  it('mirrors the server rules', () => {
    expect(cronShapeError('0 */5 * * * *')).toBeNull()
    expect(cronShapeError('*/5 * * * *')).toBe('fieldCount')
    expect(cronShapeError('0 0 * * * * 2026')).toBe('fieldCount')
    expect(cronShapeError('* * * * * *')).toBe('tooFrequent')
    expect(cronShapeError('*/10 * * * * *')).toBe('tooFrequent')
    expect(cronShapeError('60 * * * * *')).toBe('tooFrequent')
  })
})

describe('fieldIndexAt', () => {
  it('finds the field under the caret', () => {
    const expr = '0 */15 3 * * MON'
    expect(fieldIndexAt(expr, 0)).toBe(0)
    expect(fieldIndexAt(expr, 3)).toBe(1)
    expect(fieldIndexAt(expr, 7)).toBe(2)
    expect(fieldIndexAt(expr, expr.length)).toBe(5)
    expect(fieldIndexAt('0  0', 4)).toBe(1)
  })
})
