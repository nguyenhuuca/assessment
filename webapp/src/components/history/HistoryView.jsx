import React from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../hooks/useAuth.js'
import MySuggestionsSection from './MySuggestionsSection.jsx'
import SuggestLinkCard from './SuggestLinkCard.jsx'

/** History page. Sections are stacked, so a watch-history section can be added next to suggestions later. */
export default function HistoryView({ onPlayVideo }) {
  const { t } = useTranslation('history')
  const { isLoggedIn } = useAuth()

  return (
    <div className="hist-page">
      <header className="hist-header">
        <h1 className="hist-title">{t('title')}</h1>
        <p className="hist-subtitle">{t('subtitle')}</p>
      </header>
      {isLoggedIn ? (
        <>
          <SuggestLinkCard />
          <MySuggestionsSection onPlayVideo={onPlayVideo} />
        </>
      ) : (
        <p role="status" className="hist-empty">{t('signIn')}</p>
      )}
    </div>
  )
}
