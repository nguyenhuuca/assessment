import React, { useCallback, useEffect, useState } from 'react'
import { useSwipeable } from 'react-swipeable'
import VideoPlayer from './VideoPlayer.jsx'
import VoteButtons from './VoteButtons.jsx'
import { buildStreamUrl } from '../../utils/videoModel.js'
import SearchModal from '../modals/SearchModal.jsx'

export default function VideoSwiper({ videos = [], initialIndex = 0, mobileSearchOpen = false, onCloseMobileSearch, onShowComments, onDeleteVideo, currentUser }) {
  const [index, setIndex] = useState(() => Math.min(initialIndex, Math.max(0, videos.length - 1)))
  const [direction, setDirection] = useState('next')
  const [muted, setMuted] = useState(true)
  const total = videos.length

  const next = useCallback(() => {
    setDirection('next')
    setIndex(i => Math.min(i + 1, total - 1))
  }, [total])

  const prev = useCallback(() => {
    setDirection('prev')
    setIndex(i => Math.max(i - 1, 0))
  }, [])

  // Keyboard navigation
  useEffect(() => {
    function onKey(e) {
      if (e.key === 'ArrowDown' || e.key === 'ArrowRight') next()
      if (e.key === 'ArrowUp'   || e.key === 'ArrowLeft')  prev()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [next, prev])


 // Scroll wheel navigation
    useEffect(() => {
        const isScrolling = { current: false }

        function onWheel(e) {
            if (e.target.closest('.mobile-search-modal')) return
            e.preventDefault()
            if (isScrolling.current) return
            isScrolling.current = true

            if (e.deltaY > 0) next()
            else prev()

            setTimeout(() => { isScrolling.current = false }, 800)
        }

        window.addEventListener('wheel', onWheel, { passive: false })
        return () => window.removeEventListener('wheel', onWheel)
    }, [next, prev])

  // Reset index when video list changes
  useEffect(() => { setIndex(0) }, [videos.length])

  const swipeHandlers = useSwipeable({
    onSwipedUp:    next,
    onSwipedDown:  prev,
    onSwipedLeft:  next,
    onSwipedRight: prev,
    preventScrollOnSwipe: true,
    trackMouse: false,
  })

  if (total === 0) {
    return (
      <div style={{
        display: 'flex', flexDirection: 'column',
        alignItems: 'center', gap: 12,
        color: 'var(--text-muted)',
        animation: 'fadeIn 0.3s ease',
      }}>
        <span className="material-symbols-outlined" style={{ fontSize: 64, opacity: 0.25 }}>
          video_library
        </span>
        <p style={{ fontSize: 14, fontWeight: 500 }}>No videos available</p>
      </div>
    )
  }

  const video     = videos[index]
  const canDelete = video?.isPrivate && currentUser?.email === video?.userShared

  return (
    <div className="video-swiper" style={{
      position: 'relative',
      display: 'flex',
      alignItems: 'flex-end',
      gap: 20,
      height: '100%',
      padding: '20px 0',
      animation: 'fadeIn 0.25s ease',
    }}>
      {/* ── 9:16 Video ──
          Inner `key={index}` remounts for slide animation. Mute sits outside that layer so the control
          does not unmount between videos — on mobile (full-bleed reel + overlays) that flicker was very noticeable. */}
      <div
        className="video-card"
        {...swipeHandlers}
        style={{
          position: 'relative',
          height: '100%',
          aspectRatio: '9 / 16',
          maxHeight: 'calc(100vh - 108px)',
          borderRadius: 16,
          overflow: 'hidden',
          boxShadow: '0 0 0 1px var(--border), 0 20px 60px rgba(0,0,0,0.7)',
          flexShrink: 0,
        }}
      >
        <div
          key={index}
          className={`video-card-inner slide-${direction}`}
          style={{
            position: 'absolute',
            inset: 0,
            borderRadius: 'inherit',
            overflow: 'hidden',
          }}
        >
          <VideoPlayer
            video={video}
            active
            onEnded={next}
            muted={muted}
            onMutedChange={setMuted}
            showMuteButton={false}
          />
        </div>
        <button
          type="button"
          className="video-mute-btn"
          data-no-toggle
          onClick={e => { e.stopPropagation(); setMuted(m => !m) }}
          title={muted ? 'Unmute' : 'Mute'}
        >
          <span className="material-symbols-outlined" style={{ fontSize: 22, fontVariationSettings: "'FILL' 1, 'wght' 400, 'GRAD' 0, 'opsz' 24" }}>
            {muted ? 'volume_off' : 'volume_up'}
          </span>
        </button>
      </div>

      {/* ── Right-side action column ── */}
      <div className="action-col" style={{
        display: 'flex', flexDirection: 'column',
        gap: 14, paddingBottom: 44, flexShrink: 0,
      }}>

        {/* Like / Dislike */}
        <VoteButtons video={video} />

        {/* Comments */}
        <button className="action-btn" onClick={() => onShowComments?.(video)} title="Comments">
          <span className="icon material-symbols-outlined">comment</span>
          <span className="label">Comment</span>
        </button>

        {/* Share (native Web Share API, no-op if unsupported) */}
        <button
          className="action-btn"
          title="Share"
          onClick={() => {
            if (navigator.share) {
              navigator.share({ title: video?.title, text: video?.desc, url: window.location.href }).catch(() => {})
            }
          }}
        >
          <span className="icon material-symbols-outlined">share</span>
          <span className="label">Share</span>
        </button>

        {/* Delete — only for private videos owned by current user */}
        {canDelete && (
          <button
            className="action-btn"
            style={{ color: '#ff6b6b' }}
            onClick={() => onDeleteVideo?.(video)}
            title="Delete"
          >
            <span className="icon material-symbols-outlined">delete</span>
            <span className="label">Delete</span>
          </button>
        )}

        {/* Navigate up / down — grouped together */}
        <div className="nav-arrows">
          <button className="action-btn" onClick={prev} disabled={index === 0} title="Previous">
            <span className="icon material-symbols-outlined">keyboard_arrow_up</span>
          </button>
          <button className="action-btn" onClick={next} disabled={index === total - 1} title="Next">
            <span className="icon material-symbols-outlined">keyboard_arrow_down</span>
          </button>
        </div>

        {/* Counter */}
        <div style={{
          textAlign: 'center',
          fontSize: 11, fontWeight: 700,
          color: 'var(--text-muted)', letterSpacing: '0.05em',
        }}>
          {index + 1}/{total}
        </div>
      </div>

      {/* ── Preload next 2 videos ── */}
      {[1, 2].map(offset => {
        const v = videos[index + offset]
        const src = v ? buildStreamUrl(v.src) : null
        return src ? (
          <video
            key={v.id}
            src={src}
            preload="auto"
            muted
            playsInline
            style={{ display: 'none' }}
          />
        ) : null
      })}

      <SearchModal
        show={mobileSearchOpen}
        videos={videos}
        currentIndex={index}
        onClose={onCloseMobileSearch}
        onSelect={itemIndex => {
          if (itemIndex >= 0) {
            setDirection(itemIndex >= index ? 'next' : 'prev')
            setIndex(itemIndex)
          }
          onCloseMobileSearch?.()
        }}
      />
    </div>
  )
}
