import { describe, it, expect } from 'vitest'
import { formatGrouped, toDigits } from '../moneyInput.js'

describe('moneyInput', () => {
  it('groups with vi-VN separators', () => {
    expect(formatGrouped('1234567')).toBe('1.234.567')
    expect(formatGrouped('999')).toBe('999')
  })
  it('keeps empty empty', () => {
    expect(formatGrouped('')).toBe('')
    expect(formatGrouped(undefined)).toBe('')
  })
  it('strips separators and symbols from pasted text', () => {
    expect(toDigits('1,234,567 ₫')).toBe('1234567')
  })
  it('strips leading zeros', () => {
    expect(toDigits('007')).toBe('7')
    expect(toDigits('0')).toBe('')
  })
  it('caps at 12 digits', () => {
    expect(toDigits('1234567890123456')).toBe('123456789012')
  })
})
