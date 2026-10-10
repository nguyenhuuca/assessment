import { describe, it, expect } from 'vitest'
import {
  parseLines, validateSchedule, localToIso, hasActive, errorText, GENERIC_ERROR, isSupportedUrl, MAX_LINES,
} from '../importUtils.js'

describe('parseLines', () => {
  it('parses urls with optional titles and skips blanks', () => {
    const r = parseLines('https://youtu.be/abc | Hello\n\n  https://fb.watch/x/  \n')
    expect(r.errors).toEqual([])
    expect(r.items).toEqual([{ url: 'https://youtu.be/abc', title: 'Hello' }, { url: 'https://fb.watch/x/' }])
  })
  it('flags non-https and unsupported hosts with physical line number', () => {
    const r = parseLines('http://youtu.be/a\nhttps://youtube.com/watch?v=1\nhttps://evil.com/x\nhttps://notyoutube.com/')
    expect(r.items).toHaveLength(1)
    expect(r.errors.map(e => e.line)).toEqual([1, 3, 4])
  })
  it('rejects more than 20 lines', () => {
    const text = Array.from({ length: MAX_LINES + 1 }, (_, i) => `https://youtu.be/${i}`).join('\n')
    expect(parseLines(text).errors.some(e => e.line === 0)).toBe(true)
    expect(parseLines(text.split('\n').slice(0, 20).join('\n')).errors).toEqual([])
  })
  it('accepts subdomains only on dot boundary', () => {
    expect(isSupportedUrl('https://m.facebook.com/reel/1')).toBe(true)
    expect(isSupportedUrl('https://evilfacebook.com/')).toBe(false)
  })
})

describe('schedule', () => {
  const now = Date.parse('2026-10-10T00:00:00Z')
  it('converts GMT+7 local to UTC ISO', () => {
    expect(localToIso('2026-10-11T09:00')).toBe('2026-10-11T02:00:00.000Z')
  })
  it('blocks past, empty and >30 days', () => {
    expect(validateSchedule('2026-10-10T06:00', now)).toMatch(/tương lai/)
    expect(validateSchedule('', now)).toBeTruthy()
    expect(validateSchedule('2026-12-10T06:00', now)).toMatch(/30/)
    expect(validateSchedule('2026-10-12T06:00', now)).toBe('')
  })
  it('requires minutes on a 5-minute slot', () => {
    expect(validateSchedule('2026-10-12T06:05', now)).toBe('')
    expect(validateSchedule('2026-10-12T06:07', now)).toMatch(/chia hết cho 5/)
  })
})

describe('hasActive', () => {
  const now = Date.parse('2026-10-10T00:00:00Z')
  const wrap = content => ({ data: { content } })
  it('true for running jobs', () => {
    expect(hasActive(wrap([{ status: 'DOWNLOADING' }]), now)).toBe(true)
    expect(hasActive(wrap([{ status: 'UPLOADING' }]), now)).toBe(true)
  })
  it('true for due pending, false for future pending / terminal / empty', () => {
    expect(hasActive(wrap([{ status: 'PENDING', scheduledAt: '2026-10-09T00:00:00Z' }]), now)).toBe(true)
    expect(hasActive(wrap([{ status: 'PENDING', scheduledAt: '2026-10-11T00:00:00Z' }]), now)).toBe(false)
    expect(hasActive(wrap([{ status: 'DONE' }, { status: 'FAILED' }]), now)).toBe(false)
    expect(hasActive(undefined, now)).toBe(false)
  })
})

describe('errorText', () => {
  it('maps known codes and falls back', () => {
    expect(errorText('LOGIN_REQUIRED')).toMatch(/đăng nhập/)
    expect(errorText('INVALID_URL')).toMatch(/không hợp lệ/)
    expect(errorText('WHATEVER')).toBe(GENERIC_ERROR)
    expect(errorText(undefined)).toBe(GENERIC_ERROR)
  })
})
