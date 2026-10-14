import React from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../hooks/useAuth.js'
import MySuggestionsSection from './MySuggestionsSection.jsx'

/** History page. Sections are stacked, so a watch-history section can be added next to suggestions later. */
export default function HistoryView({ onPlayVideo }) {
  const { t } = useTranslation('history')
  const { isLoggedIn } = useAuth()

  return (
    <div style={{ padding: 24, maxWidth: 860, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 32 }}>
      <h1 style={{ fontFamily: 'var(--font-brand)', letterSpacing: 3, fontSize: 28, margin: 0 }}>{t('title')}</h1>
      {isLoggedIn ? (
        <MySuggestionsSection onPlayVideo={onPlayVideo} />
      ) : (
        <p role="status" className="admin-cell-muted">{t('signIn')}</p>
      )}
    </div>
  )
}
