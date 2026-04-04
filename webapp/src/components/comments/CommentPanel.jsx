import React, { useEffect, useRef, useState } from 'react'
import { commentsApi } from '../../api/comments.js'
import { useAuth } from '../../hooks/useAuth.js'

function hashCode(str) {
  let hash = 0
  for (let i = 0; i < str.length; i++) hash = str.charCodeAt(i) + ((hash << 5) - hash)
  return hash
}
function getAvatarColor(email = '') {
  const colors = ['#e74c3c', '#3498db', '#2ecc71', '#f39c12', '#9b59b6', '#1abc9c', '#e67e22']
  return colors[Math.abs(hashCode(email)) % colors.length]
}
function getInitials(email = '') { return email ? email[0].toUpperCase() : '?' }
function formatTime(dateStr) {
  if (!dateStr) return ''
  const diff = Date.now() - new Date(dateStr).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins}m ago`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24) return `${hrs}h ago`
  return `${Math.floor(hrs / 24)}d ago`
}

export default function CommentPanel({ video, onClose }) {
  const { user, isLoggedIn } = useAuth()
  const [comments, setComments]     = useState([])
  const [loading, setLoading]       = useState(true)
  const [text, setText]             = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [confirmId, setConfirmId]   = useState(null) // inline delete confirm
  const inputRef = useRef(null)

  useEffect(() => {
    if (!video?.id) return
    setLoading(true)
    commentsApi.list(video.id)
      .then(res => setComments(res.data || res || []))
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
    try {
      const res = await commentsApi.post(video.id, text.trim())
      setComments(prev => [res.data || res, ...prev])
      setText('')
    } catch {}
    finally { setSubmitting(false) }
  }

  async function handleDelete(commentId) {
    try {
      await commentsApi.delete(video.id, commentId)
      setComments(prev => prev.filter(c => c.id !== commentId))
    } catch {}
    finally { setConfirmId(null) }
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handlePost() }
  }

  const email = (c) => c.userEmail || c.email || c.guestName || c.userId || ''

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
                {comments.length} comment{comments.length !== 1 ? 's' : ''}
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
              <div key={c.id} style={{
                display: 'flex', gap: 10, marginBottom: 18,
                animation: 'fadeIn 0.2s ease',
              }}>
                {/* Avatar */}
                <div style={{
                  width: 34, height: 34, borderRadius: '50%', flexShrink: 0,
                  background: getAvatarColor(email(c)),
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  color: '#fff', fontWeight: 700, fontSize: 13,
                }}>
                  {getInitials(email(c))}
                </div>

                {/* Body */}
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
                    <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--accent-cyan)', maxWidth: 160, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {email(c).split('@')[0]}
                    </span>
                    <span style={{ fontSize: 10, color: 'var(--text-muted)', flexShrink: 0 }}>
                      {formatTime(c.createdAt || c.created_at)}
                    </span>
                  </div>
                  <p style={{ fontSize: 13, color: 'var(--text)', lineHeight: 1.5, margin: 0, wordBreak: 'break-word' }}>
                    {c.content}
                  </p>

                  {/* Delete / inline confirm */}
                  {email(c) === user?.email && (
                    confirmId === c.id ? (
                      <div style={{ display: 'flex', gap: 8, marginTop: 6 }}>
                        <button
                          onClick={() => handleDelete(c.id)}
                          style={{ background: 'none', border: 'none', color: '#ff6b6b', fontSize: 11, fontWeight: 700, cursor: 'pointer', padding: 0 }}
                        >
                          Confirm delete
                        </button>
                        <button
                          onClick={() => setConfirmId(null)}
                          style={{ background: 'none', border: 'none', color: 'var(--text-muted)', fontSize: 11, cursor: 'pointer', padding: 0 }}
                        >
                          Cancel
                        </button>
                      </div>
                    ) : (
                      <button
                        onClick={() => setConfirmId(c.id)}
                        style={{ background: 'none', border: 'none', color: 'var(--text-muted)', fontSize: 11, cursor: 'pointer', padding: 0, marginTop: 4 }}
                      >
                        Delete
                      </button>
                    )
                  )}
                </div>
              </div>
            ))
          )}
        </div>

        {/* Input area */}
        <div style={{ borderTop: '1px solid var(--border)', padding: '12px 16px', flexShrink: 0 }}>
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
