import React, { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { relativeTime } from '../../i18n/format.js'
import SuggestionStatusChip from '../suggestions/SuggestionStatusChip.jsx'
import { displayStatus } from '../suggestions/suggestionUtils.js'
import { thumbnailUrl } from '../../utils/videoModel.js'
import { useRequestRereview } from '../../hooks/useSuggestions.js'

const MESSAGE_MAX = 300
const REREVIEW_ERRORS = {
  'error.suggestion.notRejected': 'rereviewNotRejected',
  'error.suggestion.rereviewLimit': 'rereviewLimit',
  'error.suggestion.duplicate': 'rereviewDuplicate',
  'error.suggestion.alreadyPending': 'rereviewDuplicate',
}

function RereviewModal({ suggestion, onClose }) {
  const { t } = useTranslation('history')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const rereview = useRequestRereview()
  const messageRef = useRef(null)
  const pending = rereview.isPending

  // Focus once on open. Kept apart from the Escape effect: onClose is a new function on
  // every parent render, so that effect re-runs and would steal focus back to the textarea.
  useEffect(() => { messageRef.current?.focus() }, [])

  useEffect(() => {
    const onKey = e => { if (e.key === 'Escape' && !pending) onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, pending])

  function submit() {
    setError('')
    rereview.mutate({ id: suggestion.id, message: message.trim() }, {
      onSuccess: onClose,
      onError: e => setError(REREVIEW_ERRORS[e?.code] ? t(REREVIEW_ERRORS[e.code]) : (e?.message || t('rereviewError'))),
    })
  }

  return (
    <div className="app-modal-backdrop" onClick={rereview.isPending ? undefined : onClose}>
      <div className="app-modal" role="dialog" aria-modal="true" aria-label={t('rereviewTitle')} onClick={e => e.stopPropagation()}>
        <div className="app-modal-header">
          <span className="app-modal-title">{t('rereviewTitle')}</span>
        </div>
        <div className="app-modal-body">
          <p className="admin-cell-muted" style={{ marginTop: 0 }}>{t('rereviewHint')}</p>
          <label className="app-label" htmlFor={`rereview-msg-${suggestion.id}`}>{t('rereviewMessageLabel')}</label>
          <textarea
            id={`rereview-msg-${suggestion.id}`}
            ref={messageRef}
            className="app-input"
            rows={3}
            maxLength={MESSAGE_MAX}
            value={message}
            onChange={e => setMessage(e.target.value)}
          />
          {error && <div className="app-alert error" role="alert" style={{ marginTop: 12 }}>{error}</div>}
        </div>
        <div className="app-modal-footer">
          <button type="button" className="app-btn secondary" onClick={onClose} disabled={rereview.isPending}>{t('rereviewCancel')}</button>
          <button type="button" className="app-btn primary" onClick={submit} disabled={rereview.isPending} aria-busy={rereview.isPending}>
            {t('rereviewSend')}
          </button>
        </div>
      </div>
    </div>
  )
}

// Thumbnail placeholder per status: no thumbnail exists until the video is ingested
// (a LIVE video shows its real thumbnail, falling back to this icon if it fails to load).
const THUMB_ICON = { PENDING: 'hourglass_empty', APPROVED: 'sync', LIVE: 'play_arrow', REJECTED: 'block' }

const PLATFORM = {
  YOUTUBE: { cls: 'youtube', icon: 'smart_display', label: 'YouTube' },
  FACEBOOK: { cls: 'facebook', icon: 'video_library', label: 'Facebook' },
}

/** One row of "Đề xuất của tôi": status thumbnail, platform + status badges, title, source link and actions. */
export default function SuggestionCard({ suggestion: s, onPlayVideo }) {
  const { t } = useTranslation(['history', 'suggestions'])
  const [copied, setCopied] = useState(false)
  const [thumbFailed, setThumbFailed] = useState(false)
  const [rereviewOpen, setRereviewOpen] = useState(false)
  const status = displayStatus(s)
  const heading = s.title || t('suggestions:untitled')
  const platform = PLATFORM[s.platform]
  const canPlay = status === 'LIVE' && s.liveVideoId != null
  const thumb = canPlay && !thumbFailed ? thumbnailUrl(s.liveFileId) : ''

  function copyLink() {
    navigator.clipboard?.writeText(s.sourceUrl)
      .then(() => { setCopied(true); setTimeout(() => setCopied(false), 1500) })
      .catch(() => {})
  }

  return (
    <li className={`hist-card status-${status.toLowerCase()}`}>
      <button
        type="button"
        className={`hist-thumb${thumb ? ' has-img' : ''}`}
        disabled={!canPlay}
        aria-label={canPlay ? t('suggestions:viewAria', { title: heading }) : undefined}
        aria-hidden={canPlay ? undefined : true}
        tabIndex={canPlay ? 0 : -1}
        onClick={() => canPlay && onPlayVideo?.(s.liveVideoId)}
      >
        {thumb ? (
          <>
            <img src={thumb} alt="" className="hist-thumb-img" loading="lazy" onError={() => setThumbFailed(true)} />
            <span className="hist-thumb-play material-symbols-outlined" aria-hidden="true">play_arrow</span>
          </>
        ) : (
          <>
            <span className={`material-symbols-outlined${status === 'APPROVED' ? ' hist-spin' : ''}`}>{THUMB_ICON[status]}</span>
            <span className="hist-thumb-label">{t(`suggestions:status.${status}`)}</span>
          </>
        )}
      </button>

      <div className="hist-card-body">
        <div className="hist-card-meta">
          {platform && (
            <span className={`hist-platform ${platform.cls}`}>
              <span className="material-symbols-outlined" aria-hidden="true">{platform.icon}</span>
              {platform.label}
            </span>
          )}
          <SuggestionStatusChip suggestion={s} />
          {s.createdAt && <span className="hist-card-time">{t('submittedAgo', { time: relativeTime(s.createdAt) })}</span>}
        </div>
        <h4 className="hist-card-title">{heading}</h4>
        <a className="hist-card-url" href={s.sourceUrl} target="_blank" rel="noopener noreferrer" title={t('openLink')}>
          {s.sourceUrl}
        </a>
        {s.note && <p className="hist-card-note">{s.note}</p>}
        {status === 'APPROVED' && <p className="hist-card-hint">{t('approvedHint')}</p>}
        {s.rereviewCount > 0 && (
          <p className="hist-card-hint">{t('rereviewCount', { count: s.rereviewCount, max: s.maxRereviews })}</p>
        )}
      </div>

      <div className="hist-card-actions">
        {canPlay && (
          <button type="button" className="hist-action primary" onClick={() => onPlayVideo?.(s.liveVideoId)}>
            <span className="material-symbols-outlined" aria-hidden="true">play_circle</span>
            {t('watchOnApp')}
          </button>
        )}
        {s.canRequestRereview && (
          <button type="button" className="hist-action" onClick={() => setRereviewOpen(true)}>
            <span className="material-symbols-outlined" aria-hidden="true">replay</span>
            {t('rereviewAction')}
          </button>
        )}
        <button type="button" className="hist-action" onClick={copyLink}>
          <span className="material-symbols-outlined" aria-hidden="true">{copied ? 'check' : 'content_copy'}</span>
          {copied ? t('copied') : t('copyLink')}
        </button>
      </div>
      {rereviewOpen && <RereviewModal suggestion={s} onClose={() => setRereviewOpen(false)} />}
    </li>
  )
}
