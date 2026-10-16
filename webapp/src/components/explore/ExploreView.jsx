import React, { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { compactNumber } from '../../i18n/format.js'
import { useVideos } from '../../hooks/useVideos.js'
import { thumbnailUrl } from '../../utils/videoModel.js'
import WatchedBadge from '../video/WatchedBadge.jsx'

const CATEGORIES = [
  { key: 'all',     labelKey: 'catAll',    icon: 'whatshot',                 gradient: 'linear-gradient(135deg,#ff8d89 0%,#c10023 100%)' },
  { key: 'funny',   labelKey: 'catFunny',  icon: 'sentiment_very_satisfied', gradient: 'linear-gradient(135deg,#00eefc 0%,#006970 100%)' },
  { key: 'regular', labelKey: 'catDrama',  icon: 'movie',                    gradient: 'linear-gradient(135deg,#cb9cff 0%,#460e7a 100%)' },
]

const SORT_OPTIONS = [
  { key: 'popular', labelKey: 'sortPopular' },
  { key: 'views',   labelKey: 'sortViews' },
  { key: 'recent',  labelKey: 'sortRecent' },
]

function getThumbnail(video) {
  if (!video) return ''
  return thumbnailUrl(video.fileId) || video.poster || ''
}

const formatViews = n => compactNumber(n || 0)

function ReelTile({ video, onClick }) {
  const { t } = useTranslation('feed')
  const thumb = getThumbnail(video)
  const title = video.title || t('untitled')
  return (
    <div
      className="explore-reel"
      onClick={onClick}
      role="button"
      tabIndex={0}
      title={title}
      aria-label={t('tileAria', { title, views: formatViews(video.viewCount) })}
      onKeyDown={e => e.key === 'Enter' && onClick?.()}
    >
      {thumb
        ? <img src={thumb} alt="" className="explore-reel-img" loading="lazy" />
        : <div className="explore-reel-fallback"><span className="material-symbols-outlined" style={{ fontSize: 36 }}>movie</span></div>
      }
      <div className="explore-reel-scrim" />
      <WatchedBadge videoId={video.id} />
      <span className="explore-reel-title above-views">{title}</span>
      <span className="explore-reel-views">
        <span className="material-symbols-outlined" style={{ fontSize: 18, fontVariationSettings: "'FILL' 1" }}>visibility</span>
        {formatViews(video.viewCount)}
      </span>
    </div>
  )
}

export default function ExploreView({ onNavigateToVideo }) {
  const { t } = useTranslation('feed')
  const { data: allVideos = [], isLoading } = useVideos(null)
  const [activeCategory, setActiveCategory] = useState('all')
  const [sortBy, setSortBy] = useState('popular')

  const filteredVideos = useMemo(() => {
    let list = activeCategory === 'all'
      ? allVideos
      : allVideos.filter(v => v.category === activeCategory)
    if (sortBy === 'recent') return [...list].sort((a, b) => b.id - a.id)
    if (sortBy === 'views')  return [...list].sort((a, b) => b.viewCount - a.viewCount)
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
  const reelVideos  = filteredVideos.slice(1)
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
                {t('trendingNow')}
              </span>
              <h1 className="explore-hero-title">{heroVideo.title || t('featuredVideo')}</h1>
              {heroVideo.desc && (
                <p className="explore-hero-desc">{heroVideo.desc}</p>
              )}
              <div className="explore-hero-actions">
                <button
                  className="explore-btn-primary"
                  onClick={e => { e.stopPropagation(); navigateTo(heroVideo) }}
                >
                  <span className="material-symbols-outlined" style={{ fontVariationSettings: "'FILL' 1", fontSize: 20 }}>play_arrow</span>
                  {t('watchNow')}
                </button>
                <button
                  className="explore-btn-secondary"
                  onClick={e => e.stopPropagation()}
                >
                  <span className="material-symbols-outlined" style={{ fontSize: 20 }}>add</span>
                  {t('watchlist')}
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
          <h2 className="explore-section-title">{t('categories')}</h2>
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
                <span className="explore-cat-label">{t(cat.labelKey)}</span>
              </div>
            </button>
          ))}
        </div>
      </section>

      {/* ── Recommended Grid ── */}
      <section className="explore-section">
        <div className="explore-section-header">
          <h2 className="explore-section-title explore-section-title-xl">{t('recommended').toLocaleUpperCase()}</h2>
        </div>

        {reelVideos.length === 0 ? (
          <p className="explore-empty-grid">{t('noVideosInCategory')}</p>
        ) : (
          <div className="explore-reels-grid">
            {reelVideos.map(v => (
              <ReelTile key={v.id} video={v} onClick={() => navigateTo(v)} />
            ))}
          </div>
        )}
      </section>

      {/* ── Floating Filter Bar ── */}
      <div className="explore-filter-bar">
        <button className="explore-filter-refine">
          <span className="material-symbols-outlined" style={{ fontSize: 18 }}>filter_list</span>
          {t('refine')}
        </button>
        <div className="explore-filter-divider" />
        <div className="explore-filter-opts">
          {SORT_OPTIONS.map(opt => (
            <button
              key={opt.key}
              className={`explore-filter-opt${sortBy === opt.key ? ' active' : ''}`}
              onClick={() => setSortBy(opt.key)}
            >
              {t(opt.labelKey)}
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
