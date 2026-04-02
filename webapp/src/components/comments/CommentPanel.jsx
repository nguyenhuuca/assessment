import React, { useEffect, useRef, useState } from 'react'
import { commentsApi } from '../../api/comments.js'
import { useAuth } from '../../hooks/useAuth.js'

function hashCode(str) {
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash)
  }
  return hash
}
function getAvatarColor(email = '') {
  const colors = ['#e74c3c', '#3498db', '#2ecc71', '#f39c12', '#9b59b6', '#1abc9c', '#e67e22']
  return colors[Math.abs(hashCode(email)) % colors.length]
}
function getInitials(email = '') {
  return email ? email[0].toUpperCase() : '?'
}
function formatTime(dateStr) {
  if (!dateStr) return ''
  const diff = Date.now() - new Date(dateStr).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins} minute${mins > 1 ? 's' : ''} ago`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24) return `${hrs} hour${hrs > 1 ? 's' : ''} ago`
  const days = Math.floor(hrs / 24)
  return `${days} day${days > 1 ? 's' : ''} ago`
}

export default function CommentPanel({ video, onClose }) {
  const { user, isLoggedIn } = useAuth()
  const [comments, setComments] = useState([])
  const [loading, setLoading] = useState(true)
  const [text, setText] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const inputRef = useRef(null)

  useEffect(() => {
    if (!video?.id) return
    setLoading(true)
    commentsApi.list(video.id)
      .then(res => setComments(res.data || res || []))
      .catch(() => setComments([]))
      .finally(() => setLoading(false))
  }, [video?.id])

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
    if (!window.confirm('Delete this comment?')) return
    try {
      await commentsApi.delete(video.id, commentId)
      setComments(prev => prev.filter(c => c.id !== commentId))
    } catch {}
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handlePost()
    }
  }

  return (
    <div
      style={{
        position: 'fixed', right: 0, top: 0, bottom: 0, width: 320,
        background: 'var(--bg-secondary, #1a1a1a)',
        boxShadow: '-4px 0 20px rgba(0,0,0,0.5)',
        display: 'flex', flexDirection: 'column',
        animation: 'slideInRight 0.3s ease',
        zIndex: 1000,
      }}
    >
      {/* Header */}
      <div style={{ padding: '16px', borderBottom: '1px solid rgba(255,255,255,0.1)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h5 style={{ margin: 0, color: 'var(--text-primary, white)' }}>Comments</h5>
        <button onClick={onClose} style={{ background: 'none', border: 'none', color: 'white', fontSize: '1.2rem', cursor: 'pointer' }}>×</button>
      </div>

      {/* Comment list */}
      <div style={{ flex: 1, overflowY: 'auto', padding: 12 }}>
        {loading ? (
          <div style={{ textAlign: 'center', padding: 20 }}>
            <div className="spinner-border spinner-border-sm text-light" />
          </div>
        ) : comments.length === 0 ? (
          <p style={{ textAlign: 'center', color: '#888', marginTop: 20 }}>No comments yet</p>
        ) : (
          comments.map(c => (
            <div key={c.id} style={{ display: 'flex', gap: 10, marginBottom: 16 }}>
              <div style={{
                width: 36, height: 36, borderRadius: '50%', flexShrink: 0,
                background: getAvatarColor(c.userEmail || c.email),
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                color: 'white', fontWeight: 'bold', fontSize: '0.9rem',
              }}>
                {getInitials(c.userEmail || c.email)}
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <span style={{ color: '#aaa', fontSize: '0.75rem' }}>{c.userEmail || c.email}</span>
                  <span style={{ color: '#666', fontSize: '0.7rem' }}>{formatTime(c.createdAt || c.created_at)}</span>
                </div>
                <p style={{ color: 'var(--text-primary, white)', margin: '4px 0', fontSize: '0.85rem' }}>{c.content}</p>
                {(c.userEmail || c.email) === user?.email && (
                  <button
                    onClick={() => handleDelete(c.id)}
                    style={{ background: 'none', border: 'none', color: '#ff6b6b', fontSize: '0.7rem', cursor: 'pointer', padding: 0 }}
                  >
                    Delete
                  </button>
                )}
              </div>
            </div>
          ))
        )}
      </div>

      {/* Input area */}
      {isLoggedIn ? (
        <div style={{ padding: 12, borderTop: '1px solid rgba(255,255,255,0.1)' }}>
          <div style={{ display: 'flex', gap: 8 }}>
            <textarea
              ref={inputRef}
              value={text}
              onChange={e => setText(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Add a comment... (Enter to send)"
              rows={2}
              style={{
                flex: 1, background: 'rgba(255,255,255,0.1)', border: '1px solid rgba(255,255,255,0.2)',
                borderRadius: 8, padding: '8px 12px', color: 'white', resize: 'none', fontSize: '0.85rem',
              }}
            />
            <button
              onClick={handlePost}
              disabled={submitting || !text.trim()}
              style={{ background: '#3498db', border: 'none', borderRadius: 8, color: 'white', padding: '8px 12px', cursor: 'pointer' }}
            >
              {submitting ? '...' : 'Send'}
            </button>
          </div>
        </div>
      ) : (
        <div style={{ padding: 12, textAlign: 'center', color: '#888', fontSize: '0.85rem' }}>
          Login to comment
        </div>
      )}
    </div>
  )
}
