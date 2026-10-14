import React, { useEffect, useMemo, useState } from 'react'
import { useVideos } from '../../hooks/useVideos.js'
import { useCollectionVideoIds, useToggleCollectionItem } from '../../hooks/useCollections.js'
import { isBusyFor } from '../../utils/mutationBusy.js'
import { thumbnailUrl } from '../../utils/videoModel.js'
import { normalizeForSearch } from './collectionUtils.js'

const MAX_RESULTS = 50

/**
 * Search the public video list and add/remove videos to a collection without leaving the Library.
 * Membership comes from the collection's full id list (not just the page on screen); toggles made here
 * are applied locally on top so the button flips immediately. Adding twice is harmless (the API is idempotent).
 */
export default function AddVideosModal({ collection, onClose }) {
  const { data: videos = [], isLoading, error } = useVideos(null)
  const idsQuery = useCollectionVideoIds(collection.id)
  const toggle = useToggleCollectionItem()
  const [term, setTerm] = useState('')
  const [overrides, setOverrides] = useState(() => new Map())

  const serverIds = useMemo(() => new Set((idsQuery.data ?? []).map(String)), [idsQuery.data])
  const isInCollection = (id) => {
    const key = String(id)
    return overrides.has(key) ? overrides.get(key) : serverIds.has(key)
  }

  useEffect(() => {
    const onKey = e => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const results = useMemo(() => {
    const words = normalizeForSearch(term).split(/\s+/).filter(Boolean)
    const matches = words.length === 0
      ? videos
      : videos.filter(v => {
        const text = normalizeForSearch(`${v.title || ''} ${v.desc || ''}`)
        return words.every(w => text.includes(w))
      })
    return matches.slice(0, MAX_RESULTS)
  }, [videos, term])

  function handleToggle(video) {
    const key = String(video.id)
    const inCollection = isInCollection(video.id)
    toggle.mutate(
      { id: collection.id, videoId: video.id, inCollection, visibility: collection.visibility },
      { onSuccess: () => setOverrides(prev => new Map(prev).set(key, !inCollection)) },
    )
  }

  const loadError = error || idsQuery.error

  return (
    <div className="app-modal-backdrop" onClick={onClose}>
      <div
        className="app-modal"
        role="dialog"
        aria-modal="true"
        aria-label={`Thêm video vào ${collection.name}`}
        onClick={e => e.stopPropagation()}
        style={{ maxWidth: 560, width: '100%' }}
      >
        <div className="app-modal-header">
          <span className="app-modal-title">Thêm video vào "{collection.name}"</span>
          <button className="app-modal-close" onClick={onClose} aria-label="Close">
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>close</span>
          </button>
        </div>
        <div className="app-modal-body">
          <input
            className="app-input"
            type="search"
            placeholder="Tìm theo tiêu đề..."
            aria-label="Tìm video"
            value={term}
            onChange={e => setTerm(e.target.value)}
            autoFocus
          />
          {loadError && <div className="app-alert error" role="alert" style={{ marginTop: 12 }}>{loadError.message}</div>}
          {toggle.error && <div className="app-alert error" role="alert" style={{ marginTop: 12 }}>{toggle.error.message}</div>}
          <div style={{ marginTop: 12, maxHeight: '55vh', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 8 }}>
            {isLoading || idsQuery.isLoading ? (
              <div className="vid-spinner" />
            ) : results.length === 0 ? (
              <p className="admin-cell-muted" role="status">Không tìm thấy video nào</p>
            ) : results.map(v => {
              const inCollection = isInCollection(v.id)
              const busy = isBusyFor(toggle, v.id, vars => vars.videoId)
              return (
                <div key={v.id} style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  {v.fileId
                    ? <img src={thumbnailUrl(v.fileId)} alt="" loading="lazy"
                        style={{ width: 40, height: 64, objectFit: 'cover', borderRadius: 6, flexShrink: 0 }} />
                    : <span className="material-symbols-outlined" style={{ width: 40, textAlign: 'center' }}>movie</span>}
                  <span style={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {v.title || 'Untitled'}
                  </span>
                  <button
                    className={`app-btn ${inCollection ? 'secondary' : 'primary'}`}
                    aria-label={`${inCollection ? 'Bỏ' : 'Thêm'} "${v.title || 'Untitled'}"`}
                    aria-pressed={inCollection}
                    aria-busy={busy}
                    disabled={busy}
                    onClick={() => handleToggle(v)}
                  >
                    <span className="material-symbols-outlined" style={{ fontSize: 18 }}>{inCollection ? 'check' : 'add'}</span>
                    {inCollection ? 'Đã thêm' : 'Thêm'}
                  </button>
                </div>
              )
            })}
          </div>
        </div>
        <div className="app-modal-footer">
          <button className="app-btn secondary" onClick={onClose}>Xong</button>
        </div>
      </div>
    </div>
  )
}
