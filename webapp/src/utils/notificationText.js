import i18n from '../i18n/index.js'
import { reasonLabel } from './moderationReasons.js'
import { formatVnd } from './formatVnd.js'

/** Plain-text title for a notification (rendered as React text, never HTML). */
export function notificationTitle(n) {
  if (n.type === 'SUGGESTION_APPROVED' || n.type === 'SUGGESTION_REJECTED' || n.type === 'SUGGESTION_LIVE') {
    const title = n.snippet || i18n.t('notifications:suggestionUntitled')
    if (n.type === 'SUGGESTION_APPROVED') return i18n.t('notifications:suggestionApproved', { title })
    if (n.type === 'SUGGESTION_LIVE') return i18n.t('notifications:suggestionLive', { title })
    return i18n.t('notifications:suggestionRejected', {
      title,
      reason: n.reason ? i18n.t(`suggestions:rejectReason.${n.reason}`, { defaultValue: n.reason }) : '',
    })
  }
  if (n.type === 'REPORT_CLOSED') {
    if (!n.reason) return i18n.t('notifications:reportClosed', { title: n.snippet ?? '' })
    const reason = i18n.t(`report:stateReason.${n.reason}`, { defaultValue: n.reason })
    return i18n.t('notifications:reportClosedReason', { title: n.snippet ?? '', reason })
  }
  if (n.type === 'DONATION_RECEIVED' || n.type === 'PREMIUM_PURCHASED' || n.type === 'PAYMENT_UNDERPAID') {
    const params = {
      amount: formatVnd(n.amount),
      paid: formatVnd(n.paidAmount),
      name: n.donorName || i18n.t('notifications:anonymous'),
      code: n.orderCode ?? '',
    }
    const key = { DONATION_RECEIVED: 'donationReceived', PREMIUM_PURCHASED: 'premiumPurchased', PAYMENT_UNDERPAID: 'paymentUnderpaid' }[n.type]
    return i18n.t(`notifications:${key}`, params)
  }
  if (n.type === 'COMMENT_REMOVED') {
    return i18n.t('notifications:removed', { reason: reasonLabel(n.reason) })
  }
  const actor = n.actorDisplay || i18n.t('notifications:someone')
  const others = (n.actorCount ?? 1) - 1
  return others > 0
    ? i18n.t('notifications:replyMany', { actor, count: others })
    : i18n.t('notifications:replyOne', { actor })
}

/** Nav key a notification opens, or null when it opens a video (or nothing). The report page needs CONTRIBUTOR or above; payment types open the admin view. */
export function notificationNav(n, canReport, isAdmin) {
  if (n?.type === 'SUGGESTION_APPROVED' || n?.type === 'SUGGESTION_REJECTED') return 'history'
  if (n?.type === 'REPORT_CLOSED') return canReport ? 'report' : null
  if (n?.type === 'DONATION_RECEIVED' || n?.type === 'PREMIUM_PURCHASED' || n?.type === 'PAYMENT_UNDERPAID') return isAdmin ? 'admin' : null
  return null
}

export function badgeLabel(count) {
  return count > 9 ? '9+' : String(count)
}
