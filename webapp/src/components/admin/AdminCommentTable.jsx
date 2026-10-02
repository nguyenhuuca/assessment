import React, { useEffect, useState } from 'react'
import { useAdminComments, useModerateComment } from '../../hooks/useAdmin.js'

const REASONS = [
  { code: 'SPAM',          label: 'Spam, quảng cáo, link lặp lại' },
  { code: 'HARASSMENT',    label: 'Xúc phạm, bắt nạt, công kích cá nhân' },
  { code: 'HATE_SPEECH',   label: 'Thù ghét (chủng tộc, tôn giáo, giới tính…)' },
  { code: 'SEXUAL',        label: 'Nội dung tình dục / NSFW' },
  { code: 'VIOLENCE',      label: 'Đe dọa, kích động bạo lực' },
  { code: 'PERSONAL_INFO', label: 'Lộ thông tin cá nhân (SĐT, địa chỉ…)' },
  { code: 'OTHER',         label: 'Khác (cần ghi chú)' },
]

const NOTE_MAX = 500

function formatDate(s) {
  return s ? new Date(s).toLocaleString() : '—'
}

function authorLabel(c) {
  const guest = c.isGuest ?? c.guest
  if (guest) return `Guest · ${c.guestName || 'Anonymous'}`
  return c.authorEmail || '—'
}

function RemoveModal({ comment, onClose, onSubmit, saving, error }) {
  const [reason, setReason] = useState('')
  const [note, setNote] = useState('')
  const [validation, setValidation] = useState('')

  function submit() {
    if (!reason) { setValidation('Vui lòng chọn lý do'); return }
    if (reason === 'OTHER' && !note.trim()) { setValidation('Lý do "Khác" cần có ghi chú'); return }
    setValidation('')
    onSubmit({ action: 'REMOVE', reason, ...(note.trim() ? { note: note.trim() } : {}) })
  }

  return (
    <div className="app-modal-backdrop" onClick={onClose}>
      <div className="app-modal" role="dialog" aria-label="Remove comment" onClick={e => e.stopPropagation()}>
        <div className="app-modal-header">
          <span className="app-modal-title">Gỡ bình luận</span>
          <button className="app-modal-close" onClick={onClose} aria-label="Close">
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>close</span>
          </button>
        </div>
        <div className="app-modal-body">
          <p className="admin-cell-muted" style={{ marginTop: 0 }}>{comment.content}</p>
          <label className="app-label" htmlFor="mod-reason">Lý do</label>
          <select id="mod-reason" className="app-input" value={reason} onChange={e => setReason(e.target.value)}>
            <option value="">— Chọn lý do —</option>
            {REASONS.map(r => <option key={r.code} value={r.code}>{r.label}</option>)}
          </select>
          <label className="app-label" htmlFor="mod-note" style={{ marginTop: 12 }}>
            Ghi chú{reason === 'OTHER' ? ' (bắt buộc)' : ''}
          </label>
          <textarea
            id="mod-note"
            className="app-input"
            rows={3}
            maxLength={NOTE_MAX}
            value={note}
            onChange={e => setNote(e.target.value)}
          />
          {(validation || error) && (
            <div className="app-alert error" role="alert" style={{ marginTop: 12 }}>{validation || error}</div>
          )}
        </div>
        <div className="app-modal-footer">
          <button className="app-btn secondary" onClick={onClose}>Cancel</button>
          <button className="app-btn danger" onClick={submit} disabled={saving}>Remove</button>
        </div>
      </div>
    </div>
  )
}

export default function AdminCommentTable() {
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('ALL')
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [expanded, setExpanded] = useState({})
  const [target, setTarget] = useState(null)
  const [error, setError] = useState('')

  // Debounce keyword search (300ms) and reset to the first page when it changes.
  useEffect(() => {
    const t = setTimeout(() => {
      setQ(search)
      setPage(0)
    }, 300)
    return () => clearTimeout(t)
  }, [search])

  const { data, isLoading } = useAdminComments({
    page,
    status: status === 'ALL' ? null : status,
    q,
  })
  const moderate = useModerateComment()

  const pageData      = data?.data ?? data ?? {}
  const comments      = pageData?.content ?? []
  const totalPages    = pageData?.totalPages ?? 0
  const totalElements = pageData?.totalElements ?? 0

  function run(id, body, onDone) {
    setError('')
    moderate.mutate({ id, body }, {
      onSuccess: () => onDone?.(),
      onError: e => setError(e?.message || 'Thao tác thất bại'),
    })
  }

  function restore(c) {
    if (window.confirm('Khôi phục bình luận này?')) run(c.id, { action: 'RESTORE' })
  }

  return (
    <div className="admin-table-wrap">
      <div className="admin-filters">
        <select
          className="admin-status-select"
          aria-label="Status filter"
          value={status}
          onChange={e => { setStatus(e.target.value); setPage(0) }}
        >
          <option value="ALL">ALL</option>
          <option value="VISIBLE">VISIBLE</option>
          <option value="REMOVED">REMOVED</option>
        </select>
        <input
          className="app-input"
          style={{ maxWidth: 260 }}
          placeholder="Search comments…"
          aria-label="Search comments"
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
      </div>
      {error && !target && <div className="app-alert error" role="alert" style={{ marginBottom: 12 }}>{error}</div>}

      {isLoading ? (
        <div className="admin-loading"><div className="vid-spinner" /></div>
      ) : (
        <>
          <div className="admin-table-meta">SHOWING {comments.length} OF {totalElements.toLocaleString()} ENTRIES</div>
          <table className="admin-table">
            <thead>
              <tr>
                <th>COMMENT</th>
                <th>AUTHOR</th>
                <th>VIDEO</th>
                <th>CREATED</th>
                <th>STATUS</th>
                <th>ACTIONS</th>
              </tr>
            </thead>
            <tbody>
              {comments.map(c => {
                const removed = c.status === 'REMOVED'
                const tip = removed
                  ? [c.moderationReason, c.moderationNote, c.moderatedBy && `by ${c.moderatedBy}`, c.moderatedAt && formatDate(c.moderatedAt)]
                      .filter(Boolean).join(' · ')
                  : undefined
                return (
                  <tr key={c.id} className="admin-table-row">
                    <td>
                      <div
                        className={`admin-comment-text${expanded[c.id] ? ' expanded' : ''}`}
                        onClick={() => setExpanded(prev => ({ ...prev, [c.id]: !prev[c.id] }))}
                      >
                        {c.content}
                      </div>
                    </td>
                    <td className="admin-cell-muted">{authorLabel(c)}</td>
                    <td className="admin-cell-muted">
                      {c.videoTitle || '—'}<br />
                      <span style={{ opacity: 0.6 }}>#{c.videoId}</span>
                    </td>
                    <td className="admin-cell-muted">{formatDate(c.createdAt)}</td>
                    <td>
                      <span className={`admin-badge ${removed ? 'removed' : 'visible'}`} title={tip}>
                        {c.status}
                      </span>
                      {removed && c.moderationReason && (
                        <div className="admin-cell-muted" title={tip}>{c.moderationReason}</div>
                      )}
                    </td>
                    <td>
                      <div className="admin-actions">
                        {removed ? (
                          <button className="admin-action-btn" onClick={() => restore(c)} title="Restore">
                            <span className="material-symbols-outlined" style={{ fontSize: 16 }}>restore</span>
                          </button>
                        ) : (
                          <button
                            className="admin-action-btn danger"
                            onClick={() => { setError(''); setTarget(c) }}
                            title="Remove"
                          >
                            <span className="material-symbols-outlined" style={{ fontSize: 16 }}>block</span>
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
          {totalPages > 1 && (
            <div className="admin-pagination">
              <button className="admin-page-btn" disabled={page === 0} onClick={() => setPage(p => p - 1)}>‹</button>
              {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => (
                <button key={i} className={`admin-page-btn${page === i ? ' active' : ''}`} onClick={() => setPage(i)}>{i + 1}</button>
              ))}
              <button className="admin-page-btn" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>›</button>
            </div>
          )}
        </>
      )}

      {target && (
        <RemoveModal
          comment={target}
          saving={moderate.isPending}
          error={error}
          onClose={() => setTarget(null)}
          onSubmit={body => run(target.id, body, () => setTarget(null))}
        />
      )}
    </div>
  )
}
