import { describe, it, expect } from 'vitest'
import i18n from '../../i18n/index.js'
import { notificationTitle, notificationNav } from '../notificationText.js'

const n = (over) => ({ id: 'x', actorCount: 0, actorDisplay: null, snippet: 'Clip hay', reason: null, ...over })

describe('notificationTitle for suggestions', () => {
  it('translates the three types in vi', () => {
    expect(notificationTitle(n({ type: 'SUGGESTION_APPROVED' }))).toBe('Đề xuất của bạn đã được duyệt: Clip hay')
    expect(notificationTitle(n({ type: 'SUGGESTION_LIVE' }))).toBe('Video bạn đề xuất đã lên app: Clip hay')
    expect(notificationTitle(n({ type: 'SUGGESTION_REJECTED', reason: 'COPYRIGHT' })))
      .toBe('Đề xuất của bạn bị từ chối: Clip hay (Vi phạm bản quyền)')
  })

  it('translates the three types in en', async () => {
    await i18n.changeLanguage('en')
    expect(notificationTitle(n({ type: 'SUGGESTION_APPROVED' }))).toBe('Your suggestion was approved: Clip hay')
    expect(notificationTitle(n({ type: 'SUGGESTION_LIVE' }))).toBe('The video you suggested is now live: Clip hay')
    expect(notificationTitle(n({ type: 'SUGGESTION_REJECTED', reason: 'OTHER' })))
      .toBe('Your suggestion was rejected: Clip hay (Other reason)')
  })

  it('falls back when the snippet is empty and keeps unknown reason codes', () => {
    expect(notificationTitle(n({ type: 'SUGGESTION_APPROVED', snippet: null }))).toBe('Đề xuất của bạn đã được duyệt: video')
    expect(notificationTitle(n({ type: 'SUGGESTION_REJECTED', reason: 'NEW_CODE' }))).toContain('(NEW_CODE)')
  })
})

describe('REPORT_CLOSED', () => {
  it('shows the title and the translated reason', async () => {
    await i18n.changeLanguage('en')
    expect(notificationTitle(n({ type: 'REPORT_CLOSED', snippet: 'Hard to read', reason: 'not_planned' })))
      .toBe('Your report was closed: Hard to read (Not planned)')
    expect(notificationTitle(n({ type: 'REPORT_CLOSED', snippet: 'Hard to read' })))
      .toBe('Your report was closed: Hard to read')
    expect(notificationTitle(n({ type: 'REPORT_CLOSED', snippet: 'X', reason: 'brand_new' })))
      .toBe('Your report was closed: X (brand_new)')
    await i18n.changeLanguage('vi')
    expect(notificationTitle(n({ type: 'REPORT_CLOSED', snippet: 'Khó đọc', reason: 'completed' })))
      .toBe('Report của bạn đã được đóng: Khó đọc (Đã xong)')
  })
})

describe('payment notifications', () => {
  const pay = (over) => n({ type: 'DONATION_RECEIVED', amount: '50000', paidAmount: '50000', orderCode: 'FM1234', donorName: 'Lan', ...over })
  const norm = (v) => v.replace(/\s/g, ' ')

  it('renders the three types in en', async () => {
    await i18n.changeLanguage('en')
    expect(norm(notificationTitle(pay({})))).toBe('New donation 50.000 ₫ from Lan (FM1234)')
    expect(norm(notificationTitle(pay({ type: 'PREMIUM_PURCHASED', amount: '99000', donorName: null }))))
      .toBe('Premium purchased: 99.000 ₫ from Anonymous (FM1234)')
    expect(norm(notificationTitle(pay({ type: 'PAYMENT_UNDERPAID', paidAmount: '20000' }))))
      .toBe('Underpaid order FM1234: received 20.000 ₫')
  })

  it('renders in vi with the anonymous fallback', async () => {
    await i18n.changeLanguage('vi')
    expect(notificationTitle(pay({ donorName: null }))).toContain('Ẩn danh')
    expect(notificationTitle(pay({ type: 'PAYMENT_UNDERPAID' }))).toContain('chuyển thiếu')
  })

  it('opens the admin view for admins only', () => {
    for (const type of ['DONATION_RECEIVED', 'PREMIUM_PURCHASED', 'PAYMENT_UNDERPAID']) {
      expect(notificationNav({ type }, true, true)).toBe('admin')
      expect(notificationNav({ type }, true, false)).toBeNull()
    }
  })
})

describe('notificationNav', () => {
  it('opens the report page for admins only', () => {
    expect(notificationNav({ type: 'REPORT_CLOSED' }, true)).toBe('report')
    expect(notificationNav({ type: 'REPORT_CLOSED' }, false)).toBeNull()
    expect(notificationNav({ type: 'REPORT_CLOSED' }, undefined)).toBeNull()
  })

  it('keeps suggestion review on history and video types on null', () => {
    expect(notificationNav({ type: 'SUGGESTION_APPROVED' }, false)).toBe('history')
    expect(notificationNav({ type: 'SUGGESTION_REJECTED' }, true)).toBe('history')
    expect(notificationNav({ type: 'SUGGESTION_LIVE', videoId: '1' }, true)).toBeNull()
    expect(notificationNav({ type: 'COMMENT_REPLY', videoId: '1' }, true)).toBeNull()
    expect(notificationNav(null, true)).toBeNull()
  })
})
