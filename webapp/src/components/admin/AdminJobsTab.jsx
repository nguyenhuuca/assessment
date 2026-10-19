import React, { useCallback, useState } from 'react'
import { useTranslation } from 'react-i18next'
import OverlayTrigger from 'react-bootstrap/OverlayTrigger'
import Tooltip from 'react-bootstrap/Tooltip'
import CronEditModal from './CronEditModal.jsx'
import { describeCron } from '../../utils/cronText.js'
import { useAdminJobs, useUpdateJob, useRunJob } from '../../hooks/useAdmin.js'
import { useAuth } from '../../hooks/useAuth.js'
import { isSuperAdmin } from '../../utils/roles.js'
import { formatTime } from '../../utils/importUtils.js'

function JobRow({ job, canEdit, onError }) {
  const { t, i18n } = useTranslation('admin')
  const update = useUpdateJob()
  const run = useRunJob()
  const [editing, setEditing] = useState(false)
  const [editError, setEditError] = useState('')
  const closeEditor = useCallback(() => setEditing(false), [])
  const cronText = describeCron(job.cron, i18n.language) ?? job.cron
  const busy = job.running || run.isPending

  const fail = e => onError(e?.message || t('jobs.actionFailed'))
  const save = body => { onError(''); update.mutate({ name: job.name, body }, { onError: fail }) }

  return (
    <tr className="admin-table-row">
      <td>{job.name}</td>
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
          <span className={`admin-badge ${job.enabled ? 'published' : 'removed'}`}>{job.enabled ? 'ON' : 'OFF'}</span>
        )}
      </td>
      <td className="admin-cell-muted">{formatTime(job.nextRunAt)}</td>
      <td className="admin-cell-muted">
        {formatTime(job.lastRunAt)}
        {job.running ? (
          <> <span className="admin-badge pending">{t('jobs.running')}</span></>
        ) : job.lastResult && (
          <> <span className={`admin-badge ${job.lastResult.toLowerCase()}`}>{t(`jobs.result.${job.lastResult}`, { defaultValue: job.lastResult })}</span></>
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
