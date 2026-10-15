import React from 'react'
import { useTranslation } from 'react-i18next'
import { useWatchHistorySummary } from '../../hooks/useWatchHistory.js'

/**
 * "Đã xem" chip when the video was finished, otherwise a thin resume progress line.
 * Reads the shared summary map (one request for the whole page), renders nothing for guests / unseen videos.
 */
export default function WatchedBadge({ videoId, className = '' }) {
  const { t } = useTranslation('history')
  const summary = useWatchHistorySummary()
  const entry = videoId == null ? null : summary.get(String(videoId))
  if (!entry) return null

  if (entry.completed) {
    return (
      <span className={`watched-badge done ${className}`.trim()}>
        <span className="material-symbols-outlined" aria-hidden="true">check_circle</span>
        {t('badgeWatched')}
      </span>
    )
  }
  const pct = entry.durationSec > 0 ? Math.min(100, Math.max(0, (entry.positionSec / entry.durationSec) * 100)) : 0
  if (pct <= 0) return null
  return (
    <span
      className={`watched-badge line ${className}`.trim()}
      role="progressbar"
      aria-label={t('badgeProgress')}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(pct)}
    >
      <span className="watched-badge-fill" style={{ width: `${pct}%` }} />
    </span>
  )
}
