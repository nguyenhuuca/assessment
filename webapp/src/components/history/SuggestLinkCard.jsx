import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSubmitSuggestion } from '../../hooks/useSuggestions.js'
import { NOTE_MAX, TITLE_MAX, errorKeyFor, validateSuggestionUrl } from '../suggestions/suggestionUtils.js'

/** Inline "submit a link" card at the top of History; title and note are optional and hidden behind a toggle. */
export default function SuggestLinkCard() {
  const { t } = useTranslation(['history', 'suggestions', 'common'])
  const submit = useSubmitSuggestion()
  const [url, setUrl] = useState('')
  const [title, setTitle] = useState('')
  const [note, setNote] = useState('')
  const [showMore, setShowMore] = useState(false)
  const [formError, setFormError] = useState('')
  const [success, setSuccess] = useState(false)

  function handleSubmit(e) {
    e.preventDefault()
    setSuccess(false)
    const key = validateSuggestionUrl(url)
    setFormError(key ? t(`suggestions:${key}`) : '')
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

  const apiError = submit.error
    ? (errorKeyFor(submit.error.code) ? t(`suggestions:${errorKeyFor(submit.error.code)}`) : (submit.error.message || t('common:errorGeneric')))
    : ''
  const error = formError || apiError

  return (
    <form className="hist-submit-card" onSubmit={handleSubmit} noValidate aria-labelledby="hist-submit-heading">
      <div className="hist-submit-glow" aria-hidden="true" />
      <div className="hist-submit-row">
        <div className="hist-submit-copy">
          <div className="hist-kicker">
            <span className="material-symbols-outlined" aria-hidden="true">add_link</span>
            {t('submitKicker')}
          </div>
          <h3 id="hist-submit-heading" className="hist-submit-heading">{t('submitHeading')}</h3>
          <p className="hist-submit-desc">{t('submitDesc')}</p>
        </div>
        <div className="hist-submit-form">
          <div className="hist-url-field">
            <span className="material-symbols-outlined" aria-hidden="true">link</span>
            <input
              className="hist-url-input"
              type="url"
              inputMode="url"
              aria-label={t('suggestions:urlLabel')}
              placeholder={t('submitPlaceholder')}
              value={url}
              onChange={e => setUrl(e.target.value)}
            />
          </div>
          <button type="submit" className="hist-submit-btn" disabled={submit.isPending} aria-busy={submit.isPending}>
            <span className="material-symbols-outlined" aria-hidden="true">cloud_upload</span>
            {t('submitButton')}
          </button>
        </div>
      </div>

      <button type="button" className="hist-more-toggle" aria-expanded={showMore} onClick={() => setShowMore(v => !v)}>
        <span className="material-symbols-outlined" aria-hidden="true">{showMore ? 'expand_less' : 'expand_more'}</span>
        {t('moreOptions')}
      </button>
      {showMore && (
        <div className="hist-more-fields">
          <input
            className="app-input"
            aria-label={t('suggestions:titleLabel')}
            placeholder={t('suggestions:titlePlaceholder')}
            maxLength={TITLE_MAX}
            value={title}
            onChange={e => setTitle(e.target.value)}
          />
          <input
            className="app-input"
            aria-label={t('suggestions:noteLabel')}
            placeholder={t('suggestions:notePlaceholder')}
            maxLength={NOTE_MAX}
            value={note}
            onChange={e => setNote(e.target.value)}
          />
        </div>
      )}

      {error && <div className="app-alert error hist-submit-alert" role="alert">{error}</div>}
      {success && !error && <div className="app-alert success hist-submit-alert" role="status">{t('suggestions:submitted')}</div>}
    </form>
  )
}
