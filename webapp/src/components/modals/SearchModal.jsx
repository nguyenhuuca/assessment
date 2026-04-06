import React, { useEffect, useMemo, useState } from 'react'

export default function SearchModal({ show, videos = [], currentIndex = 0, onSelect, onClose }) {
  const [searchTerm, setSearchTerm] = useState('')

  useEffect(() => {
    if (!show) setSearchTerm('')
  }, [show])

  function normalizeForSearch(value) {
    return (value || '')
      .normalize('NFKD')
      .replace(/\p{M}/gu, '')
      .replace(/đ/g, 'd')
      .replace(/Đ/g, 'D')
      .toLocaleLowerCase()
  }

  // Lightweight fuzzy score: prioritize exact phrase matches, then word hits,
  // then in-order character subsequence matches for typo-tolerant search.
  function scoreFuzzy(text, term) {
    if (!text || !term) return 0
    const haystack = normalizeForSearch(text)
    const needle = normalizeForSearch(term)
    if (haystack.includes(needle)) return needle.length * 4

    const words = needle.split(/\s+/).filter(Boolean)
    let score = 0
    for (const w of words) {
      if (haystack.includes(w)) score += w.length * 2
    }

    // simple subsequence bonus
    let hi = 0
    let matched = 0
    for (let i = 0; i < needle.length && hi < haystack.length; i++) {
      const ch = needle[i]
      const foundIdx = haystack.indexOf(ch, hi)
      if (foundIdx === -1) break
      matched++
      hi = foundIdx + 1
    }
    score += matched
    return score
  }

  const filteredVideos = useMemo(() => {
    const term = searchTerm.trim()
    if (!term) return videos
    return videos
      .map(v => {
        const base = [v?.title, v?.desc, v?.userShared, v?.category]
          .filter(Boolean)
          .join(' ')
        const s = scoreFuzzy(base, term)
        return { v, s }
      })
      .filter(x => x.s > 0)
      .sort((a, b) => b.s - a.s)
      .map(x => x.v)
  }, [videos, searchTerm])

  if (!show) return null

  return (
    <div className="mobile-search-modal" onClick={() => onClose?.()}>
      <div className="mobile-search-modal-sheet" onClick={e => e.stopPropagation()}>
        <div className="mobile-search-modal-header">
          <div className="mobile-search-modal-input-wrap mobile-search-modal-input-wrap-header">
            <span className="material-symbols-outlined" style={{ fontSize: 18 }}>search</span>
            <input
              className="mobile-search-modal-input"
              value={searchTerm}
              onChange={e => setSearchTerm(e.target.value)}
              placeholder="Search in video list"
            />
          </div>
          <button className="icon-btn" onClick={() => onClose?.()} title="Close">
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>close</span>
          </button>
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
