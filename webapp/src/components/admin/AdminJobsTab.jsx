import React, { useCallback, useState } from 'react'
import { useTranslation } from 'react-i18next'
import OverlayTrigger from 'react-bootstrap/OverlayTrigger'
import Tooltip from 'react-bootstrap/Tooltip'
import CronEditModal from './CronEditModal.jsx'
import { describeCron } from '../../utils/cronText.js'
import { useAdminJobs, useUpdateJob, useRunJob, useJobRuns } from '../../hooks/useAdmin.js'
import { useAuth } from '../../hooks/useAuth.js'
import { isSuperAdmin } from '../../utils/roles.js'
import { formatTime } from '../../utils/importUtils.js'

const COLS_READ = 7

/** 850 → "850 ms", 2500 → "2.5 s", 75000 → "1m 15s". */
function formatDuration(ms) {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms} ms`
  if (ms < 60_000) return `${(ms / 1000).toFixed(1).replace(/\.0$/, '')} s`
  const s = Math.round(ms / 1000)
  return `${Math.floor(s / 60)}m ${s % 60}s`
}

function ResultBadge({ result }) {
  const { t } = useTranslation('admin')
  return <span className={`admin-badge ${result.toLowerCase()}`}>{t(`jobs.result.${result}`, { defaultValue: result })}</span>
}

function JobHistory({ job, colSpan }) {
  const { t } = useTranslation('admin')
  const { data, isLoading, isError } = useJobRuns(job.name, true, job.running)
  const runs = data?.data ?? []
  let body
  if (isLoading) body = <div className="admin-loading"><div className="vid-spinner" /></div>
  else if (isError) body = <div className="app-alert error" role="alert">{t('jobs.historyFailed')}</div>
  else if (runs.length === 0) body = <div className="admin-cell-muted">{t('jobs.noRuns')}</div>
  else body = (
    <table className="admin-table job-history-table">
      <thead>
        <tr>
          <th>{t('jobs.colStarted')}</th><th>{t('jobs.colTrigger')}</th><th>{t('jobs.colResult')}</th>
          <th>{t('jobs.colDuration')}</th><th>{t('jobs.colError')}</th>
        </tr>
      </thead>
      <tbody>
        {runs.map((r, i) => (
          <tr key={`${r.startedAt}-${i}`}>
            <td className="admin-cell-muted">{formatTime(r.startedAt)}</td>
            <td className="admin-cell-muted">{t(`jobs.trigger.${r.trigger}`, { defaultValue: r.trigger })}</td>
            <td><ResultBadge result={r.result} /></td>
            <td className="admin-cell-muted">{formatDuration(r.durationMs)}</td>
            <td className="job-history-error">{r.error || '—'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
  return (
    <tr className="job-history-row">
      <td colSpan={colSpan}>
        <div className="job-history" id={`job-history-${job.name}`}>
          <div className="admin-table-meta">{t('jobs.historyTitle')}</div>
          {body}
        </div>
      </td>
    </tr>
  )
}

function JobRow({ job, canEdit, onError }) {
  const { t, i18n } = useTranslation('admin')
  const update = useUpdateJob()
  const run = useRunJob()
  const [editing, setEditing] = useState(false)
  const [open, setOpen] = useState(false)
  const [editError, setEditError] = useState('')
  const closeEditor = useCallback(() => setEditing(false), [])
  const cronText = describeCron(job.cron, i18n.language) ?? job.cron
  const busy = job.running || run.isPending

  const fail = e => onError(e?.message || t('jobs.actionFailed'))
  const save = body => { onError(''); update.mutate({ name: job.name, body }, { onError: fail }) }

  return (
    <>
    <tr className="admin-table-row">
      <td>
        <button type="button" className="job-history-toggle" aria-expanded={open}
          aria-controls={`job-history-${job.name}`} aria-label={t('jobs.historyAria', { name: job.name })}
          onClick={() => setOpen(o => !o)}>
          <span className="material-symbols-outlined" aria-hidden="true">{open ? 'expand_more' : 'chevron_right'}</span>
          {job.name}
        </button>
      </td>
      <td className="admin-cell-muted">{job.description}</td>
      <td>
        <OverlayTrigger placement="top" overlay={<Tooltip id={`cron-tip-${job.name}`}>{cronText}</Tooltip>}>
          {canEdit ? (
            <button type="button" className="cron-cell-btn" aria-label={t('jobs.editCronAria', { name: job.name })}
              onClick={() => { setEditError(''); setEditing(true) }}>
              <code>{job.cron}</code>
              <span className="material-symbols-outlined" aria-hidden="true">edit</span>
            </button>
          ) : <code tabIndex={0}>{job.cron}</code>}
        </OverlayTrigger>
        {editing && (
          <CronEditModal
            job={job}
            pending={update.isPending}
            error={editError}
            onCancel={closeEditor}
            onSave={cron => update.mutate({ name: job.name, body: { cron } }, {
              onSuccess: closeEditor,
              onError: e => setEditError(e?.message || t('jobs.actionFailed')),
            })}
          />
        )}
      </td>
      <td className="admin-cell-muted">{job.zone || '—'}</td>
      <td>
        {canEdit ? (
          <input type="checkbox" aria-label={t('jobs.enabledAria', { name: job.name })}
            checked={job.enabled} disabled={update.isPending}
            onChange={e => save({ enabled: e.target.checked })} />
        ) : (
          <span className={`admin-badge ${job.enabled ? 'published' : 'removed'}`}>{job.enabled ? t('jobs.enabledOn') : t('jobs.enabledOff')}</span>
        )}
      </td>
      <td className="admin-cell-muted">{formatTime(job.nextRunAt)}</td>
      <td className="admin-cell-muted">
        {formatTime(job.lastRunAt)}
        {job.running ? (
          <> <span className="admin-badge pending">{t('jobs.running')}</span></>
        ) : job.lastResult && (
          <> <ResultBadge result={job.lastResult} /></>
        )}
      </td>
      {canEdit && (
        <td>
          <button className="app-btn primary" disabled={busy} aria-busy={busy}
            onClick={() => { onError(''); run.mutate(job.name, { onError: fail }) }}>
            {t('jobs.runNow')}
          </button>
        </td>
      )}
    </tr>
    {open && <JobHistory job={job} colSpan={canEdit ? COLS_READ + 1 : COLS_READ} />}
    </>
  )
}

export default function AdminJobsTab() {
  const { t } = useTranslation('admin')
  const { user } = useAuth()
  const canEdit = isSuperAdmin(user?.role)
  const { data, isLoading } = useAdminJobs()
  const [error, setError] = useState('')
  const jobs = data?.data ?? []

  return (
    <div className="admin-table-wrap">
      {!canEdit && <div className="admin-table-meta">{t('jobs.readOnly')}</div>}
      {error && <div className="app-alert error" role="alert" style={{ margin: '12px 0' }}>{error}</div>}
      {isLoading ? (
        <div className="admin-loading"><div className="vid-spinner" /></div>
      ) : (
        <div className="admin-table-scroll">
          <table className="admin-table">
            <thead>
              <tr>
                <th>{t('jobs.colName')}</th><th>{t('jobs.colDescription')}</th><th>{t('jobs.colCron')}</th>
                <th>{t('jobs.colZone')}</th><th>{t('jobs.colEnabled')}</th><th>{t('jobs.colNextRun')}</th>
                <th>{t('jobs.colLastRun')}</th>{canEdit && <th>{t('jobs.colActions')}</th>}
              </tr>
            </thead>
            <tbody>
              {jobs.length === 0 && <tr><td colSpan={8} className="admin-cell-muted">{t('jobs.noJobs')}</td></tr>}
              {jobs.map(j => <JobRow key={j.name} job={j} canEdit={canEdit} onError={setError} />)}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
