import React, { useEffect, useRef, useState } from 'react'
import { REASONS } from '../../utils/moderationReasons.js'
import { useAdminComments, useBulkModerateComments, useHardDeleteComments, useModerateComment } from '../../hooks/useAdmin.js'
import { isBusyFor } from '../../utils/mutationBusy.js'
import { useConfirm } from '../../hooks/useConfirm.jsx'
import { useTranslation } from 'react-i18next'
import { formatDate as formatDateI18n } from '../../i18n/format.js'

const NOTE_MAX = 500
const BULK_MAX = 100

function formatDate(s) {
  return s ? formatDateI18n(s, { dateStyle: 'medium', timeStyle: 'short' }) : '—'
}

function RemoveModal({ comment, count, onClose, onSubmit, saving, error }) {
  const { t } = useTranslation('admin')
  const [reason, setReason] = useState('')
  const [note, setNote] = useState('')
  const [validation, setValidation] = useState('')

  function submit() {
    if (!reason) { setValidation(t('chooseReasonError')); return }
    if (reason === 'OTHER' && !note.trim()) { setValidation(t('otherNeedsNote')); return }
    setValidation('')
    onSubmit({ action: 'REMOVE', reason, ...(note.trim() ? { note: note.trim() } : {}) })
  }

  return (
    <div className="app-modal-backdrop" onClick={onClose}>
      <div className="app-modal" role="dialog" aria-label={t('removeDialog')} onClick={e => e.stopPropagation()}>
        <div className="app-modal-header">
          <span className="app-modal-title">{t('removeDialog')}</span>
          <button className="app-modal-close" onClick={onClose} aria-label={t('close')}>
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>close</span>
          </button>
        </div>
        <div className="app-modal-body">
          <p className="admin-cell-muted" style={{ marginTop: 0 }}>
            {count ? t('selectedCount', { count }) : comment?.content}
          </p>
          <label className="app-label" htmlFor="mod-reason">{t('reason')}</label>
          <select id="mod-reason" className="app-input" value={reason} onChange={e => setReason(e.target.value)}>
            <option value="">{t('chooseReason')}</option>
            {REASONS.map(r => <option key={r.code} value={r.code}>{r.label}</option>)}
          </select>
          <label className="app-label" htmlFor="mod-note" style={{ marginTop: 12 }}>
            {reason === 'OTHER' ? t('noteRequired') : t('note')}
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
          <button className="app-btn secondary" onClick={onClose}>{t('cancel')}</button>
          <button className="app-btn danger" onClick={submit} disabled={saving} aria-busy={saving}>{t('remove')}</button>
        </div>
      </div>
    </div>
  )
}

export default function AdminCommentTable() {
  const { t } = useTranslation('admin')

  function authorLabel(c) {
    const guest = c.isGuest ?? c.guest
    if (guest) return t('guest', { name: c.guestName || t('anonymous') })
    return c.authorEmail || '—'
  }

  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('ALL')
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [expanded, setExpanded] = useState({})
  const [target, setTarget] = useState(null) // comment object, or 'BULK'
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [selected, setSelected] = useState(() => new Set())
  const headerBox = useRef(null)

  // Debounce keyword search (300ms) and reset to the first page when it changes.
  useEffect(() => {
    const t = setTimeout(() => {
      setQ(search)
      setPage(0)
      setSelected(new Set())
    }, 300)
    return () => clearTimeout(t)
  }, [search])

  const { data, isLoading } = useAdminComments({
    page,
    status: status === 'ALL' ? null : status,
    q,
  })
  const moderate = useModerateComment()
  const bulkModerate = useBulkModerateComments()
  const hardDelete = useHardDeleteComments()
  const [confirm, confirmDialog] = useConfirm()

  const pageData      = data?.data ?? data ?? {}
  const comments      = pageData?.content ?? []
  const totalPages    = pageData?.totalPages ?? 0
  const totalElements = pageData?.totalElements ?? 0

  const pageIds      = comments.map(c => c.id)
  const allSelected  = pageIds.length > 0 && pageIds.every(id => selected.has(id))
  const someSelected = pageIds.some(id => selected.has(id))

  useEffect(() => {
    if (headerBox.current) headerBox.current.indeterminate = someSelected && !allSelected
  }, [someSelected, allSelected])

  // Selection is per page: changing page or filters clears it.
  function goToPage(p) {
    setPage(p)
    setSelected(new Set())
  }

  function toggleOne(id) {
    setSelected(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  function toggleAll() {
    setSelected(allSelected ? new Set() : new Set(pageIds.slice(0, BULK_MAX)))
  }

  function runBulk(body, onDone) {
    setError('')
    setNotice('')
    bulkModerate.mutate({ ids: [...selected], ...body }, {
      onSuccess: res => {
        const r = res?.data ?? res ?? {}
        const verb = body.action === 'REMOVE' ? t('verbRemoved') : t('verbRestored')
        const extra = [
          r.unchanged ? t('unchanged', { count: r.unchanged }) : null,
          r.notFound?.length ? t('notFound', { count: r.notFound.length }) : null,
        ].filter(Boolean).join(', ')
        setNotice(t('bulkResult', { verb, count: r.updated ?? 0, extra: extra ? ` (${extra})` : '' }))
        setSelected(new Set())
        onDone?.()
      },
      onError: e => setError(e?.message || t('actionFailed')),
    })
  }

  async function restoreSelected() {
    if (await confirm({ title: t('restoreTitle'), message: t('restoreSelectedMessage', { count: selected.size }), confirmLabel: t('restore') })) runBulk({ action: 'RESTORE' })
  }

  function run(id, body, onDone) {
    setError('')
    moderate.mutate({ id, body }, {
      onSuccess: () => onDone?.(),
      onError: e => setError(e?.message || t('actionFailed')),
    })
  }

  // Permanent: also removes replies. "Gỡ" (REMOVE) is the reversible option.
  async function hardDeleteIds(ids, label) {
    const ok = await confirm({
      title: t('deletePermTitle'),
      message: t('deletePermMessage', { label }),
      confirmLabel: t('deletePermTitle'),
      danger: true,
    })
    if (!ok) return
    setError('')
    setNotice('')
    hardDelete.mutate(ids, {
      onSuccess: res => {
        const r = res?.data ?? {}
        const replies = r.repliesDeleted ? t('repliesSuffix', { count: r.repliesDeleted }) : ''
        setNotice(t('deletedPerm', { count: r.deleted ?? 0, replies }))
        setSelected(prev => {
          const next = new Set(prev)
          ids.forEach(id => next.delete(id))
          return next
        })
      },
      onError: e => setError(e?.message || t('actionFailed')),
    })
  }

  async function restore(c) {
    if (await confirm({ title: t('restoreTitle'), message: t('restoreMessage'), confirmLabel: t('restore') })) run(c.id, { action: 'RESTORE' })
  }

  return (
    <div className="admin-table-wrap">
      {confirmDialog}
      <div className="admin-filters">
        <select
          className="admin-status-select"
          aria-label={t('statusFilter')}
          value={status}
          onChange={e => { setStatus(e.target.value); goToPage(0) }}
        >
          <option value="ALL">{t('all')}</option>
          {['VISIBLE', 'REMOVED', 'DELETED'].map(s => <option key={s} value={s}>{t(`status.${s}`)}</option>)}
        </select>
        <input
          className="app-input"
          style={{ maxWidth: 260 }}
          placeholder={t('searchPlaceholder')}
          aria-label={t('searchComments')}
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
      </div>
      {error && !target && <div className="app-alert error" role="alert" style={{ marginBottom: 12 }}>{error}</div>}
      {notice && <div className="app-alert success" role="status" style={{ marginBottom: 12 }}>{notice}</div>}

      {selected.size > 0 && (
        <div className="admin-bulk-bar" role="toolbar" aria-label={t('bulkActions')}>
          <span>{t('selectedBar', { count: selected.size })}</span>
          <button
            className="app-btn danger"
            onClick={() => { setError(''); setTarget('BULK') }}
            disabled={bulkModerate.isPending}
            aria-busy={bulkModerate.isPending && bulkModerate.variables?.action === 'REMOVE'}
          >
            {t('removeSelected')}
          </button>
          <button
            className="app-btn secondary"
            onClick={restoreSelected}
            disabled={bulkModerate.isPending}
            aria-busy={bulkModerate.isPending && bulkModerate.variables?.action === 'RESTORE'}
          >
            {t('restoreSelected')}
          </button>
          <button
            className="app-btn danger-outline"
            onClick={() => hardDeleteIds([...selected], t('selectedLabel', { count: selected.size }))}
            disabled={hardDelete.isPending}
            aria-busy={hardDelete.isPending && hardDelete.variables?.length > 1}
          >
            {t('deletePermanently')}
          </button>
          <button className="app-btn secondary" onClick={() => setSelected(new Set())}>{t('clearSelection')}</button>
        </div>
      )}

      {isLoading ? (
        <div className="admin-loading"><div className="vid-spinner" /></div>
      ) : (
        <>
          <div className="admin-table-meta">{t('showing', { shown: comments.length, total: totalElements.toLocaleString() })}</div>
          <table className="admin-table">
            <thead>
              <tr>
                <th style={{ width: 36 }}>
                  <input
                    ref={headerBox}
                    type="checkbox"
                    aria-label={t('selectAll')}
                    checked={allSelected}
                    onChange={toggleAll}
                  />
                </th>
                <th>{t('colComment')}</th>
                <th>{t('colAuthor')}</th>
                <th>{t('colVideo')}</th>
                <th>{t('colCreated')}</th>
                <th>{t('colStatus')}</th>
                <th>{t('colActions')}</th>
              </tr>
            </thead>
            <tbody>
              {comments.map(c => {
                const removed = c.status === 'REMOVED'
                const deleted = c.status === 'DELETED'
                const badgeClass = removed ? 'removed' : deleted ? 'deleted' : 'visible'
                const tip = removed
                  ? [c.moderationReason, c.moderationNote, c.moderatedBy && t('moderatedBy', { name: c.moderatedBy }), c.moderatedAt && formatDate(c.moderatedAt)]
                      .filter(Boolean).join(' · ')
                  : undefined
                return (
                  <tr key={c.id} className={`admin-table-row${selected.has(c.id) ? ' selected' : ''}`}>
                    <td>
                      <input
                        type="checkbox"
                        aria-label={t('selectComment', { id: c.id })}
                        checked={selected.has(c.id)}
                        onChange={() => toggleOne(c.id)}
                      />
                    </td>
                    <td>
                      {c.parentId && <span className="admin-reply-tag">{t('reply')}</span>}
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
                      <span className={`admin-badge ${badgeClass}`} title={tip}>
                        {t(`status.${c.status}`, { defaultValue: c.status })}
                      </span>
                      {removed && c.moderationReason && (
                        <div className="admin-cell-muted" title={tip}>{c.moderationReason}</div>
                      )}
                    </td>
                    <td>
                      <div className="admin-actions">
                        {deleted ? null : removed ? (
                          <button
                            className="admin-action-btn"
                            onClick={() => restore(c)}
                            title={t('restore')}
                            aria-busy={isBusyFor(moderate, c.id, x => x?.id)}
                          >
                            <span className="material-symbols-outlined" style={{ fontSize: 16 }}>restore</span>
                          </button>
                        ) : (
                          <button
                            className="admin-action-btn danger"
                            onClick={() => { setError(''); setTarget(c) }}
                            title={t('remove')}
                          >
                            <span className="material-symbols-outlined" style={{ fontSize: 16 }}>block</span>
                          </button>
                        )}
                        <button
                          className="admin-action-btn danger"
                          onClick={() => hardDeleteIds([c.id], t('thisComment'))}
                          disabled={hardDelete.isPending}
                          aria-busy={isBusyFor(hardDelete, c.id)}
                          title={t('deletePermTitle')}
                        >
                          <span className="material-symbols-outlined" style={{ fontSize: 16 }}>delete_forever</span>
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
          {totalPages > 1 && (
            <div className="admin-pagination">
              <button className="admin-page-btn" disabled={page === 0} onClick={() => goToPage(page - 1)}>‹</button>
              {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => (
                <button key={i} className={`admin-page-btn${page === i ? ' active' : ''}`} onClick={() => goToPage(i)}>{i + 1}</button>
              ))}
              <button className="admin-page-btn" disabled={page >= totalPages - 1} onClick={() => goToPage(page + 1)}>›</button>
            </div>
          )}
        </>
      )}

      {target && (
        <RemoveModal
          comment={target === 'BULK' ? null : target}
          count={target === 'BULK' ? selected.size : 0}
          saving={target === 'BULK' ? bulkModerate.isPending : moderate.isPending}
          error={error}
          onClose={() => setTarget(null)}
          onSubmit={body => target === 'BULK'
            ? runBulk(body, () => setTarget(null))
            : run(target.id, body, () => setTarget(null))}
        />
      )}
    </div>
  )
}
