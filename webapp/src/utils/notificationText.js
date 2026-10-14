import i18n from '../i18n/index.js'
import { reasonLabel } from './moderationReasons.js'

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
  if (n.type === 'COMMENT_REMOVED') {
    return i18n.t('notifications:removed', { reason: reasonLabel(n.reason) })
  }
  const actor = n.actorDisplay || i18n.t('notifications:someone')
  const others = (n.actorCount ?? 1) - 1
  return others > 0
    ? i18n.t('notifications:replyMany', { actor, count: others })
    : i18n.t('notifications:replyOne', { actor })
}

export function badgeLabel(count) {
  return count > 9 ? '9+' : String(count)
}
