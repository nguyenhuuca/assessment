import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { relativeTime } from '../../i18n/format.js'
import SuggestionStatusChip from '../suggestions/SuggestionStatusChip.jsx'
import { displayStatus } from '../suggestions/suggestionUtils.js'

// Thumbnail placeholder per status: no thumbnail exists until the video is ingested.
const THUMB_ICON = { PENDING: 'hourglass_empty', APPROVED: 'sync', LIVE: 'play_arrow', REJECTED: 'block' }

const PLATFORM = {
  YOUTUBE: { cls: 'youtube', icon: 'smart_display', label: 'YouTube' },
  FACEBOOK: { cls: 'facebook', icon: 'video_library', label: 'Facebook' },
}

/** One row of "Đề xuất của tôi": status thumbnail, platform + status badges, title, source link and actions. */
export default function SuggestionCard({ suggestion: s, onPlayVideo }) {
  const { t } = useTranslation(['history', 'suggestions'])
  const [copied, setCopied] = useState(false)
  const status = displayStatus(s)
  const heading = s.title || t('suggestions:untitled')
  const platform = PLATFORM[s.platform]
  const canPlay = status === 'LIVE' && s.liveVideoId != null

  function copyLink() {
    navigator.clipboard?.writeText(s.sourceUrl)
      .then(() => { setCopied(true); setTimeout(() => setCopied(false), 1500) })
      .catch(() => {})
  }

  return (
    <li className={`hist-card status-${status.toLowerCase()}`}>
      <button
        type="button"
        className="hist-thumb"
        disabled={!canPlay}
        aria-label={canPlay ? t('suggestions:viewAria', { title: heading }) : undefined}
        aria-hidden={canPlay ? undefined : true}
        tabIndex={canPlay ? 0 : -1}
        onClick={() => canPlay && onPlayVideo?.(s.liveVideoId)}
      >
        <span className={`material-symbols-outlined${status === 'APPROVED' ? ' hist-spin' : ''}`}>{THUMB_ICON[status]}</span>
        <span className="hist-thumb-label">{t(`suggestions:status.${status}`)}</span>
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
      </div>

      <div className="hist-card-actions">
        {canPlay && (
          <button type="button" className="hist-action primary" onClick={() => onPlayVideo?.(s.liveVideoId)}>
            <span className="material-symbols-outlined" aria-hidden="true">play_circle</span>
            {t('watchOnApp')}
          </button>
        )}
        <button type="button" className="hist-action" onClick={copyLink}>
          <span className="material-symbols-outlined" aria-hidden="true">{copied ? 'check' : 'content_copy'}</span>
          {copied ? t('copied') : t('copyLink')}
        </button>
      </div>
    </li>
  )
}
