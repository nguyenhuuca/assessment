import { reasonLabel } from './moderationReasons.js'

/** Plain-text title for a notification (rendered as React text, never HTML). */
export function notificationTitle(n) {
  if (n.type === 'COMMENT_REMOVED') {
    return `Bình luận của bạn đã bị gỡ: ${reasonLabel(n.reason)}`
  }
  const actor = n.actorDisplay || 'Ai đó'
  const others = (n.actorCount ?? 1) - 1
  return others > 0
    ? `${actor} và ${others} người khác đã trả lời bình luận của bạn`
    : `${actor} đã trả lời bình luận của bạn`
}

export function badgeLabel(count) {
  return count > 9 ? '9+' : String(count)
}
