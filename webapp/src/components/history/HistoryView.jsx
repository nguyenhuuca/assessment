import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../hooks/useAuth.js'
import { useMySuggestionsInfinite } from '../../hooks/useSuggestions.js'
import { useWatchHistoryInfinite } from '../../hooks/useWatchHistory.js'
import MySuggestionsSection from './MySuggestionsSection.jsx'
import SuggestLinkCard from './SuggestLinkCard.jsx'
import WatchHistorySection from './WatchHistorySection.jsx'

const TAB_KEY = 'historyTab'
const TABS = [
  { key: 'watch', labelKey: 'tabWatch', icon: 'history' },
  { key: 'suggestions', labelKey: 'mySuggestions', icon: 'verified' },
]

function readTab() {
  try {
    const saved = localStorage.getItem(TAB_KEY)
    return TABS.some(x => x.key === saved) ? saved : 'watch'
  } catch {
    return 'watch'
  }
}

/** History page: pill tabs "Lịch sử xem" (default, remembered) and "Đề xuất của tôi". */
export default function HistoryView({ onPlayVideo }) {
  const { t } = useTranslation('history')
  const { isLoggedIn } = useAuth()
  const [tab, setTab] = useState(readTab)
  // Same query keys as the sections, so the counts cost no extra request once a section has loaded
  const watchCount = useWatchHistoryInfinite('').data?.pages?.[0]?.totalElements
  const suggestionCount = useMySuggestionsInfinite('', { enabled: !!isLoggedIn }).data?.pages?.[0]?.totalElements
  const counts = { watch: watchCount, suggestions: suggestionCount }

  function selectTab(key) {
    setTab(key)
    try { localStorage.setItem(TAB_KEY, key) } catch { /* storage unavailable: the tab just isn't remembered */ }
  }

  return (
    <div className="hist-page">
      <header className="hist-header">
        <h1 className="hist-title">{t('title')}</h1>
        <p className="hist-subtitle">{t(tab === 'watch' ? 'subtitleWatch' : 'subtitle')}</p>
      </header>
      {isLoggedIn ? (
        <>
          <div className="hist-pills hist-main-tabs" role="tablist" aria-label={t('sectionsLabel')}>
            {TABS.map(x => (
              <button
                key={x.key}
                type="button"
                role="tab"
                aria-selected={tab === x.key}
                className={`hist-pill${tab === x.key ? ' active' : ''}`}
                onClick={() => selectTab(x.key)}
              >
                <span className="material-symbols-outlined" aria-hidden="true">{x.icon}</span>
                {t(x.labelKey)}
                {counts[x.key] != null && <span className="hist-tab-count">{counts[x.key]}</span>}
              </button>
            ))}
          </div>
          {tab === 'watch' ? (
            <WatchHistorySection onPlayVideo={onPlayVideo} />
          ) : (
            <>
              <SuggestLinkCard />
              <MySuggestionsSection onPlayVideo={onPlayVideo} />
            </>
          )}
        </>
      ) : (
        <p role="status" className="hist-empty">{t('signIn')}</p>
      )}
    </div>
  )
}
