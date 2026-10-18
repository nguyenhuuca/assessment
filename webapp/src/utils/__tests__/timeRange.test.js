import { describe, it, expect } from 'vitest'
import { TIME_PRESETS, DEFAULT_TIME_FILTER, toRangeParams } from '../timeRange.js'

const NOW = Date.parse('2026-10-18T12:00:00Z')

describe('toRangeParams', () => {
  it('defaults to the last 12 hours', () => {
    expect(DEFAULT_TIME_FILTER).toEqual({ mode: 'relative', preset: '12h' })
    expect(toRangeParams(DEFAULT_TIME_FILTER, NOW).params).toEqual({ from: '2026-10-18T00:00:00.000Z' })
  })

  it.each(Object.keys(TIME_PRESETS))('preset %s gives from = now - preset and no to', (preset) => {
    const { params, error } = toRangeParams({ mode: 'relative', preset }, NOW)
    expect(error).toBe('')
    expect(params).toEqual({ from: new Date(NOW - TIME_PRESETS[preset]).toISOString() })
  })

  it('all gives empty params', () => {
    expect(toRangeParams({ mode: 'all' }, NOW)).toEqual({ params: {}, error: '' })
  })

  it('custom from only converts VN time to UTC', () => {
    expect(toRangeParams({ mode: 'custom', from: '2026-10-18T07:00', to: '' }, NOW))
      .toEqual({ params: { from: '2026-10-18T00:00:00.000Z' }, error: '' })
  })

  it('custom to only includes its whole minute', () => {
    expect(toRangeParams({ mode: 'custom', from: '', to: '2026-10-18T19:00' }, NOW))
      .toEqual({ params: { to: '2026-10-18T12:01:00.000Z' }, error: '' })
  })

  it('custom with both bounds', () => {
    expect(toRangeParams({ mode: 'custom', from: '2026-10-18T07:00', to: '2026-10-18T19:00' }, NOW))
      .toEqual({ params: { from: '2026-10-18T00:00:00.000Z', to: '2026-10-18T12:01:00.000Z' }, error: '' })
  })

  it('custom with no bound picked yet gives nothing to fetch', () => {
    expect(toRangeParams({ mode: 'custom', from: '', to: '' }, NOW)).toEqual({ params: null, error: '' })
  })

  it('same minute for from and to is valid', () => {
    expect(toRangeParams({ mode: 'custom', from: '2026-10-18T07:00', to: '2026-10-18T07:00' }, NOW))
      .toEqual({ params: { from: '2026-10-18T00:00:00.000Z', to: '2026-10-18T00:01:00.000Z' }, error: '' })
  })

  it('from one minute after to is an error', () => {
    expect(toRangeParams({ mode: 'custom', from: '2026-10-18T07:01', to: '2026-10-18T07:00' }, NOW))
      .toEqual({ params: null, error: 'admin:timeRangeInvalid' })
  })

  it('from after to is an error and has no params', () => {
    expect(toRangeParams({ mode: 'custom', from: '2026-10-18T19:00', to: '2026-10-18T07:00' }, NOW))
      .toEqual({ params: null, error: 'admin:timeRangeInvalid' })
  })
})
