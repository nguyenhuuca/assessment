import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../hooks/useAuth.js'
import { useReports, useCreateReport, useRetryReport } from '../../hooks/useReports.js'
import { isSuperAdmin } from '../../utils/roles.js'
import { formatTime } from '../../utils/importUtils.js'

const TYPES = ['BUG', 'ENHANCEMENT', 'ISSUE']
const AUTOPILOT = [
  { value: '', labelKey: 'autopilot.NONE' },
  { value: 'AUTO_MERGE', labelKey: 'autopilot.AUTO_MERGE' },
  { value: 'AUTO', labelKey: 'autopilot.AUTO' },
]
const TITLE_MAX = 120
const DESCRIPTION_MAX = 5000

/** Backend sends `github.http.502`; split it into a key plus the status. */
function errorText(t, code) {
  const m = /^github\.http\.(\d+)$/.exec(code ?? '')
  if (m) return t('error.github.http', { status: m[1] })
  const key = /^github\.(notConfigured|timeout|io)$/.exec(code ?? '')?.[1]
  return t(`error.github.${key ?? 'unknown'}`)
}

function IssueLink({ url, children }) {
  return <a href={url} target="_blank" rel="noopener noreferrer">{children}</a>
}

function StateBadge({ report, t }) {
  if (report.status === 'FAILED' || !report.githubState) {
    return <span className="admin-badge removed">{t('list.failedBadge')}</span>
  }
  const cls = report.githubState === 'OPEN' ? 'published' : report.githubState === 'CLOSED' ? 'pending' : 'removed'
  return <span className={`admin-badge ${cls}`}>{t(`state.${report.githubState}`, { defaultValue: report.githubState })}</span>
}

export default function ReportPage({ fromPage }) {
  const { t } = useTranslation('report')
  const { user } = useAuth()
  const superAdmin = isSuperAdmin(user?.role)
  const [type, setType] = useState('BUG')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [autoLabel, setAutoLabel] = useState('')
  const [result, setResult] = useState(null)
  const [submitError, setSubmitError] = useState('')
  const [page, setPage] = useState(0)
  const create = useCreateReport()
  const retry = useRetryReport()
  const { data } = useReports(page)

  const rows = data?.data?.content ?? []
  const totalPages = data?.data?.totalPages ?? 0
  const valid = title.trim().length > 0 && description.trim().length > 0

  const context = {
    page: fromPage ?? null,
    path: window.location.pathname,
    appVersion: __APP_BUILD__,
    userAgent: navigator.userAgent,
    screenWidth: window.innerWidth,
  }

  function submit(e) {
    e.preventDefault()
    if (!valid || create.isPending) return
    setResult(null)
    setSubmitError('')
    create.mutate(
      { type, title: title.trim(), description: description.trim(), autoLabel: superAdmin && autoLabel ? autoLabel : null, context },
      {
        onSuccess: res => {
          setResult(res?.data ?? null)
          if (res?.data?.status !== 'FAILED') { setTitle(''); setDescription(''); setAutoLabel('') }
        },
        onError: err => setSubmitError(err?.message || t('error.github.unknown')),
      },
    )
  }

  // Server enforces ownership; show Retry to the reporter when the user name is known, else to SUPER_ADMIN only.
  const canRetry = r => r.status === 'FAILED' && (superAdmin || (!!user?.userName && r.reporter === user.userName))

  const ctxRows = [
    ['page', context.page ?? '—'], ['path', context.path], ['appVersion', context.appVersion],
    ['userAgent', context.userAgent], ['screenWidth', context.screenWidth],
  ]

  return (
    <div className="admin-root">
      <div className="admin-header"><h1 className="admin-title">{t('title')}</h1></div>
      <p className="admin-cell-muted">{t('intro')}</p>

      <form onSubmit={submit} style={{ maxWidth: 720, display: 'grid', gap: 14, marginBottom: 28 }}>
        <div>
          <span className="app-label" id="report-type-label">{t('typeLabel')}</span>
          <div className="settings-segment" role="group" aria-labelledby="report-type-label">
            {TYPES.map(k => (
              <button key={k} type="button" className={`settings-segment-btn${type === k ? ' active' : ''}`}
                aria-pressed={type === k} onClick={() => setType(k)}>{t(`type.${k}`)}</button>
            ))}
          </div>
        </div>
        <div>
          <label className="app-label" htmlFor="report-title">{t('titleLabel')}</label>
          <input id="report-title" className="app-input" maxLength={TITLE_MAX} value={title}
            onChange={e => setTitle(e.target.value)} />
          <div className="admin-cell-muted">{t('titleHint', { max: TITLE_MAX })}</div>
        </div>
        <div>
          <label className="app-label" htmlFor="report-description">{t('descriptionLabel')}</label>
          <textarea id="report-description" className="app-input" rows={6} maxLength={DESCRIPTION_MAX}
            value={description} onChange={e => setDescription(e.target.value)} />
          <div className="admin-cell-muted">{t('descriptionHint', { max: DESCRIPTION_MAX })}</div>
        </div>
        {superAdmin && (
          <div>
            <label className="app-label" htmlFor="report-autopilot">{t('autopilotLabel')}</label>
            <select id="report-autopilot" className="app-input" value={autoLabel} onChange={e => setAutoLabel(e.target.value)}>
              {AUTOPILOT.map(o => <option key={o.value} value={o.value}>{t(o.labelKey)}</option>)}
            </select>
          </div>
        )}
        <div>
          <span className="app-label">{t('contextTitle')}</span>
          <dl className="admin-cell-muted" style={{ margin: 0 }}>
            {ctxRows.map(([k, v]) => (
              <div key={k} style={{ display: 'flex', gap: 8 }}>
                <dt style={{ fontWeight: 600 }}>{t(`context.${k}`)}</dt>
                <dd style={{ margin: 0, wordBreak: 'break-all' }}>{String(v)}</dd>
              </div>
            ))}
          </dl>
        </div>
        <div className="app-alert error" role="note">{t('publicWarning')}</div>
        <div>
          <button type="submit" className="app-btn primary" disabled={!valid || create.isPending} aria-busy={create.isPending}>
            {create.isPending ? t('submitting') : t('submit')}
          </button>
        </div>
      </form>

      {submitError && <div className="app-alert error" role="alert">{submitError}</div>}
      {result?.status === 'CREATED' && (
        <div className="app-alert success" role="status">
          <IssueLink url={result.issueUrl}>{t('created', { number: result.issueNumber })}</IssueLink>
          {result.capReached && <div>{t('capReached')}</div>}
        </div>
      )}
      {result?.status === 'FAILED' && (
        <div className="app-alert error" role="alert">
          <div>{t('failed')}</div>
          <div>{errorText(t, result.error)}</div>
          <button type="button" className="app-btn secondary" disabled={retry.isPending}
            onClick={() => retry.mutate(result.id, { onSuccess: res => setResult(res?.data ?? null) })}>
            {retry.isPending ? t('retrying') : t('retry')}
          </button>
        </div>
      )}

      <h2 className="admin-title" style={{ fontSize: 18 }}>{t('list.title')}</h2>
      <div className="admin-table-wrap">
        <div className="admin-table-scroll">
          <table className="admin-table">
            <thead>
              <tr>
                <th>{t('list.colTitle')}</th><th>{t('list.colType')}</th><th>{t('list.colReporter')}</th>
                <th>{t('list.colCreated')}</th><th>{t('list.colState')}</th><th>{t('list.colIssue')}</th>
                <th>{t('list.colActions')}</th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 && <tr><td colSpan={7} className="admin-cell-muted">{t('list.empty')}</td></tr>}
              {rows.map(r => (
                <tr key={r.id} className="admin-table-row">
                  <td>{r.title}</td>
                  <td className="admin-cell-muted">{t(`type.${r.type}`, { defaultValue: r.type })}</td>
                  <td className="admin-cell-muted">{r.reporter ?? t('list.deletedUser')}</td>
                  <td className="admin-cell-muted">{formatTime(r.createdAt)}</td>
                  <td><StateBadge report={r} t={t} /></td>
                  <td>{r.issueUrl ? <IssueLink url={r.issueUrl}>#{r.issueNumber}</IssueLink> : '—'}</td>
                  <td>
                    {canRetry(r) && (
                      <button type="button" className="app-btn secondary" disabled={retry.isPending}
                        onClick={() => retry.mutate(r.id)}>
                        {t('retry')}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {totalPages > 1 && (
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 12 }}>
            <button type="button" className="app-btn secondary" disabled={page === 0}
              onClick={() => setPage(p => p - 1)} aria-label="prev">‹</button>
            <span className="admin-cell-muted">{page + 1} / {totalPages}</span>
            <button type="button" className="app-btn secondary" disabled={page + 1 >= totalPages}
              onClick={() => setPage(p => p + 1)} aria-label="next">›</button>
          </div>
        )}
      </div>
    </div>
  )
}
