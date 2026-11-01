import { describe, it, expect } from 'vitest'
import i18n from '../../i18n/index.js'
import { formatBulkResult, buildBulkNotice } from '../bulkResult.js'

const en = i18n.getFixedT('en', 'admin')
const vi_ = i18n.getFixedT('vi', 'admin')

describe('formatBulkResult', () => {
  it('joins all non-zero parts', () => {
    const r = { updated: 3, unchanged: 1, skipped: [{ id: 1 }, { id: 2 }], notFound: [9] }
    expect(formatBulkResult(r, en)).toBe('3 updated · 1 unchanged · 2 skipped · 1 not found')
  })

  it('omits zero parts', () => {
    expect(formatBulkResult({ updated: 2, unchanged: 0, skipped: [], notFound: [] }, en)).toBe('2 updated')
    expect(formatBulkResult({ updated: 0, skipped: [{ id: 1 }] }, en)).toBe('1 skipped')
  })

  it('returns empty string for empty or missing result', () => {
    expect(formatBulkResult({}, en)).toBe('')
    expect(formatBulkResult(undefined, en)).toBe('')
  })

  it('renders Vietnamese text', () => {
    expect(formatBulkResult({ updated: 3, notFound: [1] }, vi_)).toBe('3 đã cập nhật · 1 không tìm thấy')
  })
})

describe('buildBulkNotice', () => {
  it('groups skipped codes with counts and translated labels', () => {
    const r = {
      updated: 1,
      skipped: [{ id: 1, code: 'error.account.outranked' }, { id: 2, code: 'error.account.outranked' }, { id: 3, code: 'error.nope' }],
    }
    const n = buildBulkNotice(r, en)
    expect(n.summary).toBe('1 updated · 3 skipped')
    expect(n.reasons).toEqual([
      { code: 'error.account.outranked', label: en('bulk.reason.error.account.outranked'), count: 2 },
      { code: 'error.nope', label: en('bulk.reason.unknown'), count: 1 },
    ])
  })

  it('has no reasons when nothing was skipped or the result is missing', () => {
    expect(buildBulkNotice({ updated: 2 }, en).reasons).toEqual([])
    expect(buildBulkNotice(undefined, en)).toEqual({ summary: '', reasons: [] })
  })
})
