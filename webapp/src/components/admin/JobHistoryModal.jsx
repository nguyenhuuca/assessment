import React, { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import ModalHeader from '../common/ModalHeader.jsx'
import { useJobRuns } from '../../hooks/useAdmin.js'
import { formatTime } from '../../utils/importUtils.js'

/** 850 → "850 ms", 2500 → "2.5 s", 75000 → "1m 15s". */
function formatDuration(ms) {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms} ms`
  if (ms < 60_000) return `${(ms / 1000).toFixed(1).replace(/\.0$/, '')} s`
  const s = Math.round(ms / 1000)
  return `${Math.floor(s / 60)}m ${s % 60}s`
}

export function ResultBadge({ result }) {
  const { t } = useTranslation('admin')
  return <span className={`admin-badge ${result.toLowerCase()}`}>{t(`jobs.result.${result}`, { defaultValue: result })}</span>
}

const RESULTS = ['SUCCESS', 'FAILED', 'SKIPPED']

/** Run history of one job (job_run, newest 20), opened from Admin → Jobs. */
export default function JobHistoryModal({ job, onClose }) {
  const { t } = useTranslation('admin')
  const { data, isLoading, isError } = useJobRuns(job.name, true, job.running)
  const runs = data?.data ?? []
  const title = t('jobs.historyAria', { name: job.name })

  useEffect(() => {
    const onKey = e => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  let body
  if (isLoading) body = <div className="admin-loading"><div className="vid-spinner" /></div>
  else if (isError) body = <div className="app-alert error" role="alert">{t('jobs.historyFailed')}</div>
  else if (runs.length === 0) body = <p className="admin-cell-muted job-history-empty">{t('jobs.noRuns')}</p>
  else body = (
    <>
      <div className="job-history-summary">
        {RESULTS.map(r => {
          const n = runs.filter(x => x.result === r).length
          return n > 0 && <span key={r} className="job-history-count"><ResultBadge result={r} /> ×{n}</span>
        })}
      </div>
      <ul className="job-history-list">
        {runs.map((r, i) => (
          <li key={`${r.startedAt}-${i}`} className={`job-history-item ${r.result.toLowerCase()}`}>
            <div className="job-history-line">
              <ResultBadge result={r.result} />
              <span className="job-history-time">{formatTime(r.startedAt)}</span>
              <span className="job-history-trigger">
                <span className="material-symbols-outlined" aria-hidden="true">{r.trigger === 'MANUAL' ? 'touch_app' : 'schedule'}</span>
                {t(`jobs.trigger.${r.trigger}`, { defaultValue: r.trigger })}
              </span>
              <span className="job-history-duration">{formatDuration(r.durationMs)}</span>
            </div>
            {r.error && <code className="job-history-error">{r.error}</code>}
          </li>
        ))}
      </ul>
    </>
  )

  return (
    <div className="app-modal-backdrop" onClick={onClose}>
      <div className="app-modal job-history-modal" role="dialog" aria-modal="true" aria-label={title}
        onClick={e => e.stopPropagation()}>
        <ModalHeader icon="history" title={title} subtitle={job.description || t('jobs.historyTitle')} onClose={onClose} />
        <div className="app-modal-body">{body}</div>
      </div>
    </div>
  )
}
