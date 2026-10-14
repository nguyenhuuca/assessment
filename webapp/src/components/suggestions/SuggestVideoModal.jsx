import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { RECENT_SUGGESTIONS, useMySuggestions, useSubmitSuggestion } from '../../hooks/useSuggestions.js'
import SuggestionStatusChip from './SuggestionStatusChip.jsx'
import { NOTE_MAX, TITLE_MAX, displayStatus, errorKeyFor, validateSuggestionUrl } from './suggestionUtils.js'

/** "+" button for regular users: suggest a YouTube/Facebook link for admins to review. */
export default function SuggestVideoModal({ onClose, onPlayVideo, onOpenHistory }) {
  const { t } = useTranslation(['suggestions', 'common'])
  const submit = useSubmitSuggestion()
  const recent = useMySuggestions('', 0, { size: RECENT_SUGGESTIONS })
  const [url, setUrl] = useState('')
  const [title, setTitle] = useState('')
  const [note, setNote] = useState('')
  const [formError, setFormError] = useState('')
  const [success, setSuccess] = useState(false)

  function handleSubmit(e) {
    e.preventDefault()
    setSuccess(false)
    const key = validateSuggestionUrl(url)
    setFormError(key ? t(key) : '')
    if (key) return
    submit.mutate(
      { url: url.trim(), title: title.trim(), note: note.trim() },
      {
        onSuccess: () => {
          setUrl(''); setTitle(''); setNote('')
          setSuccess(true)
        },
      },
    )
  }

  function apiErrorText(err) {
    if (!err) return ''
    const key = errorKeyFor(err.code)
    return key ? t(key) : (err.message || t('common:errorGeneric'))
  }

  const error = formError || apiErrorText(submit.error)
  const items = (recent.data?.content ?? []).slice(0, RECENT_SUGGESTIONS)

  return (
    <div className="app-modal-backdrop" onClick={e => { if (e.target === e.currentTarget && !submit.isPending) onClose() }}>
      <form className="app-modal" role="dialog" aria-modal="true" aria-label={t('title')} onSubmit={handleSubmit} noValidate>
        <div className="app-modal-header">
          <span className="app-modal-title">{t('title')}</span>
          <button type="button" className="app-modal-close" onClick={onClose} aria-label={t('common:close')}>
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>close</span>
          </button>
        </div>
        <div className="app-modal-body">
          <p className="admin-cell-muted" style={{ marginTop: 0 }}>{t('intro')}</p>
          {error && <div className="app-alert error" role="alert">{error}</div>}
          {success && !error && <div className="app-alert success" role="status">{t('submitted')}</div>}

          <label className="app-label" htmlFor="suggest-url">{t('urlLabel')}</label>
          <input
            id="suggest-url"
            className="app-input"
            type="url"
            inputMode="url"
            value={url}
            placeholder={t('urlPlaceholder')}
            autoFocus
            onChange={e => setUrl(e.target.value)}
          />
          <label className="app-label" htmlFor="suggest-title" style={{ marginTop: 12 }}>{t('titleLabel')}</label>
          <input
            id="suggest-title"
            className="app-input"
            value={title}
            maxLength={TITLE_MAX}
            placeholder={t('titlePlaceholder')}
            onChange={e => setTitle(e.target.value)}
          />
          <label className="app-label" htmlFor="suggest-note" style={{ marginTop: 12 }}>{t('noteLabel')}</label>
          <textarea
            id="suggest-note"
            className="app-input"
            rows={2}
            value={note}
            maxLength={NOTE_MAX}
            placeholder={t('notePlaceholder')}
            onChange={e => setNote(e.target.value)}
          />

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 20 }}>
            <strong style={{ fontSize: 13 }}>{t('recent')}</strong>
            <button type="button" className="comment-action-link" onClick={onOpenHistory}>{t('viewAll')}</button>
          </div>
          {items.length === 0 ? (
            <p className="admin-cell-muted">{t('recentEmpty')}</p>
          ) : (
            <ul style={{ listStyle: 'none', padding: 0, margin: '8px 0 0', display: 'flex', flexDirection: 'column', gap: 8 }}>
              {items.map(s => {
                const label = s.title || s.sourceUrl || t('untitled')
                return (
                  <li key={s.id} style={{ display: 'flex', alignItems: 'center', gap: 8, justifyContent: 'space-between' }}>
                    <span style={{ minWidth: 0, display: 'flex', flexDirection: 'column', gap: 4 }}>
                      <span style={{ fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{label}</span>
                      <SuggestionStatusChip suggestion={s} />
                    </span>
                    {displayStatus(s) === 'LIVE' && s.liveVideoId != null && (
                      <button
                        type="button"
                        className="app-btn secondary"
                        aria-label={t('viewAria', { title: label })}
                        onClick={() => onPlayVideo?.(s.liveVideoId)}
                      >
                        {t('view')}
                      </button>
                    )}
                  </li>
                )
              })}
            </ul>
          )}
        </div>
        <div className="app-modal-footer">
          <button type="button" className="app-btn secondary" onClick={onClose} disabled={submit.isPending}>{t('common:close')}</button>
          <button type="submit" className="app-btn primary" disabled={submit.isPending} aria-busy={submit.isPending}>
            {t('submit')}
          </button>
        </div>
      </form>
    </div>
  )
}
