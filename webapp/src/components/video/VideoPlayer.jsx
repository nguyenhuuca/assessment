import React, { useEffect, useRef, useState } from 'react'
import { buildStreamUrl } from '../../utils/videoModel.js'

export default function VideoPlayer({ video, onEnded, active, muted = true, onMutedChange }) {
  const videoRef = useRef(null)
  const trackRef = useRef(null)
  const [loading,      setLoading]      = useState(true)
  const [paused,       setPaused]       = useState(false)
  const [progress,     setProgress]     = useState(0)
  const [descExpanded, setDescExpanded] = useState(false)

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
    setDescExpanded(false)
    setProgress(0)
    setPaused(false)
    setLoading(true)
  }, [video?.id])

  function handleLoadStart() { setLoading(true) }
  function handleCanPlay()   { setLoading(false) }
  function handleWaiting()   { setLoading(true) }
  function handlePlaying()   { setLoading(false); setPaused(false) }
  function handlePause()     { setPaused(true) }
  function handleTimeUpdate() {
    const v = videoRef.current
    if (v?.duration) setProgress((v.currentTime / v.duration) * 100)
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

  const poster = video?.fileId
    ? `https://images.canh-labs.com/${video.fileId}.jpg`
    : undefined

  const handle = video?.userShared?.split('@')[0] || 'anonymous'

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
        src={buildStreamUrl(video?.src)}
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
        onTimeUpdate={handleTimeUpdate}
        onEnded={onEnded}
      />

      {/* ── Mute / unmute button ── */}
      <button
        className="video-mute-btn"
        data-no-toggle
        onClick={toggleMute}
        title={muted ? 'Unmute' : 'Mute'}
        style={{
          position: 'absolute', top: 12, right: 12, zIndex: 20,
          background: 'rgba(0,0,0,0.48)',
          backdropFilter: 'blur(8px)',
          border: 'none',
          borderRadius: '50%',
          width: 40, height: 40,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          color: 'white',
          cursor: 'pointer',
          transition: 'background 0.15s ease, transform 0.12s ease',
        }}
        onMouseEnter={e => e.currentTarget.style.background = 'rgba(0,0,0,0.72)'}
        onMouseLeave={e => e.currentTarget.style.background = 'rgba(0,0,0,0.48)'}
      >
        <span className="material-symbols-outlined" style={{ fontSize: 22, fontVariationSettings: "'FILL' 1, 'wght' 400, 'GRAD' 0, 'opsz' 24" }}>
          {muted ? 'volume_off' : 'volume_up'}
        </span>
      </button>

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
              {video?.category || 'video'}
            </div>
          </div>
        </div>

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
              <span style={{ color: 'var(--accent-cyan)', fontWeight: 600 }}> …more</span>
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
