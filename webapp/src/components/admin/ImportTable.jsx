import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  useCancelImport, useRetryImport, useRunImportNow, useVideoImports,
} from '../../hooks/useAdmin.js'
import { isBusyFor } from '../../utils/mutationBusy.js'
import {
  canCancel, canRetry, canRunNow, errorText, formatMb, formatTime, isRunning, platformLabel,
} from '../../utils/importUtils.js'

const STATUSES = ['PENDING', 'DOWNLOADING', 'UPLOADING', 'DONE', 'FAILED', 'CANCELLED']

function Progress({ job }) {
  const { t } = useTranslation('admin')
  const pct = Math.max(0, Math.min(100, Math.round(job.progressPct ?? 0)))
  return (
    <div>
      <div>{job.status === 'UPLOADING' ? t('uploadingToDrive') : t('downloading')}</div>
      <div className="import-progress" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}>
        <div className="import-progress-fill" style={{ width: `${pct}%` }} />
      </div>
      <div className="admin-cell-muted">{pct}% · {formatMb(job.downloadedBytes)} / {formatMb(job.totalBytes)}</div>
    </div>
  )
}

function StateCell({ job }) {
  const { t } = useTranslation('admin')
  if (isRunning(job)) return <Progress job={job} />
  if (job.status === 'DONE') {
    return <span className="admin-cell-muted">{job.ingested ? t('ingested') : t('awaitingSync')}</span>
  }
  if (job.status === 'FAILED' || job.status === 'CANCELLED') {
    return <span className="import-error">{errorText(job.errorCode || (job.status === 'CANCELLED' ? 'CANCELLED' : undefined))}</span>
  }
  return <span className="admin-cell-muted">—</span>
}

export default function ImportTable() {
  const { t } = useTranslation('admin')
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('ALL')
  const [error, setError] = useState('')
  const { data, isLoading } = useVideoImports(page, status === 'ALL' ? null : status)
  const runNow = useRunImportNow()
  const cancel = useCancelImport()
  const retry = useRetryImport()

  const pageData = data?.data ?? data ?? {}
  const jobs = pageData?.content ?? []
  const totalPages = pageData?.totalPages ?? 0
  const totalElements = pageData?.totalElements ?? 0

  function act(mutation, id) {
    setError('')
    mutation.mutate(id, { onError: e => setError(e?.message || t('actionFailed')) })
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
          <option value="ALL">{t('all')}</option>
          {STATUSES.map(s => <option key={s} value={s}>{t(`status.${s}`)}</option>)}
        </select>
      </div>
      {error && <div className="app-alert error" role="alert" style={{ marginBottom: 12 }}>{error}</div>}
      {isLoading ? (
        <div className="admin-loading"><div className="vid-spinner" /></div>
      ) : (
        <>
          <div className="admin-table-meta">{t('showing', { shown: jobs.length, total: totalElements.toLocaleString() })}</div>
          <div className="import-scroll">
            {/* Fixed column widths: cell content changes with every status update and must not resize the table */}
            <table className="admin-table import-table">
              <colgroup>
                <col />
                <col className="import-col-platform" />
                <col className="import-col-status" />
                <col className="import-col-progress" />
                <col className="import-col-time" />
                <col className="import-col-actions" />
              </colgroup>
              <thead>
                <tr>
                  <th>{t('colTitle')}</th><th>{t('colPlatform')}</th><th>{t('colStatus')}</th><th>{t('colProgress')}</th><th>{t('colTime')}</th><th>{t('colActions')}</th>
                </tr>
              </thead>
              <tbody>
                {jobs.map(j => (
                  <tr key={j.id} className="admin-table-row">
                    <td><div className="import-cell-title" title={j.title || j.sourceUrl}>{j.title || j.sourceUrl}</div></td>
                    <td className="admin-cell-muted">{platformLabel(j.platform)}</td>
                    <td>
                      <span className={`admin-badge ${String(j.status).toLowerCase()}`}>{t(`status.${j.status}`, { defaultValue: j.status })}</span>
                      {j.visibility === 'COLLECTION_ONLY' && (
                        <span className="admin-badge collection-only" title={t('collectionOnlyHint')}>{t('collectionOnlyBadge')}</span>
                      )}
                    </td>
                    <td><StateCell job={j} /></td>
                    <td className="admin-cell-muted">{formatTime(j.startedAt || j.scheduledAt || j.createdAt)}</td>
                    <td>
                      <div className="admin-actions">
                        {canRunNow(j) && (
                          <button className="app-btn secondary" onClick={() => act(runNow, j.id)} disabled={runNow.isPending} aria-busy={isBusyFor(runNow, j.id)}>{t('runNow')}</button>
                        )}
                        {canCancel(j) && (
                          <button className="app-btn danger" onClick={() => act(cancel, j.id)} disabled={cancel.isPending} aria-busy={isBusyFor(cancel, j.id)}>{t('cancelJob')}</button>
                        )}
                        {canRetry(j) && (
                          <button className="app-btn secondary" onClick={() => act(retry, j.id)} disabled={retry.isPending} aria-busy={isBusyFor(retry, j.id)}>{t('retry')}</button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
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
    </div>
  )
}
