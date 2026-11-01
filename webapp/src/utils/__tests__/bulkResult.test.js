import { describe, it, expect } from 'vitest'
import i18n from '../../i18n/index.js'
import { formatBulkResult } from '../bulkResult.js'

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
