import React, { useEffect, useRef, useState } from 'react'
import { commentsApi } from '../../api/comments.js'
import { useAuth } from '../../hooks/useAuth.js'

import CommentThread from './CommentThread.jsx'
import { countVisible } from './commentUtils.js'

export default function CommentPanel({ video, onClose, highlightCommentId = null }) {
  const { user, isLoggedIn } = useAuth()
  const [comments, setComments]     = useState([])
  const [loading, setLoading]       = useState(true)
  const [text, setText]             = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [confirmId, setConfirmId]   = useState(null) // inline delete confirm
  const [error, setError]           = useState('')
  const [replyTo, setReplyTo]       = useState(null) // { rootId, name } — only one reply box open
  const [replyError, setReplyError] = useState('')
  const inputRef = useRef(null)

  function loadComments(videoId) {
    return commentsApi.list(videoId).then(res => setComments(res.data || res || []))
  }

  useEffect(() => {
    if (!video?.id) return
    setLoading(true)
    setError('')
    loadComments(video.id)
      .catch(() => setComments([]))
      .finally(() => setLoading(false))
  }, [video?.id])

  // Auto-focus textarea when panel opens
  useEffect(() => {
    if (isLoggedIn) setTimeout(() => inputRef.current?.focus(), 320)
  }, [isLoggedIn])

  async function handlePost() {
    if (!text.trim() || !isLoggedIn) return
    setSubmitting(true)
    setError('')
    try {
      // POST only returns { id }, so reload to get the full comment (author, content, createdAt)
      await commentsApi.post(video.id, text.trim())
      setText('')
      await loadComments(video.id)
    } catch (e) {
      setError(e?.message || 'Không gửi được bình luận, thử lại sau')
    }
    finally { setSubmitting(false) }
  }

  // Reply to a thread; the server normalizes parentId to the root. Resolves true on success.
  async function handleReply(rootId, content) {
    if (!isLoggedIn) return false
    setSubmitting(true)
    setReplyError('')
    try {
      await commentsApi.post(video.id, content, rootId)
      await loadComments(video.id)
      setReplyTo(null)
      return true
    } catch (e) {
      setReplyError(e?.message || 'Không gửi được phản hồi, thử lại sau')
      return false
    } finally { setSubmitting(false) }
  }

  function openReply(target) {
    setReplyError('')
    setReplyTo(target)
  }

  async function handleDelete(commentId) {
    try {
      await commentsApi.delete(video.id, commentId)
      // Reload: the server may keep a "deleted" placeholder when the comment has replies
      await loadComments(video.id)
    } catch {}
    finally { setConfirmId(null) }
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handlePost() }
  }

  const total = countVisible(comments)

  return (
    <>
      {/* Backdrop — click to close */}
      <div
        onClick={onClose}
        style={{
          position: 'fixed', inset: 0,
          background: 'rgba(0,0,0,0.45)',
          zIndex: 99,
          animation: 'fadeIn 0.2s ease',
        }}
      />

      {/* Panel */}
      <div style={{
        position: 'fixed', right: 0, top: 0, bottom: 0,
        width: 'min(360px, 100vw)',
        background: 'var(--bg-surface)',
        borderLeft: '1px solid var(--border-subtle)',
        boxShadow: '-8px 0 40px rgba(0,0,0,0.55)',
        display: 'flex', flexDirection: 'column',
        animation: 'slideInRight 0.28s cubic-bezier(0.25,0.46,0.45,0.94)',
        zIndex: 100,
      }}>

        {/* Header */}
        <div style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          padding: '18px 20px',
          borderBottom: '1px solid var(--border)',
          flexShrink: 0,
        }}>
          <div>
            <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--text)' }}>Comments</div>
            {!loading && (
              <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>
                {total} comment{total !== 1 ? 's' : ''}
              </div>
            )}
          </div>
          <button className="app-modal-close" onClick={onClose}>
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>close</span>
          </button>
        </div>

        {/* Comment list */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '12px 16px' }}>
          {loading ? (
            <div style={{ display: 'flex', justifyContent: 'center', padding: 32 }}>
              <div className="vid-spinner" />
            </div>
          ) : comments.length === 0 ? (
            <div style={{
              display: 'flex', flexDirection: 'column', alignItems: 'center',
              gap: 10, padding: '48px 0', color: 'var(--text-muted)',
            }}>
              <span className="material-symbols-outlined" style={{ fontSize: 40, opacity: 0.3 }}>chat_bubble</span>
              <span style={{ fontSize: 13 }}>No comments yet</span>
            </div>
          ) : (
            comments.map(c => (
              <CommentThread
                key={c.id}
                root={c}
                user={user}
                isLoggedIn={isLoggedIn}
                confirmId={confirmId}
                onAskDelete={setConfirmId}
                onCancelDelete={() => setConfirmId(null)}
                onDelete={handleDelete}
                replyTo={replyTo}
                onReplyTo={openReply}
                onCloseReply={() => setReplyTo(null)}
                onSubmitReply={handleReply}
                submitting={submitting}
                replyError={replyError}
                highlightId={highlightCommentId}
              />
            ))
          )}
        </div>

        {/* Input area */}
        <div style={{ borderTop: '1px solid var(--border)', padding: '12px 16px', flexShrink: 0 }}>
          {error && <div className="app-alert error" role="alert" style={{ marginBottom: 8, fontSize: 13 }}>{error}</div>}
          {isLoggedIn ? (
            <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
              <textarea
                ref={inputRef}
                className="app-input"
                value={text}
                onChange={e => setText(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="Add a comment… (Enter to send)"
                rows={2}
                style={{ flex: 1, resize: 'none', fontSize: 13 }}
              />
              <button
                className="app-btn primary"
                onClick={handlePost}
                disabled={submitting || !text.trim()}
                style={{ padding: '8px 14px', alignSelf: 'flex-end', flexShrink: 0 }}
              >
                {submitting
                  ? <span style={{ width: 14, height: 14, border: '2px solid rgba(0,48,53,0.3)', borderTopColor: '#003035', borderRadius: '50%', display: 'inline-block', animation: 'spin 0.7s linear infinite' }} />
                  : <span className="material-symbols-outlined" style={{ fontSize: 18 }}>send</span>
                }
              </button>
            </div>
          ) : (
            <div style={{
              textAlign: 'center', padding: '10px 0',
              fontSize: 13, color: 'var(--text-muted)',
            }}>
              <span className="material-symbols-outlined" style={{ fontSize: 16, verticalAlign: 'middle', marginRight: 6 }}>lock</span>
              Login to comment
            </div>
          )}
        </div>
      </div>
    </>
  )
}
