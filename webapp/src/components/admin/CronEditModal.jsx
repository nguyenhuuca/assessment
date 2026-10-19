import React, { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useCronPreview } from '../../hooks/useAdmin.js'
import { CRON_FIELDS, cronShapeError, describeCron, fieldIndexAt } from '../../utils/cronText.js'
import { formatDate } from '../../i18n/format.js'

const EXAMPLES = ['0 */5 * * * *', '0 */15 * * * *', '0 0 * * * *', '0 30 3 * * *', '0 0 9 * * MON']

// Allowed values per field, shown for the field under the caret
const RANGES = {
  second: '0-59', minute: '0-59', hour: '0-23', dayOfMonth: '1-31',
  month: '1-12 · JAN-DEC', dayOfWeek: '0-7 · SUN-SAT (0, 7 = SUN)',
}

/** crontab.guru-style editor: live description, field under the caret, syntax help and the next runs. */
export default function CronEditModal({ job, pending, error, onCancel, onSave }) {
  const { t, i18n } = useTranslation('admin')
  const [cron, setCron] = useState(job.cron)
  const [caret, setCaret] = useState(0)
  const [debounced, setDebounced] = useState(job.cron)
  const inputRef = useRef(null)

  useEffect(() => {
    inputRef.current?.focus()
    const onKey = e => { if (e.key === 'Escape' && !pending) onCancel() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onCancel, pending])

  // Ask the server for the next runs only once typing pauses (300 ms)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(cron.trim()), 300)
    return () => clearTimeout(timer)
  }, [cron])

  const trimmed = cron.trim()
  const shapeError = cronShapeError(trimmed)
  const text = shapeError === 'fieldCount' ? null : describeCron(trimmed, i18n.language)
  const localError = shapeError ? t(`jobs.editor.error.${shapeError}`) : (!text ? t('jobs.editor.error.invalid') : '')
  const preview = useCronPreview(debounced, job.zone, !localError && debounced === trimmed)
  const runs = preview.data?.data ?? []
  const shownError = localError || (preview.isError ? preview.error?.message : '') || error
  const active = CRON_FIELDS[fieldIndexAt(cron, caret)]
  const canSave = !localError && !preview.isError && trimmed !== job.cron && !pending

  const track = e => setCaret(e.target.selectionStart ?? 0)

  return (
    <div className="app-modal-backdrop" onClick={pending ? undefined : onCancel}>
      <div className="app-modal cron-editor" role="dialog" aria-modal="true"
        aria-label={t('jobs.editor.title', { name: job.name })} onClick={e => e.stopPropagation()}>
        <div className="app-modal-header">
          <span className="app-modal-title">{t('jobs.editor.title', { name: job.name })}</span>
          <button className="app-modal-close" onClick={onCancel} disabled={pending} aria-label={t('common:close')}>
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>close</span>
          </button>
        </div>
        <div className="app-modal-body">
          <p className={`cron-editor-text${shownError ? ' invalid' : ''}`} aria-live="polite">
            {shownError || `“${text}”`}
          </p>
          <p className="cron-editor-next">
            {runs.length > 0 && <>
              {t('jobs.editor.nextAt')} <strong>{formatRun(runs[0])}</strong>
              {runs.length > 1 && <> · {t('jobs.editor.then')} {runs.slice(1).map(formatRun).join(', ')}</>}
            </>}
            {!shownError && preview.isSuccess && runs.length === 0 && t('jobs.editor.never')}
            {job.zone && <span className="admin-cell-muted"> ({job.zone})</span>}
          </p>
          <input
            ref={inputRef}
            className="app-input cron-editor-input"
            aria-label={t('jobs.cronAria', { name: job.name })}
            aria-invalid={!!shownError}
            spellCheck={false}
            value={cron}
            onChange={e => { setCron(e.target.value); track(e) }}
            onKeyUp={track}
            onClick={track}
            onSelect={track}
          />
          <div className="cron-editor-fields">
            {CRON_FIELDS.map(f => (
              <span key={f} className={f === active ? 'active' : ''}>{t(`jobs.editor.field.${f}`)}</span>
            ))}
          </div>
          <table className="cron-editor-legend">
            <tbody>
              <tr><td>*</td><td>{t('jobs.editor.legend.any')}</td></tr>
              <tr><td>,</td><td>{t('jobs.editor.legend.list')}</td></tr>
              <tr><td>-</td><td>{t('jobs.editor.legend.range')}</td></tr>
              <tr><td>/</td><td>{t('jobs.editor.legend.step')}</td></tr>
              <tr className="active"><td>{RANGES[active]}</td><td>{t('jobs.editor.legend.allowed', { field: t(`jobs.editor.field.${active}`) })}</td></tr>
            </tbody>
          </table>
          <p className="admin-cell-muted" style={{ margin: '8px 0' }}>{t('jobs.editor.secondsNote')}</p>
          <div className="cron-editor-examples">
            {EXAMPLES.map(ex => (
              <button key={ex} type="button" className="app-btn secondary" title={describeCron(ex, i18n.language)}
                onClick={() => { setCron(ex); inputRef.current?.focus() }}>
                <code>{ex}</code>
              </button>
            ))}
          </div>
        </div>
        <div className="app-modal-footer">
          <button className="app-btn secondary" onClick={onCancel} disabled={pending}>{t('common:cancel')}</button>
          <button className="app-btn primary" onClick={() => onSave(trimmed)} disabled={!canSave} aria-busy={pending}>
            {t('jobs.save')}
          </button>
        </div>
      </div>
    </div>
  )
}

function formatRun(iso) {
  return formatDate(iso, { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' })
}
