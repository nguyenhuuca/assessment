import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMySuggestionsInfinite } from '../../hooks/useSuggestions.js'
import SuggestionCard from './SuggestionCard.jsx'

const FILTERS = [
  { status: '', labelKey: 'tabAll', icon: 'apps' },
  { status: 'PENDING', labelKey: 'tabPending', icon: 'hourglass_empty' },
  { status: 'APPROVED', labelKey: 'tabApproved', icon: 'task_alt' },
  { status: 'REJECTED', labelKey: 'tabRejected', icon: 'block' },
  { status: 'LIVE', labelKey: 'tabLive', icon: 'play_circle' },
]

/** "Đề xuất của tôi": the caller's suggestions with status filter pills and "Xem thêm" paging. */
export default function MySuggestionsSection({ onPlayVideo }) {
  const { t } = useTranslation(['history', 'common'])
  const [status, setStatus] = useState('')
  const query = useMySuggestionsInfinite(status)
  const pages = query.data?.pages ?? []
  const items = pages.flatMap(p => p.content)
  const total = pages[0]?.totalElements

  return (
    <section aria-labelledby="history-my-suggestions">
      <div className="hist-section-head">
        <h2 id="history-my-suggestions" className="hist-section-title">
          <span className="material-symbols-outlined" aria-hidden="true">verified</span>
          {t('mySuggestions')}
        </h2>
        {total != null && <span className="hist-count">{t('count', { count: total })}</span>}
      </div>

      <div className="hist-pills" role="tablist" aria-label={t('tabsLabel')}>
        {FILTERS.map(f => (
          <button
            key={f.status || 'ALL'}
            type="button"
            role="tab"
            aria-selected={status === f.status}
            className={`hist-pill${status === f.status ? ' active' : ''}`}
            onClick={() => setStatus(f.status)}
          >
            <span className="material-symbols-outlined" aria-hidden="true">{f.icon}</span>
            {t(f.labelKey)}
          </button>
        ))}
      </div>

      {query.isLoading ? (
        <div role="status" className="admin-cell-muted">{t('common:loading')}</div>
      ) : query.isError ? (
        <div className="app-alert error" role="alert">{t('loadFailed')}</div>
      ) : items.length === 0 ? (
        <p role="status" className="hist-empty">{status ? t('emptyFiltered') : t('empty')}</p>
      ) : (
        <ul className="hist-list">
          {items.map(s => <SuggestionCard key={s.id} suggestion={s} onPlayVideo={onPlayVideo} />)}
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
