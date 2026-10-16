import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  useAdminSuggestions, useApproveSuggestion, useRejectSuggestion,
} from '../../hooks/useAdminSuggestions.js'
import { isBusyFor } from '../../utils/mutationBusy.js'
import { formatDate as formatDateI18n } from '../../i18n/format.js'

const FILTERS = ['PENDING', 'APPROVED', 'REJECTED', 'LIVE']
const REASONS = ['DUPLICATE', 'INAPPROPRIATE', 'LOW_QUALITY', 'COPYRIGHT', 'OTHER']
const TITLE_MAX = 200
const NOTE_MAX = 300

function formatDate(s) {
  return s ? formatDateI18n(s, { dateStyle: 'medium', timeStyle: 'short' }) : '—'
}

function useErrorText() {
  const { t } = useTranslation('admin')
  return err => {
    if (err?.code === 'error.import.disabled') return t('sugg.importDisabled')
    if (err?.code === 'error.suggestion.notPending') return t('sugg.notPending')
    return err?.message || t('actionFailed')
  }
}

function ModalShell({ label, onClose, children, footer }) {
  const { t } = useTranslation('admin')
  return (
    <div className="app-modal-backdrop" onClick={onClose}>
      <div className="app-modal" role="dialog" aria-label={label} onClick={e => e.stopPropagation()}>
        <div className="app-modal-header">
          <span className="app-modal-title">{label}</span>
          <button className="app-modal-close" onClick={onClose} aria-label={t('close')}>
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>close</span>
          </button>
        </div>
        <div className="app-modal-body">{children}</div>
        <div className="app-modal-footer">
          <button className="app-btn secondary" onClick={onClose}>{t('cancel')}</button>
          {footer}
        </div>
      </div>
    </div>
  )
}

function ApproveModal({ suggestion, onClose, onSubmit, saving, error }) {
  const { t } = useTranslation('admin')
  const [title, setTitle] = useState(suggestion.title || '')
  return (
    <ModalShell
      label={t('sugg.approveDialog')}
      onClose={onClose}
      footer={(
        <button className="app-btn primary" onClick={() => onSubmit({ title: title.trim() })} disabled={saving} aria-busy={saving}>
          {t('sugg.approve')}
        </button>
      )}
    >
      <p className="admin-cell-muted" style={{ marginTop: 0, wordBreak: 'break-all' }}>{suggestion.sourceUrl}</p>
      <label className="app-label" htmlFor="sugg-title">{t('sugg.titleOverride')}</label>
      <input
        id="sugg-title"
        className="app-input"
        maxLength={TITLE_MAX}
        value={title}
        onChange={e => setTitle(e.target.value)}
      />
      {error && <div className="app-alert error" role="alert" style={{ marginTop: 12 }}>{error}</div>}
    </ModalShell>
  )
}

function RejectModal({ suggestion, onClose, onSubmit, saving, error }) {
  const { t } = useTranslation('admin')
  const [reason, setReason] = useState('')
  const [note, setNote] = useState('')
  const [validation, setValidation] = useState('')

  function submit() {
    if (!reason) { setValidation(t('chooseReasonError')); return }
    if (reason === 'OTHER' && !note.trim()) { setValidation(t('otherNeedsNote')); return }
    setValidation('')
    onSubmit({ reason, note: note.trim() })
  }

  return (
    <ModalShell
      label={t('sugg.rejectDialog')}
      onClose={onClose}
      footer={<button className="app-btn danger" onClick={submit} disabled={saving} aria-busy={saving}>{t('sugg.reject')}</button>}
    >
      <p className="admin-cell-muted" style={{ marginTop: 0, wordBreak: 'break-all' }}>{suggestion.title || suggestion.sourceUrl}</p>
      <label className="app-label" htmlFor="sugg-reason">{t('reason')}</label>
      <select id="sugg-reason" className="app-input" value={reason} onChange={e => setReason(e.target.value)}>
        <option value="">{t('chooseReason')}</option>
        {REASONS.map(r => <option key={r} value={r}>{t(`sugg.reasons.${r}`)}</option>)}
      </select>
      <label className="app-label" htmlFor="sugg-note" style={{ marginTop: 12 }}>
        {reason === 'OTHER' ? t('noteRequired') : t('note')}
      </label>
      <textarea
        id="sugg-note"
        className="app-input"
        rows={3}
        maxLength={NOTE_MAX}
        value={note}
        onChange={e => setNote(e.target.value)}
      />
      {(validation || error) && (
        <div className="app-alert error" role="alert" style={{ marginTop: 12 }}>{validation || error}</div>
      )}
    </ModalShell>
  )
}

function StatusCell({ s }) {
  const { t } = useTranslation('admin')
  let cls = 'pending'
  if (s.status === 'APPROVED') cls = 'visible'
  else if (s.status === 'REJECTED') cls = 'removed'
  return (
    <>
      <span className={`admin-badge ${cls}`}>{t(`status.${s.status}`, { defaultValue: s.status })}</span>
      {s.status === 'REJECTED' && s.rejectReason && (
        <div className="admin-cell-muted" title={s.rejectNote || undefined}>
          {t(`sugg.reasons.${s.rejectReason}`, { defaultValue: s.rejectReason })}
          {s.rejectNote ? ` · ${s.rejectNote}` : ''}
        </div>
      )}
      {s.status === 'APPROVED' && (
        <div className="admin-cell-muted">
          {s.live && <span className="admin-badge visible">{t('sugg.live')}</span>}
          {s.importJobId != null && <span> {t('sugg.importJob', { id: s.importJobId })}</span>}
        </div>
      )}
    </>
  )
}

export default function AdminSuggestionTable() {
  const { t } = useTranslation('admin')
  const errorText = useErrorText()
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('PENDING')
  const [target, setTarget] = useState(null) // { kind: 'approve' | 'reject', s }
  const [error, setError] = useState('')
  const { data, isLoading } = useAdminSuggestions(status === 'ALL' ? null : status, page)
  const approve = useApproveSuggestion()
  const reject = useRejectSuggestion()

  const pageData = data?.data ?? data ?? {}
  const items = pageData?.content ?? []
  const totalPages = pageData?.totalPages ?? 0
  const totalElements = pageData?.totalElements ?? 0

  function open(kind, s) { setError(''); setTarget({ kind, s }) }

  function run(mutation, vars) {
    setError('')
    mutation.mutate(vars, {
      onSuccess: () => setTarget(null),
      onError: e => setError(errorText(e)),
    })
  }

  return (
    <div className="admin-table-wrap">
      <div className="admin-filters">
        <select
          className="admin-status-select"
          aria-label={t('statusFilter')}
          value={status}
          onChange={e => { setStatus(e.target.value); setPage(0) }}
        >
          {FILTERS.map(s => <option key={s} value={s}>{t(`sugg.filters.${s}`)}</option>)}
          <option value="ALL">{t('all')}</option>
        </select>
      </div>
      {error && !target && <div className="app-alert error" role="alert" style={{ marginBottom: 12 }}>{error}</div>}

      {isLoading ? (
        <div className="admin-loading"><div className="vid-spinner" /></div>
      ) : (
        <>
          <div className="admin-table-meta">{t('showing', { shown: items.length, total: totalElements.toLocaleString() })}</div>
          <div className="admin-table-scroll">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>{t('sugg.colLink')}</th>
                  <th>{t('sugg.colTitle')}</th>
                  <th>{t('sugg.colNote')}</th>
                  <th>{t('sugg.colSuggester')}</th>
                  <th>{t('colCreated')}</th>
                  <th>{t('colStatus')}</th>
                  <th>{t('colActions')}</th>
                </tr>
              </thead>
              <tbody>
                {items.map(s => (
                  <tr key={s.id} className="admin-table-row">
                    <td className="admin-cell-muted" style={{ maxWidth: 220, wordBreak: 'break-all' }}>
                      <a href={s.sourceUrl} target="_blank" rel="noopener noreferrer">{s.sourceUrl}</a>
                      <div style={{ opacity: 0.6 }}>{s.platform}</div>
                    </td>
                    <td>{s.title || '—'}</td>
                    <td className="admin-cell-muted">{s.note || '—'}</td>
                    <td className="admin-cell-muted">{s.suggesterName || '—'}</td>
                    <td className="admin-cell-muted">{formatDate(s.createdAt)}</td>
                    <td><StatusCell s={s} /></td>
                    <td>
                      <div className="admin-actions">
                        {s.status === 'PENDING' && (
                          <>
                            <button
                              className="admin-action-btn"
                              onClick={() => open('approve', s)}
                              title={t('sugg.approve')}
                              aria-label={t('sugg.approve')}
                              aria-busy={isBusyFor(approve, s.id, v => v?.id)}
                            >
                              <span className="material-symbols-outlined" style={{ fontSize: 16 }}>check_circle</span>
                            </button>
                            <button
                              className="admin-action-btn danger"
                              onClick={() => open('reject', s)}
                              title={t('sugg.reject')}
                              aria-label={t('sugg.reject')}
                              aria-busy={isBusyFor(reject, s.id, v => v?.id)}
                            >
                              <span className="material-symbols-outlined" style={{ fontSize: 16 }}>block</span>
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {items.length === 0 && <div className="admin-cell-muted" style={{ padding: 16 }}>{t('sugg.empty')}</div>}
          {totalPages > 1 && (
            <div className="admin-pagination">
              <button className="admin-page-btn" disabled={page === 0} onClick={() => setPage(page - 1)}>‹</button>
              {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => (
                <button key={i} className={`admin-page-btn${page === i ? ' active' : ''}`} onClick={() => setPage(i)}>{i + 1}</button>
              ))}
              <button className="admin-page-btn" disabled={page >= totalPages - 1} onClick={() => setPage(page + 1)}>›</button>
            </div>
          )}
        </>
      )}

      {target?.kind === 'approve' && (
        <ApproveModal
          suggestion={target.s}
          saving={approve.isPending}
          error={error}
          onClose={() => setTarget(null)}
          onSubmit={({ title }) => run(approve, { id: target.s.id, title })}
        />
      )}
      {target?.kind === 'reject' && (
        <RejectModal
          suggestion={target.s}
          saving={reject.isPending}
          error={error}
          onClose={() => setTarget(null)}
          onSubmit={({ reason, note }) => run(reject, { id: target.s.id, reason, note })}
        />
      )}
    </div>
  )
}
