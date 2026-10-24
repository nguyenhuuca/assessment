import React, { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { buildStreamUrl, thumbnailUrl } from '../../utils/videoModel.js'
import { useQueryClient } from '@tanstack/react-query'
import { viewsApi } from '../../api/views.js'
import { watchHistoryApi } from '../../api/watchHistory.js'
import { useAuth } from '../../hooks/useAuth.js'
import { useHlsSource } from '../../hooks/useHlsSource.js'
import WatchedBadge from './WatchedBadge.jsx'

/** Seconds actually played (not seeked) while visible before a view counts; server dedupes per viewer per video for 30 min. */
const VIEW_THRESHOLD_SEC = 3
/** timeupdate fires every ~250 ms; a bigger jump between two updates is a seek, not playback. */
const MAX_PLAY_STEP_SEC = 1.5
/** Resume point is reported at most this often while playing (pause / unmount are always sent). */
const PROGRESS_MIN_INTERVAL_MS = 10_000
const PROGRESS_TICK_MS = 30_000
/** startAt is only honoured when it is worth resuming and not at the very end. */
const MIN_RESUME_SEC = 5
const END_MARGIN_SEC = 10

export default function VideoPlayer({ video, onEnded, active, muted = true, onMutedChange, showMuteButton = true, startAt }) {
  const { t } = useTranslation('feed')
  const videoRef = useRef(null)
  const trackRef = useRef(null)
  const [loading,      setLoading]      = useState(true)
  const [paused,       setPaused]       = useState(false)
  const [progress,     setProgress]     = useState(0)
  const [descExpanded, setDescExpanded] = useState(false)
  const viewSentRef = useRef(false)
  const watchedRef  = useRef(0)
  const lastTimeRef = useRef(0)
  const durationRef = useRef(0)
  const playingRef = useRef(false)
  const lastProgressAtRef = useRef(0)
  const startAppliedRef = useRef(false)
  const queryClient = useQueryClient()
  const { isLoggedIn } = useAuth()
  const loggedInRef = useRef(isLoggedIn)
  loggedInRef.current = isLoggedIn

  /** Best-effort resume point. Only after the view counted (so history has the row), logged-in users only. */
  function reportProgress(videoId, force, fromRefs = false) {
    if (!loggedInRef.current || !viewSentRef.current || videoId == null) return
    // On switch/unmount the <video> may already point at the next source, so use the last tracked values
    const v = fromRefs ? null : videoRef.current
    const pos = v ? v.currentTime : lastTimeRef.current
    const dur = v ? v.duration : durationRef.current
    if (!Number.isFinite(pos) || !Number.isFinite(dur) || dur <= 0) return
    const now = Date.now()
    if (!force && now - lastProgressAtRef.current < PROGRESS_MIN_INTERVAL_MS) return
    lastProgressAtRef.current = now
    watchHistoryApi.progress(videoId, Math.round(pos), Math.round(dur))?.catch?.(() => {})
  }
  const reportRef = useRef(reportProgress)
  reportRef.current = reportProgress

  // Play / pause driven by `active` prop
  useEffect(() => {
    const v = videoRef.current
    if (!v) return
    if (active) { v.play().catch(() => {}) } else { v.pause() }
  }, [active])

  // Sync muted prop → video element
  useEffect(() => {
    const v = videoRef.current
    if (v) v.muted = muted
  }, [muted])

  // Reset overlay state when video changes
  useEffect(() => {
    const id = video?.id
    setDescExpanded(false)
    setProgress(0)
    setPaused(false)
    setLoading(true)
    viewSentRef.current = false
    watchedRef.current = 0
    lastTimeRef.current = 0
    durationRef.current = 0
    playingRef.current = false
    lastProgressAtRef.current = 0
    startAppliedRef.current = false
    // Switching video or unmounting: flush the resume point of the video that was playing
    return () => reportRef.current(id, true, true)
  }, [video?.id])

  // Periodic resume point while playing, and when the tab is hidden / the slide becomes inactive
  useEffect(() => {
    const id = video?.id
    const timer = setInterval(() => { if (playingRef.current) reportRef.current(id, false) }, PROGRESS_TICK_MS)
    const onVisibility = () => { if (document.visibilityState === 'hidden') reportRef.current(id, false) }
    document.addEventListener('visibilitychange', onVisibility)
    return () => {
      clearInterval(timer)
      document.removeEventListener('visibilitychange', onVisibility)
    }
  }, [video?.id])

  const prevActiveRef = useRef(active)
  useEffect(() => {
    if (prevActiveRef.current && !active) reportRef.current(video?.id, false)
    prevActiveRef.current = active
  }, [active, video?.id])

  function handleLoadStart() { setLoading(true) }
  function handleCanPlay()   { setLoading(false) }
  function handleWaiting()   { setLoading(true) }
  function handlePlaying()   { setLoading(false); setPaused(false); playingRef.current = true }
  function handlePause()     { setPaused(true); playingRef.current = false; reportProgress(video?.id, true) }
  function handleEnded()     { playingRef.current = false; reportProgress(video?.id, true); onEnded?.() }
  function handleLoadedMetadata() {
    const v = videoRef.current
    if (!v || startAppliedRef.current) return
    startAppliedRef.current = true
    const dur = v.duration
    if (startAt > MIN_RESUME_SEC && Number.isFinite(dur) && startAt < dur - END_MARGIN_SEC) {
      v.currentTime = startAt
      lastTimeRef.current = startAt
    }
  }
  function handleTimeUpdate() {
    const v = videoRef.current
    if (v && Number.isFinite(v.duration)) durationRef.current = v.duration
    if (v?.duration) setProgress((v.currentTime / v.duration) * 100)
    trackView(v)
  }

  function trackView(v) {
    if (!v) return
    const step = v.currentTime - lastTimeRef.current
    lastTimeRef.current = v.currentTime
    // Only real playback counts: no seeks (big or backward jumps), no background tab, no inactive slide
    const visible = typeof document === 'undefined' || document.visibilityState !== 'hidden'
    if (step > 0 && step <= MAX_PLAY_STEP_SEC && active && visible) {
      watchedRef.current += step
    }
    if (!viewSentRef.current && video?.id != null && watchedRef.current >= VIEW_THRESHOLD_SEC) {
      viewSentRef.current = true
      const id = video.id
      // best effort: a lost view must never disturb playback
      viewsApi.record(id)
        .then(() => { if (loggedInRef.current) queryClient.invalidateQueries({ queryKey: ['watchHistory'] }) })
        .catch(() => {})
    }
  }

  // Click body of player to toggle play/pause, but skip interactive children
  function handlePlayerClick(e) {
    if (e.target.closest('[data-no-toggle]')) return
    const v = videoRef.current
    if (!v) return
    if (v.paused) { v.play() } else { v.pause() }
  }

  function toggleMute(e) {
    e.stopPropagation()
    onMutedChange?.(!muted)
  }

  function handleTrackClick(e) {
    e.stopPropagation()
    const v = videoRef.current
    const track = trackRef.current
    if (!v?.duration || !track) return
    const rect = track.getBoundingClientRect()
    const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
    v.currentTime = ratio * v.duration
    setProgress(ratio * 100)
  }

  const src = useHlsSource(videoRef, { hlsUrl: video?.hlsUrl, rangeUrl: buildStreamUrl(video?.src) })

  const poster = thumbnailUrl(video?.fileId) || undefined

  const handle = video?.userShared?.split('@')[0] || t('anonymous')

  return (
    <div
      onClick={handlePlayerClick}
      style={{
        position: 'relative',
        width: '100%', height: '100%',
        background: '#000',
        overflow: 'hidden',
        borderRadius: 16,
        cursor: 'pointer',
      }}
    >
      {/* ── Video element ── */}
      <video
        ref={videoRef}
        src={src}
        poster={poster}
        autoPlay={active}
        playsInline
        preload="auto"
        muted={muted}
        style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
        onLoadStart={handleLoadStart}
        onCanPlay={handleCanPlay}
        onWaiting={handleWaiting}
        onPlaying={handlePlaying}
        onPause={handlePause}
        onLoadedMetadata={handleLoadedMetadata}
        onTimeUpdate={handleTimeUpdate}
        onEnded={handleEnded}
      />

      {/* ── Mute / unmute (optional: parent renders stable control outside slide layer) ── */}
      {showMuteButton && (
        <button
          type="button"
          className="video-mute-btn"
          data-no-toggle
          onClick={toggleMute}
          title={muted ? t('unmute') : t('mute')}
        >
          <span className="material-symbols-outlined" style={{ fontSize: 22, fontVariationSettings: "'FILL' 1, 'wght' 400, 'GRAD' 0, 'opsz' 24" }}>
            {muted ? 'volume_off' : 'volume_up'}
          </span>
        </button>
      )}

      {/* ── Loading spinner ── */}
      {loading && (
        <div style={{
          position: 'absolute', inset: 0, zIndex: 10,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          pointerEvents: 'none',
        }}>
          <div className="vid-spinner" />
        </div>
      )}

      {/* ── Centre play icon when paused ── */}
      {paused && !loading && (
        <div style={{
          position: 'absolute', inset: 0, zIndex: 10,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          pointerEvents: 'none',
        }}>
          <div style={{
            width: 72, height: 72, borderRadius: '50%',
            background: 'rgba(0,0,0,0.52)',
            backdropFilter: 'blur(10px)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            animation: 'fadeIn 0.15s ease',
          }}>
            <span className="material-symbols-outlined" style={{
              fontSize: 40, color: 'white',
              fontVariationSettings: "'FILL' 1, 'wght' 400, 'GRAD' 0, 'opsz' 48",
            }}>
              play_arrow
            </span>
          </div>
        </div>
      )}

      {/* ── Bottom scrim + metadata ── */}
      <div className="scrim" style={{
        position: 'absolute', bottom: 0, left: 0, right: 0,
        padding: '80px 18px 0', zIndex: 10,
        pointerEvents: 'none',
      }}>
        {/* Creator row */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 10, pointerEvents: 'auto' }}>
          <div style={{
            width: 40, height: 40, borderRadius: '50%',
            border: '2px solid var(--primary)',
            background: 'var(--bg-high)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 15, fontWeight: 700,
            flexShrink: 0,
          }}>
            {handle[0]?.toUpperCase()}
          </div>
          <div>
            <div style={{ fontSize: 14, fontWeight: 700, lineHeight: 1.2 }}>@{handle}</div>
            <div style={{
              fontSize: 10, color: 'var(--accent-cyan)',
              fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.1em',
            }}>
              {video?.category || t('videoFallback')}
            </div>
          </div>
        </div>

        <WatchedBadge videoId={video?.id} />

        {/* Title */}
        <p style={{
          fontSize: 15, fontWeight: 600, lineHeight: 1.4,
          marginBottom: 6, pointerEvents: 'auto',
        }}>
          {video?.title}
        </p>

        {/* Description (tap to expand) */}
        {video?.desc && (
          <p
            data-no-toggle
            onClick={e => { e.stopPropagation(); setDescExpanded(x => !x) }}
            style={{
              fontSize: 12, color: 'rgba(255,255,255,0.72)', lineHeight: 1.5,
              display: '-webkit-box',
              WebkitLineClamp: descExpanded ? 'unset' : 2,
              WebkitBoxOrient: 'vertical',
              overflow: descExpanded ? 'visible' : 'hidden',
              cursor: 'pointer',
              pointerEvents: 'auto',
              marginBottom: 10,
            }}
          >
            {video.desc}
            {!descExpanded && video.desc.length > 60 && (
              <span style={{ color: 'var(--accent-cyan)', fontWeight: 600 }}> … {t('more')}</span>
            )}
          </p>
        )}

      </div>

      {/* ── Progress bar — tách riêng, absolute ở đáy, không bị scrim/overflow che ── */}
      <div
        ref={trackRef}
        data-no-toggle
        onClick={handleTrackClick}
        style={{
          position: 'absolute', bottom: 0, left: 0, right: 0,
          height: 20, zIndex: 30,
          cursor: 'pointer',
          pointerEvents: 'auto',
          display: 'flex', alignItems: 'flex-end',
        }}
      >
        {/* track nền */}
        <div style={{ width: '100%', height: 4, background: 'rgba(255,255,255,0.2)', borderRadius: 2, overflow: 'hidden' }}>
          {/* fill */}
          <div style={{
            height: '100%',
            width: `${progress}%`,
            background: 'var(--accent-cyan)',
            boxShadow: '0 0 8px rgba(0,238,252,0.6)',
            borderRadius: 2,
            transition: 'width 0.25s linear',
          }} />
        </div>
      </div>
    </div>
  )
}
