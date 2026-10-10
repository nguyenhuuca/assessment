import React, { useState } from 'react'
import { useCreateImports, usePreviewImport } from '../../hooks/useAdmin.js'
import { errorText, localToIso, parseLines, validateSchedule } from '../../utils/importUtils.js'

function ResultList({ items, results }) {
  return (
    <ul className="import-line-list" aria-label="Kết quả">
      {results.map(r => (
        <li key={r.line} className={r.jobId ? 'ok' : 'bad'}>
          Dòng {r.line}{items[r.line - 1] ? ` (${items[r.line - 1].url})` : ''}:{' '}
          {r.jobId ? `Đã tạo job #${r.jobId}` : errorText(r.errorCode)}
        </li>
      ))}
    </ul>
  )
}

export default function ImportForm() {
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
      onError: e => setServerError(e?.message ? errorText(e.message) : 'Không lấy được tiêu đề'),
    })
  }

  function submit(e) {
    e.preventDefault()
    setServerError('')
    setResults(null)
    const errs = parsed.errors.map(x => (x.line ? { ...x, message: `Dòng ${x.line}: ${x.message}` } : x))
    if (parsed.items.length === 0 && errs.length === 0) errs.push({ line: 0, message: 'Vui lòng nhập ít nhất một URL' })
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
      onError: er => setServerError(er?.message ? errorText(er.message) : 'Thao tác thất bại'),
    })
  }

  return (
    <form className="import-form" onSubmit={submit} noValidate>
      <label className="app-label" htmlFor="import-urls">URL video (mỗi dòng một URL, tuỳ chọn: URL | Tiêu đề)</label>
      <textarea
        id="import-urls"
        className="app-input"
        rows={5}
        placeholder="https://www.youtube.com/watch?v=... | Tiêu đề"
        value={text}
        onChange={e => setText(e.target.value)}
      />
      {single && (
        <div className="import-title-row">
          <input
            className="app-input"
            aria-label="Tiêu đề"
            placeholder="Tiêu đề"
            value={title}
            onChange={e => setTitle(e.target.value)}
          />
          <button type="button" className="app-btn secondary" onClick={fetchTitle} disabled={preview.isPending}>
            Lấy tiêu đề
          </button>
        </div>
      )}
      <div className="import-radios" role="radiogroup" aria-label="Thời điểm tải">
        <label><input type="radio" name="import-mode" checked={mode === 'NOW'} onChange={() => setMode('NOW')} /> Tải ngay</label>
        <label><input type="radio" name="import-mode" checked={mode === 'LATER'} onChange={() => setMode('LATER')} /> Hẹn giờ</label>
        {mode === 'LATER' && (
          <input
            type="datetime-local"
            className="app-input"
            style={{ maxWidth: 220 }}
            aria-label="Thời gian hẹn giờ (GMT+7)"
            value={when}
            onChange={e => setWhen(e.target.value)}
          />
        )}
      </div>
      {errors.length > 0 && (
        <div className="app-alert error" role="alert">
          {errors.map((er, i) => <div key={i}>{er.message}</div>)}
        </div>
      )}
      {serverError && <div className="app-alert error" role="alert">{serverError}</div>}
      {results && <ResultList items={sent} results={results} />}
      <div>
        <button type="submit" className="app-btn primary" disabled={create.isPending}>Nhập video</button>
      </div>
    </form>
  )
}
