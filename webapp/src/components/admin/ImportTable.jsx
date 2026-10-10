import React, { useState } from 'react'
import {
  useCancelImport, useRetryImport, useRunImportNow, useVideoImports,
} from '../../hooks/useAdmin.js'
import {
  canCancel, canRetry, canRunNow, errorText, formatMb, formatTime, isRunning, platformLabel,
} from '../../utils/importUtils.js'

const STATUSES = ['PENDING', 'DOWNLOADING', 'UPLOADING', 'DONE', 'FAILED', 'CANCELLED']

function Progress({ job }) {
  const pct = Math.max(0, Math.min(100, Math.round(job.progressPct ?? 0)))
  return (
    <div>
      <div>{job.status === 'UPLOADING' ? 'Đang upload lên Drive' : 'Đang tải về'}</div>
      <div className="import-progress" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}>
        <div className="import-progress-fill" style={{ width: `${pct}%` }} />
      </div>
      <div className="admin-cell-muted">{pct}% · {formatMb(job.downloadedBytes)} / {formatMb(job.totalBytes)}</div>
    </div>
  )
}

function StateCell({ job }) {
  if (isRunning(job)) return <Progress job={job} />
  if (job.status === 'DONE') {
    return <span className="admin-cell-muted">{job.ingested ? 'Đã lên app' : 'Chờ đồng bộ (≤15 phút)'}</span>
  }
  if (job.status === 'FAILED' || job.status === 'CANCELLED') {
    return <span className="import-error">{errorText(job.errorCode || (job.status === 'CANCELLED' ? 'CANCELLED' : undefined))}</span>
  }
  return <span className="admin-cell-muted">—</span>
}

export default function ImportTable() {
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
    mutation.mutate(id, { onError: e => setError(e?.message || 'Thao tác thất bại') })
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
          {STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
        </select>
      </div>
      {error && <div className="app-alert error" role="alert" style={{ marginBottom: 12 }}>{error}</div>}
      {isLoading ? (
        <div className="admin-loading"><div className="vid-spinner" /></div>
      ) : (
        <>
          <div className="admin-table-meta">SHOWING {jobs.length} OF {totalElements.toLocaleString()} ENTRIES</div>
          <div className="import-scroll">
            <table className="admin-table import-table">
              <thead>
                <tr>
                  <th>TIÊU ĐỀ</th><th>NỀN TẢNG</th><th>TRẠNG THÁI</th><th>TIẾN ĐỘ</th><th>THỜI GIAN</th><th>ACTIONS</th>
                </tr>
              </thead>
              <tbody>
                {jobs.map(j => (
                  <tr key={j.id} className="admin-table-row">
                    <td>{j.title || j.sourceUrl}</td>
                    <td className="admin-cell-muted">{platformLabel(j.platform)}</td>
                    <td><span className={`admin-badge ${String(j.status).toLowerCase()}`}>{j.status}</span></td>
                    <td><StateCell job={j} /></td>
                    <td className="admin-cell-muted">{formatTime(j.startedAt || j.scheduledAt || j.createdAt)}</td>
                    <td>
                      <div className="admin-actions">
                        {canRunNow(j) && (
                          <button className="app-btn secondary" onClick={() => act(runNow, j.id)} disabled={runNow.isPending}>Chạy ngay</button>
                        )}
                        {canCancel(j) && (
                          <button className="app-btn danger" onClick={() => act(cancel, j.id)} disabled={cancel.isPending}>Huỷ</button>
                        )}
                        {canRetry(j) && (
                          <button className="app-btn secondary" onClick={() => act(retry, j.id)} disabled={retry.isPending}>Thử lại</button>
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
