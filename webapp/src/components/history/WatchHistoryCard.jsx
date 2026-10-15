import React, { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { compactNumber, relativeTime } from '../../i18n/format.js'

// Intl capitalises some phrases (vi: "Hôm qua"); they sit mid-sentence here ("Đã xem hôm qua").
const lowerFirst = (text) => (text ? text.charAt(0).toLocaleLowerCase() + text.slice(1) : text)
import { thumbnailUrl } from '../../utils/videoModel.js'
import { formatClock } from './watchHistoryUtils.js'

/** One watched video: thumbnail with duration / "Đã xem hết" chip, title, views, "Đã xem …", progress bar and a ⋮ menu. */
export default function WatchHistoryCard({ item, onPlay, onRemove }) {
  const { t } = useTranslation('history')
  const [menuOpen, setMenuOpen] = useState(false)
  const menuRef = useRef(null)
  const title = item.title || t('untitled')
  const thumb = thumbnailUrl(item.fileId)
  const hasDuration = item.durationSec > 0
  const pct = hasDuration ? Math.min(100, Math.max(0, (item.positionSec / item.durationSec) * 100)) : 0

  useEffect(() => {
    if (!menuOpen) return undefined
    const onDown = e => { if (!menuRef.current?.contains(e.target)) setMenuOpen(false) }
    const onKey = e => { if (e.key === 'Escape') setMenuOpen(false) }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [menuOpen])

  return (
    <li className="hist-watch-card">
      <button
        type="button"
        className="hist-watch-open"
        aria-label={t('playAria', { title })}
        onClick={() => onPlay?.(item.videoId, { startAt: item.completed ? 0 : item.positionSec })}
      >
        <span className="hist-watch-thumb">
          {thumb
            ? <img src={thumb} alt="" loading="lazy" />
            : <span className="material-symbols-outlined hist-watch-thumb-fallback" aria-hidden="true">movie</span>}
          {item.completed
            ? <span className="hist-watch-chip done">{t('watchedFully')}</span>
            : hasDuration && <span className="hist-watch-chip">{formatClock(item.durationSec)}</span>}
          {!item.completed && pct > 0 && (
            <span
              className="hist-watch-progress"
              role="progressbar"
              aria-label={t('badgeProgress')}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={Math.round(pct)}
            >
              <span className="hist-watch-progress-fill" style={{ width: `${pct}%` }} />
            </span>
          )}
        </span>
        <span className="hist-watch-body">
          <span className="hist-watch-title">{title}</span>
          <span className="hist-watch-meta">
            <span>{t('viewsCount', { views: compactNumber(item.viewCount) })}</span>
            <span className="hist-watch-ago">{t('watchedAgo', { time: lowerFirst(relativeTime(item.watchedAt)) })}</span>
          </span>
        </span>
      </button>

      <div className="hist-watch-menu" ref={menuRef}>
        <button
          type="button"
          className="hist-watch-menu-btn"
          aria-label={t('menuAria', { title })}
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen(o => !o)}
        >
          <span className="material-symbols-outlined" aria-hidden="true">more_vert</span>
        </button>
        {menuOpen && (
          <div className="hist-watch-popover" role="menu">
            <button
              type="button"
              role="menuitem"
              className="hist-watch-popover-item"
              onClick={() => { setMenuOpen(false); onRemove?.(item.videoId) }}
            >
              <span className="material-symbols-outlined" aria-hidden="true">delete</span>
              {t('removeEntry')}
            </button>
          </div>
        )}
      </div>
    </li>
  )
}
