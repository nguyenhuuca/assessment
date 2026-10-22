import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useCreateImports, usePreviewImport } from '../../hooks/useAdmin.js'
import { SCHEDULE_STEP_MINUTES, errorText, localToIso, parseLines, validateSchedule } from '../../utils/importUtils.js'

function ResultList({ items, results }) {
  const { t } = useTranslation('admin')
  return (
    <ul className="import-line-list" aria-label={t('results')}>
      {results.map(r => (
        <li key={r.line} className={r.jobId ? 'ok' : 'bad'}>
          {t('resultLine', {
            line: r.line,
            url: items[r.line - 1] ? ` (${items[r.line - 1].url})` : '',
            text: r.jobId ? t('jobCreated', { id: r.jobId }) : errorText(r.errorCode),
          })}
        </li>
      ))}
    </ul>
  )
}

export default function ImportForm() {
  const { t } = useTranslation('admin')
  const [text, setText] = useState('')
  const [title, setTitle] = useState('')
  const [mode, setMode] = useState('NOW')
  const [when, setWhen] = useState('')
  const [errors, setErrors] = useState([])
  const [serverError, setServerError] = useState('')
  const [results, setResults] = useState(null)
  const [sent, setSent] = useState([])
  const create = useCreateImports()
  const preview = usePreviewImport()

  const parsed = parseLines(text)
  const single = parsed.items.length === 1 && parsed.errors.length === 0

  function fetchTitle() {
    setServerError('')
    preview.mutate(parsed.items[0].url, {
      onSuccess: res => setTitle((res?.data ?? res)?.title || ''),
      onError: e => setServerError(e?.message ? errorText(e.message) : t('fetchTitleFailed')),
    })
  }

  function submit(e) {
    e.preventDefault()
    setServerError('')
    setResults(null)
    const errs = parsed.errors.map(x => (x.line ? { ...x, message: t('lineError', { line: x.line, message: x.message }) } : x))
    if (parsed.items.length === 0 && errs.length === 0) errs.push({ line: 0, message: t('needOneUrl') })
    if (mode === 'LATER') {
      const msg = validateSchedule(when)
      if (msg) errs.push({ line: 0, message: msg })
    }
    setErrors(errs)
    if (errs.length) return

    const items = single && title.trim() ? [{ ...parsed.items[0], title: title.trim() }] : parsed.items
    const scheduledAt = mode === 'LATER' ? localToIso(when) : null
    create.mutate({ items, scheduledAt }, {
      onSuccess: res => {
        setSent(items)
        setResults((res?.data ?? res)?.results ?? [])
        setText('')
        setTitle('')
      },
      onError: er => setServerError(er?.message ? errorText(er.message) : t('actionFailed')),
    })
  }

  return (
    <form className="import-form" onSubmit={submit} noValidate>
      <label className="app-label" htmlFor="import-urls">{t('importUrlsLabel')}</label>
      <textarea
        id="import-urls"
        className="app-input"
        rows={5}
        placeholder={t('importUrlsPlaceholder')}
        value={text}
        onChange={e => setText(e.target.value)}
      />
      {single && (
        <div className="import-title-row">
          <input
            className="app-input"
            aria-label={t('titleLabel')}
            placeholder={t('titleLabel')}
            value={title}
            onChange={e => setTitle(e.target.value)}
          />
          <button type="button" className="app-btn secondary" onClick={fetchTitle} disabled={preview.isPending} aria-busy={preview.isPending}>
            {t('fetchTitle')}
          </button>
        </div>
      )}
      <div className="import-radios" role="radiogroup" aria-label={t('importWhen')}>
        <label><input type="radio" name="import-mode" checked={mode === 'NOW'} onChange={() => setMode('NOW')} /> {t('importNow')}</label>
        <label><input type="radio" name="import-mode" checked={mode === 'LATER'} onChange={() => setMode('LATER')} /> {t('importLater')}</label>
        <input
          type="datetime-local"
          step={SCHEDULE_STEP_MINUTES * 60}
          className={mode === 'LATER' ? 'app-input import-schedule' : 'app-input import-schedule import-schedule-idle'}
          disabled={mode !== 'LATER'}
          aria-label={t('scheduleAria')}
          value={when}
          onChange={e => setWhen(e.target.value)}
        />
      </div>
      {errors.length > 0 && (
        <div className="app-alert error" role="alert">
          {errors.map((er, i) => <div key={i}>{er.message}</div>)}
        </div>
      )}
      {serverError && <div className="app-alert error" role="alert">{serverError}</div>}
      {results && <ResultList items={sent} results={results} />}
      <div>
        <button type="submit" className="app-btn primary" disabled={create.isPending} aria-busy={create.isPending}>{t('importSubmit')}</button>
      </div>
    </form>
  )
}
