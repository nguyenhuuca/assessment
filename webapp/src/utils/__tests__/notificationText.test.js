import { describe, it, expect } from 'vitest'
import i18n from '../../i18n/index.js'
import { notificationTitle } from '../notificationText.js'

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
