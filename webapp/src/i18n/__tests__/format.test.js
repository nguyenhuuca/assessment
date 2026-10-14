import { describe, it, expect, afterEach } from 'vitest'
import i18n from '../index.js'
import { relativeTime, compactNumber, formatDate } from '../format.js'

const NOW = new Date('2026-10-14T12:00:00Z').getTime()
const ago = (sec) => new Date(NOW - sec * 1000)

afterEach(() => i18n.changeLanguage('vi'))

describe('relativeTime', () => {
  it('formats in English', async () => {
    await i18n.changeLanguage('en')
    expect(relativeTime(ago(120), NOW)).toBe('2 minutes ago')
    expect(relativeTime(ago(3 * 3600), NOW)).toBe('3 hours ago')
    expect(relativeTime(ago(86400), NOW)).toBe('yesterday')
    expect(relativeTime(ago(14 * 86400), NOW)).toBe('2 weeks ago')
    expect(relativeTime(ago(60 * 86400), NOW)).toBe('2 months ago')
    expect(relativeTime(ago(800 * 86400), NOW)).toBe('2 years ago')
    expect(relativeTime(ago(0), NOW)).toBe('now')
  })

  it('formats in Vietnamese', async () => {
    await i18n.changeLanguage('vi')
    expect(relativeTime(ago(120), NOW)).toBe('2 phút trước')
    expect(relativeTime(ago(3 * 3600), NOW)).toBe('3 giờ trước')
    expect(relativeTime(ago(14 * 86400), NOW)).toBe('2 tuần trước')
    expect(relativeTime(ago(30), NOW)).toBe('30 giây trước')
  })
})

describe('compactNumber', () => {
  it('follows the language', async () => {
    await i18n.changeLanguage('en')
    expect(compactNumber(1500)).toBe('1.5K')
    expect(compactNumber(2000000)).toBe('2M')
    expect(compactNumber(999)).toBe('999')
    await i18n.changeLanguage('vi')
    expect(compactNumber(1500)).toMatch(/^1,5\s?N$/)
    expect(compactNumber(2000000)).toMatch(/^2\s?Tr$/)
  })

  it('treats nullish as 0', () => {
    expect(compactNumber(undefined)).toBe('0')
  })
})

describe('formatDate', () => {
  it('follows the language', async () => {
    const d = '2026-10-14T12:00:00Z'
    const opts = { year: 'numeric', month: 'long', day: 'numeric', timeZone: 'UTC' }
    await i18n.changeLanguage('en')
    expect(formatDate(d, opts)).toBe('October 14, 2026')
    await i18n.changeLanguage('vi')
    expect(formatDate(d, opts)).toBe('14 tháng 10, 2026')
  })
})
