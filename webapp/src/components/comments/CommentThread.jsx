import React, { useEffect, useRef, useState } from 'react'
import CommentItem from './CommentItem.jsx'
import { authorName, flattenReplies } from './commentUtils.js'

const COLLAPSED_COUNT = 2

function ReplyBox({ prefill, submitting, error, onSubmit, onClose }) {
  const [text, setText] = useState(prefill)
  const ref = useRef(null)

  useEffect(() => {
    const el = ref.current
    if (!el) return
    el.focus()
    el.setSelectionRange(el.value.length, el.value.length)
  }, [])

  function send() {
    if (!text.trim() || submitting) return
    onSubmit(text.trim())
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send() }
    else if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); onClose() }
  }

  return (
    <div className="comment-reply-box">
      {error && <div className="app-alert error" role="alert" style={{ marginBottom: 6, fontSize: 12 }}>{error}</div>}
      <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
        <textarea
          ref={ref}
          className="app-input"
          aria-label="Reply"
          value={text}
          onChange={e => setText(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Trả lời… (Enter để gửi, Esc để đóng)"
          rows={2}
          style={{ flex: 1, resize: 'none', fontSize: 13 }}
        />
        <button
          className="app-btn primary"
          aria-label="Send reply"
          onClick={send}
          disabled={submitting || !text.trim()}
          style={{ padding: '8px 12px', flexShrink: 0 }}
        >
          Gửi
        </button>
      </div>
    </div>
  )
}

/** A root comment with its flattened replies, collapse toggle and inline reply box. */
export default function CommentThread({
  root,
  user,
  isLoggedIn,
  confirmId,
  onAskDelete,
  onCancelDelete,
  onDelete,
  replyTo,
  onReplyTo,
  onCloseReply,
  onSubmitReply,
  submitting,
  replyError,
  highlightId,
}) {
  const replies = flattenReplies(root)
  const highlightIdx = highlightId ? replies.findIndex(r => String(r.id) === String(highlightId)) : -1
  const [expanded, setExpanded] = useState(highlightIdx >= COLLAPSED_COUNT)

  // A later deep link to a collapsed reply expands the thread
  useEffect(() => {
    if (highlightIdx >= COLLAPSED_COUNT) setExpanded(true)
  }, [highlightIdx])
  const visibleReplies = expanded ? replies : replies.slice(0, COLLAPSED_COUNT)
  const hidden = replies.length - visibleReplies.length
  const boxOpen = replyTo?.rootId === root.id

  const itemProps = { user, isLoggedIn, confirmId, onAskDelete, onCancelDelete, onDelete, highlightId }

  async function submit(text) {
    // Send the comment actually being replied to (not always the root): the server stores the root
    // as parent but uses this id to notify that reply's author.
    const ok = await onSubmitReply(replyTo?.targetId ?? root.id, text)
    if (ok) setExpanded(true)
  }

  return (
    <div>
      <CommentItem
        comment={root}
        {...itemProps}
        onReply={() => onReplyTo({ rootId: root.id, targetId: root.id, name: null })}
      />

      {(replies.length > 0 || boxOpen) && (
        <div className="comment-replies">
          {visibleReplies.map(r => (
            <CommentItem
              key={r.id}
              comment={r}
              isReply
              {...itemProps}
              onReply={c => onReplyTo({ rootId: root.id, targetId: c.id, name: authorName(c) })}
            />
          ))}
          {replies.length > COLLAPSED_COUNT && (
            <button
              className="comment-action-link"
              style={{ marginBottom: 10 }}
              onClick={() => setExpanded(v => !v)}
            >
              {expanded ? 'Ẩn phản hồi ▴' : `Xem thêm ${hidden} phản hồi ▾`}
            </button>
          )}
          {boxOpen && (
            <ReplyBox
              key={`${root.id}:${replyTo.targetId || ''}`}
              prefill={replyTo.name ? `@${replyTo.name} ` : ''}
              submitting={submitting}
              error={replyError}
              onSubmit={submit}
              onClose={onCloseReply}
            />
          )}
        </div>
      )}
    </div>
  )
}
