import React, { useEffect, useMemo, useState } from 'react'

export default function SearchModal({ show, videos = [], currentIndex = 0, onSelect, onClose }) {
  const [searchTerm, setSearchTerm] = useState('')

  useEffect(() => {
    if (!show) setSearchTerm('')
  }, [show])

  const filteredVideos = useMemo(() => {
    const term = searchTerm.trim().toLowerCase()
    if (!term) return videos
    return videos.filter(v => {
      const haystack = [v?.title, v?.desc, v?.userShared, v?.category]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
      return haystack.includes(term)
    })
  }, [videos, searchTerm])

  if (!show) return null

  return (
    <div className="mobile-search-modal" onClick={() => onClose?.()}>
      <div className="mobile-search-modal-sheet" onClick={e => e.stopPropagation()}>
        <div className="mobile-search-modal-header">
          <div className="mobile-search-modal-title">Search Videos</div>
          <button className="icon-btn" onClick={() => onClose?.()} title="Close">
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>close</span>
          </button>
        </div>
        <div className="mobile-search-modal-input-wrap">
          <span className="material-symbols-outlined" style={{ fontSize: 18 }}>search</span>
          <input
            autoFocus
            className="mobile-search-modal-input"
            value={searchTerm}
            onChange={e => setSearchTerm(e.target.value)}
            placeholder="Search in video list"
          />
        </div>
        <div className="mobile-search-modal-list">
          {filteredVideos.map(item => {
            const itemIndex = videos.findIndex(v => v.id === item.id)
            const thumbnail = item?.fileId
              ? `https://images.canh-labs.com/${item.fileId}.jpg`
              : item?.poster || ''
            return (
              <button
                key={item.id}
                className="mobile-search-modal-item"
                onClick={() => onSelect?.(itemIndex)}
              >
                <div className="mobile-search-modal-thumb-wrap">
                  {thumbnail ? (
                    <img src={thumbnail} alt={item.title || 'Video thumbnail'} className="mobile-search-modal-thumb" />
                  ) : (
                    <div className="mobile-search-modal-thumb-fallback">
                      <span className="material-symbols-outlined" style={{ fontSize: 20 }}>movie</span>
                    </div>
                  )}
                </div>
                <div className="mobile-search-modal-item-text">
                  <div className="mobile-search-modal-item-title">{item.title || 'Untitled'}</div>
                  <div className="mobile-search-modal-item-sub">
                    @{item?.userShared?.split('@')[0] || 'anonymous'} • {item?.category || 'video'}
                  </div>
                </div>
                {itemIndex === currentIndex && (
                  <span className="material-symbols-outlined" style={{ fontSize: 18, color: 'var(--accent-cyan)' }}>
                    play_circle
                  </span>
                )}
              </button>
            )
          })}
          {filteredVideos.length === 0 && (
            <div className="mobile-search-modal-empty">No videos found</div>
          )}
        </div>
      </div>
    </div>
  )
}
