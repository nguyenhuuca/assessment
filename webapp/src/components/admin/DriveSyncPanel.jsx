import React, { useState } from 'react'
import { useDriveSyncFiles, useDriveSyncRun, useStartDriveSyncRun } from '../../hooks/useAdmin.js'
import { useAuth } from '../../hooks/useAuth.js'
import { isSuperAdmin } from '../../utils/roles.js'
import { formatTime } from '../../utils/importUtils.js'
import ConfirmModal from '../common/ConfirmModal.jsx'
import { useTranslation } from 'react-i18next'

const DEFAULT_MAX_BATCH = 50

function formatSize(bytes) {
  if (bytes === null || bytes === undefined) return '—'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let v = bytes
  let i = 0
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++ }
  return `${i === 0 ? v : v.toFixed(1)} ${units[i]}`
}

function ItemProgress({ item }) {
  const { t } = useTranslation('admin')
  const total = item.totalBytes || 0
  const pct = total > 0 ? Math.max(0, Math.min(100, Math.round((item.downloadedBytes / total) * 100))) : 0
  return (
    <div>
      <div className="import-progress" role="progressbar" aria-label={t('progressOf', { name: item.name })}
        aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}>
        <div className="import-progress-fill" style={{ width: `${pct}%` }} />
      </div>
      <div className="admin-cell-muted">{pct}% · {formatSize(item.downloadedBytes)} / {formatSize(item.totalBytes)}</div>
    </div>
  )
}

function RunProgress({ run }) {
  const { t } = useTranslation('admin')
  return (
    <div className="admin-table-wrap">
      <div className="admin-table-meta">
        {run.active ? t('syncing') : t('lastRun')} · {t('runStarted', { time: formatTime(run.startedAt) })}
        {run.finishedAt ? ` · ${t('runFinished', { time: formatTime(run.finishedAt) })}` : ''}
      </div>
      {(run.items ?? []).map(it => (
        <div key={it.fileId} className="drive-sync-run-item" data-testid="run-item">
          <span>{it.name}</span>
          <span><span className={`admin-badge ${String(it.status).toLowerCase()}`}>{t(`status.${it.status}`, { defaultValue: it.status })}</span></span>
          <ItemProgress item={it} />
          <span className="import-error">{it.errorCode || ''}</span>
        </div>
      ))}
    </div>
  )
}

function ConfirmSyncModal({ files, pending, onCancel, onConfirm }) {
  const { t } = useTranslation('admin')
  const overwrite = files.filter(f => f.state === 'SYNCED').length
  return (
    <ConfirmModal
      title={t('confirmSyncTitle', { count: files.length })}
      ariaLabel={t('confirmSyncAria')}
      message={t('confirmSyncMessage')}
      confirmLabel={t('forceSync')}
      pending={pending}
      onCancel={onCancel}
      onConfirm={onConfirm}
    >
      <ul className="drive-sync-confirm-list">
        {files.map(f => (
          <li key={f.fileId}>
            <span>{f.name}</span>
            <span className="admin-cell-muted">{formatSize(f.sizeBytes)}</span>
          </li>
        ))}
      </ul>
      {overwrite > 0 && (
        <div className="app-alert error" role="alert" style={{ marginTop: 12 }}>
          {t('overwriteWarning', { count: overwrite })}
        </div>
      )}
    </ConfirmModal>
  )
}

export default function DriveSyncPanel() {
  const { t } = useTranslation('admin')
  const { user } = useAuth()
  const canSync = isSuperAdmin(user?.role)
  const { data, isLoading } = useDriveSyncFiles()
  const { run, active } = useDriveSyncRun()
  const start = useStartDriveSyncRun()
  const [selected, setSelected] = useState(() => new Set())
  const [onlyUnsynced, setOnlyUnsynced] = useState(false)
  const [error, setError] = useState('')
  const [confirming, setConfirming] = useState(false)

  const payload = data?.data ?? data ?? {}
  const allFiles = payload.files ?? []
  const orphans = payload.orphans ?? []
  const maxBatch = payload.maxBatch ?? DEFAULT_MAX_BATCH
  const files = onlyUnsynced ? allFiles.filter(f => f.state !== 'SYNCED') : allFiles

  const lockReason = !canSync ? t('driveNoPermission') : active ? t('driveRunActive') : undefined
  const locked = !canSync || active
  const atCap = selected.size >= maxBatch

  function toggle(id) {
    setSelected(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else if (next.size < maxBatch) next.add(id)
      return next
    })
  }

  function selectAllUnsynced() {
    const ids = allFiles.filter(f => f.state !== 'SYNCED').map(f => f.fileId).slice(0, maxBatch)
    setSelected(new Set(ids))
  }

  function forceSync() {
    setError('')
    start.mutate([...selected], {
      onSuccess: () => setSelected(new Set()),
      onError: e => setError(e?.message || t('actionFailed')),
      onSettled: () => setConfirming(false),
    })
  }

  return (
    <div className="drive-sync-panel">
      <div className="admin-table-wrap">
        <div className="drive-sync-toolbar">
          <label>
            <input type="checkbox" checked={onlyUnsynced} onChange={e => setOnlyUnsynced(e.target.checked)} />
            {t('onlyUnsynced')}
          </label>
          <button className="app-btn secondary" disabled={locked} title={lockReason} onClick={selectAllUnsynced}>
            {t('selectAllUnsynced')}
          </button>
          <button className="app-btn primary" disabled={locked || selected.size === 0 || start.isPending}
            aria-busy={start.isPending}
            title={lockReason} onClick={() => setConfirming(true)}>
            {t('forceSyncButton', { count: selected.size })}
          </button>
          <span className="admin-cell-muted">{t('maxBatch', { count: maxBatch })}</span>
        </div>
        {error && <div className="app-alert error" role="alert" style={{ margin: '12px 0' }}>{error}</div>}
        {isLoading ? (
          <div className="admin-loading"><div className="vid-spinner" /></div>
        ) : (
          <>
            <div className="admin-table-meta">{t('showingFiles', { shown: files.length, total: allFiles.length })}</div>
            <div className="import-scroll">
              <table className="admin-table">
                <thead>
                  <tr><th></th><th>{t('colFileName')}</th><th>{t('colSize')}</th><th>{t('colCreatedAt')}</th><th>{t('colState')}</th><th>{t('colDbTitle')}</th></tr>
                </thead>
                <tbody>
                  {files.map(f => (
                    <tr key={f.fileId} className="admin-table-row">
                      <td>
                        <input
                          type="checkbox"
                          aria-label={t('selectFile', { name: f.name })}
                          checked={selected.has(f.fileId)}
                          disabled={locked || (atCap && !selected.has(f.fileId))}
                          title={lockReason}
                          onChange={() => toggle(f.fileId)}
                        />
                      </td>
                      <td>{f.name}</td>
                      <td className="admin-cell-muted">{formatSize(f.sizeBytes)}</td>
                      <td className="admin-cell-muted">{formatTime(f.createdAt)}</td>
                      <td><span className={`admin-badge ${String(f.state).toLowerCase()}`}>{t(`status.${f.state}`, { defaultValue: f.state })}</span></td>
                      <td className="admin-cell-muted">{f.dbTitle || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </div>

      {confirming && (
        <ConfirmSyncModal
          files={allFiles.filter(f => selected.has(f.fileId))}
          pending={start.isPending}
          onCancel={() => setConfirming(false)}
          onConfirm={forceSync}
        />
      )}

      {run && <RunProgress run={run} />}

      {orphans.length > 0 && (
        <div className="admin-table-wrap">
          <div className="admin-table-meta">{t('orphans', { count: orphans.length })}</div>
          <ul className="import-line-list">
            {orphans.map(o => <li key={o.videoSourceId}>{o.title} <span className="admin-cell-muted">({o.sourceId})</span></li>)}
          </ul>
        </div>
      )}
    </div>
  )
}
