import React, { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  useAdminSuggestions, useApproveSuggestion, useRejectSuggestion, useReopenSuggestion, useBulkSuggestions,
} from '../../hooks/useAdminSuggestions.js'
import { useConfirm } from '../../hooks/useConfirm.jsx'
import { isBusyFor } from '../../utils/mutationBusy.js'
import { formatDate as formatDateI18n } from '../../i18n/format.js'
import ModalHeader from '../common/ModalHeader.jsx'
import BulkActionBar from './BulkActionBar.jsx'
import { useBulkSelection } from '../../hooks/useBulkSelection.js'
import { formatBulkResult } from '../../utils/bulkResult.js'

const FILTERS = ['PENDING', 'APPROVED', 'REJECTED', 'LIVE']
const REASONS = ['DUPLICATE', 'INAPPROPRIATE', 'LOW_QUALITY', 'COPYRIGHT', 'OTHER']
const TITLE_MAX = 200
const NOTE_MAX = 300
const BULK_MAX = 100

const isPending = s => s.status === 'PENDING'

const REOPEN_ERRORS = {
  'error.suggestion.cannotReopen': 'sugg.cannotReopen',
  'error.suggestion.alreadyPending': 'sugg.alreadyPending',
  'error.suggestion.duplicate': 'sugg.duplicate',
}

function formatDate(s) {
  return s ? formatDateI18n(s, { dateStyle: 'medium', timeStyle: 'short' }) : '—'
}

function useErrorText() {
  const { t } = useTranslation('admin')
  return err => {
    if (err?.code === 'error.import.disabled') return t('sugg.importDisabled')
    if (err?.code === 'error.suggestion.noteRequired') return t('otherNeedsNote')
    if (err?.code === 'error.suggestion.notPending') return t('sugg.notPending')
    const key = REOPEN_ERRORS[err?.code]
    if (key) return t(key)
    return err?.message || t('actionFailed')
  }
}

function ModalShell({ label, icon, tone, onClose, children, footer }) {
  const { t } = useTranslation('admin')
  return (
    <div className="app-modal-backdrop" onClick={onClose}>
      <div className="app-modal" role="dialog" aria-label={label} onClick={e => e.stopPropagation()}>
        <ModalHeader icon={icon} tone={tone} title={label} onClose={onClose} />
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
      icon="check_circle"
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

function RejectModal({ label, subtitle, onClose, onSubmit, saving, error }) {
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
      label={label}
      icon="block"
      tone="danger"
      onClose={onClose}
      footer={<button className="app-btn danger" onClick={submit} disabled={saving} aria-busy={saving}>{t('sugg.reject')}</button>}
    >
      <p className="admin-cell-muted" style={{ marginTop: 0, wordBreak: 'break-all' }}>{subtitle}</p>
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
      {s.rereviewCount > 0 && (
        <div className="admin-cell-muted">
          <span className="admin-badge pending">{t('sugg.rereviewBadge', { count: s.rereviewCount, max: s.maxRereviews })}</span>
        </div>
      )}
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
  const reopen = useReopenSuggestion()
  const [confirm, confirmDialog] = useConfirm()
  const bulk = useBulkSuggestions()
  const [bulkError, setBulkError] = useState('')
  const [bulkReject, setBulkReject] = useState(false)
  const [notice, setNotice] = useState(null) // { summary, reasons: [{ code, count }] }
  const headerBox = useRef(null)

  const pageData = data?.data ?? data ?? {}
  const items = pageData?.content ?? []
  const totalPages = pageData?.totalPages ?? 0
  const totalElements = pageData?.totalElements ?? 0

  // Selection is per page and status filter; only PENDING rows can be moderated in bulk.
  const pendingIds = new Set(items.filter(isPending).map(s => s.id))
  const sel = useBulkSelection({
    pageIds: items.map(s => s.id),
    resetKey: `${page}|${status}`,
    max: BULK_MAX,
    isSelectable: id => pendingIds.has(id),
  })
  const { allSelected, someSelected, toggleAll } = sel

  useEffect(() => {
    if (headerBox.current) headerBox.current.indeterminate = someSelected && !allSelected
  })

  function runBulk(body) {
    setBulkError('')
    setNotice(null)
    bulk.mutate({ ids: sel.selectedIds, ...body }, {
      onSuccess: res => {
        const r = res?.data ?? res ?? {}
        const counts = {}
        ;(r.skipped ?? []).forEach(x => { counts[x.code] = (counts[x.code] ?? 0) + 1 })
        setNotice({
          summary: formatBulkResult(r, t),
          reasons: Object.entries(counts).map(([code, count]) => ({ code, count })),
        })
        setBulkReject(false)
        sel.clear()
      },
      onError: e => setBulkError(errorText(e)),
    })
  }

  async function approveSelected() {
    const ok = await confirm({
      title: t('sugg.bulkApproveTitle'),
      message: t('sugg.bulkApproveMessage', { count: sel.count }),
      confirmLabel: t('sugg.approve'),
    })
    if (ok) runBulk({ action: 'APPROVE' })
  }

  function open(kind, s) { setError(''); setTarget({ kind, s }) }

  function run(mutation, vars) {
    setError('')
    mutation.mutate(vars, {
      onSuccess: () => setTarget(null),
      onError: e => setError(errorText(e)),
    })
  }

  async function reopenRow(s) {
    if (reopen.isPending) return
    const ok = await confirm({
      title: t('sugg.reopenTitle'),
      message: t('sugg.reopenMessage', { title: s.title || s.sourceUrl }),
      confirmLabel: t('sugg.reopen'),
    })
    if (!ok) return
    setError('')
    reopen.mutate({ id: s.id }, { onError: e => setError(errorText(e)) })
  }

  return (
    <div className="admin-table-wrap">
      {confirmDialog}
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
      {bulkError && !bulkReject && <div className="app-alert error" role="alert" style={{ marginBottom: 12 }}>{bulkError}</div>}
      {notice && (
        <div className="app-alert success" role="status" style={{ marginBottom: 12 }}>
          {notice.summary}
          {notice.reasons.length > 0 && (
            <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>
              {notice.reasons.map(r => (
                <li key={r.code}>{t('bulkSkippedLabel')}: {t(`bulk.reason.${r.code}`, { defaultValue: t('bulk.reason.unknown') })} ({r.count})</li>
              ))}
            </ul>
          )}
        </div>
      )}
      <BulkActionBar count={sel.count} onClear={sel.clear}>
        <button className="app-btn primary" onClick={approveSelected} disabled={bulk.isPending} aria-busy={bulk.isPending}>
          {t('sugg.approve')}
        </button>
        <button className="app-btn danger" onClick={() => { setBulkError(''); setBulkReject(true) }} disabled={bulk.isPending}>
          {t('sugg.reject')}
        </button>
      </BulkActionBar>
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
                  <th style={{ width: 36 }}>
                    <input
                      ref={headerBox}
                      type="checkbox"
                      aria-label={t('selectAll')}
                      checked={allSelected}
                      disabled={pendingIds.size === 0}
                      onChange={toggleAll}
                    />
                  </th>
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
                  <tr key={s.id} className={`admin-table-row${sel.isSelected(s.id) ? ' selected' : ''}`}>
                    <td>
                      {isPending(s) && (
                        <input
                          type="checkbox"
                          aria-label={t('sugg.selectRow', { id: s.id })}
                          checked={sel.isSelected(s.id)}
                          onChange={() => sel.toggle(s.id)}
                        />
                      )}
                    </td>
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
                        {s.canReopen && (
                          <button
                            className="admin-action-btn"
                            onClick={() => reopenRow(s)}
                            title={t('sugg.reopen')}
                            aria-label={t('sugg.reopen')}
                            disabled={isBusyFor(reopen, s.id, v => v?.id)}
                            aria-busy={isBusyFor(reopen, s.id, v => v?.id)}
                          >
                            <span className="material-symbols-outlined" style={{ fontSize: 16 }}>undo</span>
                          </button>
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
          label={t('sugg.rejectDialog')}
          subtitle={target.s.title || target.s.sourceUrl}
          saving={reject.isPending}
          error={error}
          onClose={() => setTarget(null)}
          onSubmit={({ reason, note }) => run(reject, { id: target.s.id, reason, note })}
        />
      )}
      {bulkReject && (
        <RejectModal
          label={t('sugg.bulkRejectTitle', { count: sel.count })}
          subtitle={t('sugg.bulkRejectSubtitle', { count: sel.count })}
          saving={bulk.isPending}
          error={bulkError}
          onClose={() => setBulkReject(false)}
          onSubmit={({ reason, note }) => runBulk({ action: 'REJECT', reason, ...(note ? { note } : {}) })}
        />
      )}
    </div>
  )
}
