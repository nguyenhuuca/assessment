import React, { useEffect, useRef } from 'react'
import { authorEmail, authorName, formatTime, getAvatarColor, getInitials } from './commentUtils.js'

/**
 * One comment row (root or reply). Removed/deleted comments render as a
 * placeholder with no author and no actions.
 */
export default function CommentItem({
  comment: c,
  isReply = false,
  user,
  isLoggedIn,
  confirmId,
  onAskDelete,
  onCancelDelete,
  onDelete,
  onReply,
  highlightId,
}) {
  const rowRef = useRef(null)
  const highlighted = !!highlightId && String(highlightId) === String(c.id)

  useEffect(() => {
    if (highlighted) rowRef.current?.scrollIntoView?.({ block: 'center', behavior: 'smooth' })
  }, [highlighted])

  const size = isReply ? 24 : 34
  const mine = !!user?.email && authorEmail(c) === user.email

  if (c.removed || c.deleted) {
    return (
      <div
        ref={rowRef}
        data-comment-id={c.id}
        data-testid={c.removed ? 'removed-comment' : 'deleted-comment'}
        className={highlighted ? 'comment-highlight' : undefined}
        style={{ marginBottom: isReply ? 12 : 18, fontSize: 13, fontStyle: 'italic', color: 'var(--text-muted)' }}
      >
        {c.removed ? 'Bình luận đã bị gỡ do vi phạm chính sách' : 'Bình luận đã bị xoá'}
      </div>
    )
  }

  const email = authorEmail(c)

  return (
    <div
      ref={rowRef}
      data-comment-id={c.id}
      className={highlighted ? 'comment-highlight' : undefined}
      style={{ display: 'flex', gap: 10, marginBottom: isReply ? 12 : 18, animation: highlighted ? undefined : 'fadeIn 0.2s ease' }}
    >
      <div style={{
        width: size, height: size, borderRadius: '50%', flexShrink: 0,
        background: getAvatarColor(email),
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        color: '#fff', fontWeight: 700, fontSize: isReply ? 10 : 13,
      }}>
        {getInitials(email)}
      </div>

      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
          <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--accent-cyan)', maxWidth: 160, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {authorName(c)}
          </span>
          <span style={{ fontSize: 10, color: 'var(--text-muted)', flexShrink: 0 }}>
            {formatTime(c.createdAt || c.created_at)}
          </span>
        </div>
        <p style={{ fontSize: 13, color: 'var(--text)', lineHeight: 1.5, margin: 0, wordBreak: 'break-word', whiteSpace: 'pre-wrap' }}>
          {c.content}
        </p>

        <div style={{ display: 'flex', gap: 12, marginTop: 4 }}>
          {isLoggedIn && (
            <button className="comment-action-link" onClick={() => onReply(c)}>
              Trả lời
            </button>
          )}
          {mine && (
            confirmId === c.id ? (
              <>
                <button className="comment-action-link danger" onClick={() => onDelete(c.id)}>
                  Confirm delete
                </button>
                <button className="comment-action-link" onClick={onCancelDelete}>
                  Cancel
                </button>
              </>
            ) : (
              <button className="comment-action-link" onClick={() => onAskDelete(c.id)}>
                Delete
              </button>
            )
          )}
        </div>
      </div>
    </div>
  )
}
