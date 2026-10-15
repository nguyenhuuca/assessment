import React, { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { useClearWatchHistory, useRemoveWatchEntry, useWatchHistoryInfinite } from '../../hooks/useWatchHistory.js'
import { useSettings, useUpdateSettings } from '../../hooks/useSettings.js'
import { useConfirm } from '../../hooks/useConfirm.jsx'
import WatchHistoryCard from './WatchHistoryCard.jsx'
import { groupByDay } from './watchHistoryUtils.js'

const SEARCH_DEBOUNCE_MS = 300

/** "Lịch sử xem": debounced title search, pause switch, clear-all, day groups and "Xem thêm" paging. */
export default function WatchHistorySection({ onPlayVideo }) {
  const { t } = useTranslation(['history', 'common'])
  const queryClient = useQueryClient()
  const [input, setInput] = useState('')
  const [q, setQ] = useState('')
  const [confirm, confirmDialog] = useConfirm()

  useEffect(() => {
    const id = setTimeout(() => setQ(input.trim()), SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(id)
  }, [input])

  const query = useWatchHistoryInfinite(q)
  const { data: settings } = useSettings()
  const updateSettings = useUpdateSettings()
  const remove = useRemoveWatchEntry()
  const clear = useClearWatchHistory()

  const paused = settings?.incognitoEnabled ?? false
  const pages = query.data?.pages
  const items = useMemo(() => (pages ?? []).flatMap(p => p.content), [pages])
  const total = pages?.[0]?.totalElements ?? 0
  const groups = useMemo(() => groupByDay(items), [items])

  function togglePause() {
    updateSettings.mutate(
      { incognitoEnabled: !paused },
      { onSuccess: () => queryClient.invalidateQueries({ queryKey: ['settings'] }) },
    )
  }

  async function clearAll() {
    const ok = await confirm({
      title: t('clearTitle'),
      message: t('clearMessage', { count: total }),
      confirmLabel: t('clearConfirm'),
      danger: true,
    })
    if (ok) clear.mutate()
  }

  return (
    <section aria-label={t('tabWatch')}>
      <div className="hist-watch-controls">
        <div className="hist-watch-search">
          <span className="material-symbols-outlined" aria-hidden="true">search</span>
          <input
            type="search"
            className="hist-watch-search-input"
            value={input}
            maxLength={100}
            placeholder={t('searchPlaceholder')}
            aria-label={t('searchLabel')}
            onChange={e => setInput(e.target.value)}
          />
        </div>
        <div className="hist-watch-actions">
          <div className="hist-watch-pause">
            <span id="hist-watch-pause-label">{t('pauseHistory')}</span>
            <button
              type="button"
              role="switch"
              aria-checked={paused}
              aria-labelledby="hist-watch-pause-label"
              className={`settings-toggle${paused ? ' on' : ''}`}
              disabled={updateSettings.isPending}
              onClick={togglePause}
            >
              <span className="settings-toggle-knob" />
            </button>
          </div>
          <button type="button" className="hist-watch-clear" onClick={clearAll} disabled={total === 0 || clear.isPending}>
            <span className="material-symbols-outlined" aria-hidden="true">delete_sweep</span>
            {t('clearAll')}
          </button>
        </div>
      </div>

      {paused && (
        <div className="hist-watch-paused" role="status">
          <span className="material-symbols-outlined" aria-hidden="true">pause_circle</span>
          {t('pausedBanner')}
        </div>
      )}

      {query.isLoading ? (
        <div role="status" className="admin-cell-muted">{t('common:loading')}</div>
      ) : query.isError ? (
        <div className="app-alert error" role="alert">{t('loadFailedWatch')}</div>
      ) : items.length === 0 ? (
        <p role="status" className="hist-empty">{q ? t('emptyWatchSearch') : t('emptyWatch')}</p>
      ) : (
        groups.map(g => (
          <div key={g.key} className="hist-watch-group">
            <h3 className="hist-watch-group-title">{t(`group.${g.key}`)}</h3>
            <ul className="hist-watch-list">
              {g.items.map(it => (
                <WatchHistoryCard key={it.videoId} item={it} onPlay={onPlayVideo} onRemove={id => remove.mutate(id)} />
              ))}
            </ul>
          </div>
        ))
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
      {confirmDialog}
    </section>
  )
}
