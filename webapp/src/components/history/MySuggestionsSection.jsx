import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { formatDate } from '../../i18n/format.js'
import { useMySuggestionsInfinite } from '../../hooks/useSuggestions.js'
import SuggestionStatusChip from '../suggestions/SuggestionStatusChip.jsx'
import { displayStatus } from '../suggestions/suggestionUtils.js'

const FILTERS = [
  { status: '', labelKey: 'tabAll' },
  { status: 'PENDING', labelKey: 'tabPending' },
  { status: 'APPROVED', labelKey: 'tabApproved' },
  { status: 'REJECTED', labelKey: 'tabRejected' },
  { status: 'LIVE', labelKey: 'tabLive' },
]

/** "Đề xuất của tôi": the caller's suggestions with status filter tabs and "Xem thêm" paging. */
export default function MySuggestionsSection({ onPlayVideo }) {
  const { t } = useTranslation(['history', 'suggestions', 'common'])
  const [status, setStatus] = useState('')
  const query = useMySuggestionsInfinite(status)
  const items = (query.data?.pages ?? []).flatMap(p => p.content)

  return (
    <section aria-labelledby="history-my-suggestions">
      <h2 id="history-my-suggestions" style={{ fontSize: 18, margin: '0 0 12px' }}>{t('mySuggestions')}</h2>
      <div className="app-tabs" role="tablist" aria-label={t('tabsLabel')} style={{ overflowX: 'auto' }}>
        {FILTERS.map(f => (
          <button
            key={f.status || 'ALL'}
            type="button"
            role="tab"
            aria-selected={status === f.status}
            className={`app-tab${status === f.status ? ' active' : ''}`}
            onClick={() => setStatus(f.status)}
          >
            {t(f.labelKey)}
          </button>
        ))}
      </div>

      {query.isLoading ? (
        <div role="status" className="admin-cell-muted">{t('common:loading')}</div>
      ) : query.isError ? (
        <div className="app-alert error" role="alert">{t('loadFailed')}</div>
      ) : items.length === 0 ? (
        <p role="status" className="admin-cell-muted">{status ? t('emptyFiltered') : t('empty')}</p>
      ) : (
        <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: 12 }}>
          {items.map(s => {
            const heading = s.title || s.sourceUrl
            return (
              <li key={s.id} className="admin-stat-card" style={{ display: 'flex', gap: 12, justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div style={{ minWidth: 0, display: 'flex', flexDirection: 'column', gap: 6 }}>
                  <a
                    href={s.sourceUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    title={t('openLink')}
                    style={{ fontWeight: 600, overflowWrap: 'anywhere' }}
                  >
                    {heading}
                  </a>
                  {s.note && <span className="admin-cell-muted">{s.note}</span>}
                  <span className="admin-cell-muted">{s.createdAt ? formatDate(s.createdAt) : ''}</span>
                  <SuggestionStatusChip suggestion={s} />
                </div>
                {displayStatus(s) === 'LIVE' && s.liveVideoId != null && (
                  <button
                    type="button"
                    className="app-btn secondary"
                    aria-label={t('suggestions:viewAria', { title: heading })}
                    onClick={() => onPlayVideo?.(s.liveVideoId)}
                  >
                    {t('suggestions:view')}
                  </button>
                )}
              </li>
            )
          })}
        </ul>
      )}

      {query.hasNextPage && (
        <div style={{ display: 'flex', justifyContent: 'center', marginTop: 16 }}>
          <button
            type="button"
            className="app-btn secondary"
            onClick={() => query.fetchNextPage()}
            disabled={query.isFetchingNextPage}
            aria-busy={query.isFetchingNextPage}
          >
            {t('loadMore')}
          </button>
        </div>
      )}
    </section>
  )
}
