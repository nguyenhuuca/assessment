import React, { useState, useMemo } from 'react'
import { useVideos } from '../../hooks/useVideos.js'

const CATEGORIES = [
  { key: 'all',     label: 'All',    icon: 'whatshot',                 gradient: 'linear-gradient(135deg,#ff8d89 0%,#c10023 100%)' },
  { key: 'funny',   label: 'Funny',  icon: 'sentiment_very_satisfied', gradient: 'linear-gradient(135deg,#00eefc 0%,#006970 100%)' },
  { key: 'regular', label: 'Drama',  icon: 'movie',                    gradient: 'linear-gradient(135deg,#cb9cff 0%,#460e7a 100%)' },
]

const SORT_OPTIONS = [
  { key: 'popular', label: 'Most Liked' },
  { key: 'recent',  label: 'Recently Added' },
]

function getThumbnail(video) {
  if (!video) return ''
  return video.fileId
    ? `https://images.canh-labs.com/${video.fileId}.jpg`
    : video.poster || ''
}

function ThumbFallback() {
  return (
    <div className="explore-thumb-fallback">
      <span className="material-symbols-outlined" style={{ fontSize: 36 }}>movie</span>
    </div>
  )
}

function VideoCard({ video, onClick, size = 'bottom' }) {
  const thumb = getThumbnail(video)
  return (
    <div
      className={`explore-card explore-card-${size}`}
      onClick={onClick}
      role="button"
      tabIndex={0}
      onKeyDown={e => e.key === 'Enter' && onClick?.()}
    >
      <div className="explore-card-thumb">
        {thumb
          ? <img src={thumb} alt={video.title || 'Video'} className="explore-card-img" loading="lazy" />
          : <ThumbFallback />
        }
        <div className="explore-card-play-overlay">
          <div className="explore-card-play-btn">
            <span className="material-symbols-outlined" style={{ fontSize: 32, fontVariationSettings: "'FILL' 1" }}>play_arrow</span>
          </div>
        </div>
      </div>
      <div className="explore-card-info">
        <h4 className="explore-card-title">{video.title || 'Untitled'}</h4>
        <p className="explore-card-meta">
          @{video.userShared?.split('@')[0] || 'anonymous'}
          {video.upvotes > 0 && <> · <span style={{ color: 'var(--accent-cyan)' }}>{video.upvotes.toLocaleString()} likes</span></>}
        </p>
      </div>
    </div>
  )
}

export default function ExploreView({ onNavigateToVideo }) {
  const { data: allVideos = [], isLoading } = useVideos(null)
  const [activeCategory, setActiveCategory] = useState('all')
  const [sortBy, setSortBy] = useState('popular')

  const filteredVideos = useMemo(() => {
    let list = activeCategory === 'all'
      ? allVideos
      : allVideos.filter(v => v.category === activeCategory)
    if (sortBy === 'recent') return [...list].sort((a, b) => b.id - a.id)
    return [...list].sort((a, b) => b.upvotes - a.upvotes)
  }, [allVideos, activeCategory, sortBy])

  function navigateTo(video) {
    if (!video) return
    const idx = allVideos.findIndex(v => v.id === video.id)
    onNavigateToVideo?.(idx >= 0 ? idx : 0)
  }

  if (isLoading) {
    return (
      <div className="explore-loading">
        <div className="vid-spinner" />
      </div>
    )
  }

  const heroVideo   = filteredVideos[0] || null
  const gridVideos  = filteredVideos.slice(1)
  const largeCard   = gridVideos[0]
  const stackCards  = gridVideos.slice(1, 3)
  const bottomCards = gridVideos.slice(3, 6)
  const moreCards   = gridVideos.slice(6)
  const heroThumb   = getThumbnail(heroVideo)

  return (
    <div className="explore-root">

      {/* ── Hero Banner ── */}
      {heroVideo && (
        <section className="explore-hero" onClick={() => navigateTo(heroVideo)}>
          {heroThumb
            ? <img src={heroThumb} alt={heroVideo.title} className="explore-hero-img" />
            : <div className="explore-hero-img-fallback" />
          }
          <div className="explore-hero-scrim">
            <div className="explore-hero-content">
              <span className="explore-hero-badge">
                <span className="explore-hero-badge-dot" />
                Trending Now
              </span>
              <h1 className="explore-hero-title">{heroVideo.title || 'Featured Video'}</h1>
              {heroVideo.desc && (
                <p className="explore-hero-desc">{heroVideo.desc}</p>
              )}
              <div className="explore-hero-actions">
                <button
                  className="explore-btn-primary"
                  onClick={e => { e.stopPropagation(); navigateTo(heroVideo) }}
                >
                  <span className="material-symbols-outlined" style={{ fontVariationSettings: "'FILL' 1", fontSize: 20 }}>play_arrow</span>
                  Watch Now
                </button>
                <button
                  className="explore-btn-secondary"
                  onClick={e => e.stopPropagation()}
                >
                  <span className="material-symbols-outlined" style={{ fontSize: 20 }}>add</span>
                  Watchlist
                </button>
              </div>
              <div className="explore-hero-meta">
                {heroVideo.upvotes > 0 && (
                  <span className="explore-hero-stat">
                    <span className="material-symbols-outlined" style={{ fontSize: 14, fontVariationSettings: "'FILL' 1", color: 'var(--primary)' }}>favorite</span>
                    {heroVideo.upvotes.toLocaleString()}
                  </span>
                )}
                <span className="explore-hero-cat">{heroVideo.category}</span>
                {heroVideo.userShared && (
                  <span>@{heroVideo.userShared.split('@')[0]}</span>
                )}
              </div>
            </div>
          </div>
        </section>
      )}

      {/* ── Categories ── */}
      <section className="explore-section">
        <div className="explore-section-header">
          <h2 className="explore-section-title">Categories</h2>
        </div>
        <div className="explore-cats-scroll">
          {CATEGORIES.map(cat => (
            <button
              key={cat.key}
              className={`explore-cat-card${activeCategory === cat.key ? ' active' : ''}`}
              onClick={() => setActiveCategory(cat.key)}
            >
              <div className="explore-cat-bg" style={{ background: cat.gradient }} />
              <div className="explore-cat-overlay">
                <span className="material-symbols-outlined" style={{ fontSize: 26 }}>{cat.icon}</span>
                <span className="explore-cat-label">{cat.label}</span>
              </div>
            </button>
          ))}
        </div>
      </section>

      {/* ── Recommended Grid ── */}
      <section className="explore-section">
        <div className="explore-section-header">
          <h2 className="explore-section-title explore-section-title-xl">RECOMMENDED FOR YOU</h2>
        </div>

        {filteredVideos.length <= 1 ? (
          <p className="explore-empty-grid">No videos in this category</p>
        ) : (
          <div className="explore-bento">
            {/* Large feature card */}
            {largeCard && (
              <div
                className="explore-card explore-card-large"
                onClick={() => navigateTo(largeCard)}
                role="button"
                tabIndex={0}
                onKeyDown={e => e.key === 'Enter' && navigateTo(largeCard)}
              >
                <div className="explore-card-thumb">
                  {getThumbnail(largeCard)
                    ? <img src={getThumbnail(largeCard)} alt={largeCard.title || 'Video'} className="explore-card-img" loading="lazy" />
                    : <ThumbFallback />
                  }
                  <div className="explore-card-play-overlay">
                    <div className="explore-card-play-btn">
                      <span className="material-symbols-outlined" style={{ fontSize: 40, fontVariationSettings: "'FILL' 1" }}>play_arrow</span>
                    </div>
                  </div>
                </div>
                <div className="explore-card-info explore-card-info-large">
                  <h3 className="explore-card-title explore-card-title-lg">{largeCard.title || 'Untitled'}</h3>
                  <div className="explore-card-meta">
                    <span>@{largeCard.userShared?.split('@')[0] || 'anonymous'}</span>
                    {largeCard.upvotes > 0 && (
                      <><span className="explore-meta-dot" /><span style={{ color: 'var(--accent-cyan)', fontWeight: 700 }}>{largeCard.upvotes.toLocaleString()} likes</span></>
                    )}
                    <span className="explore-meta-dot" />
                    <span>{largeCard.category}</span>
                  </div>
                </div>
              </div>
            )}

            {/* Stacked small cards */}
            {stackCards.length > 0 && (
              <div className="explore-bento-stack">
                {stackCards.map(v => (
                  <VideoCard key={v.id} video={v} size="small" onClick={() => navigateTo(v)} />
                ))}
              </div>
            )}

            {/* Bottom row */}
            {bottomCards.map(v => (
              <VideoCard key={v.id} video={v} size="bottom" onClick={() => navigateTo(v)} />
            ))}
          </div>
        )}

        {/* Extra cards */}
        {moreCards.length > 0 && (
          <div className="explore-more-grid">
            {moreCards.map(v => (
              <VideoCard key={v.id} video={v} size="bottom" onClick={() => navigateTo(v)} />
            ))}
          </div>
        )}
      </section>

      {/* ── Floating Filter Bar ── */}
      <div className="explore-filter-bar">
        <button className="explore-filter-refine">
          <span className="material-symbols-outlined" style={{ fontSize: 18 }}>filter_list</span>
          Refine
        </button>
        <div className="explore-filter-divider" />
        <div className="explore-filter-opts">
          {SORT_OPTIONS.map(opt => (
            <button
              key={opt.key}
              className={`explore-filter-opt${sortBy === opt.key ? ' active' : ''}`}
              onClick={() => setSortBy(opt.key)}
            >
              {opt.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
