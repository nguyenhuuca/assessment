import React, { useEffect, useRef, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import {
  faPlay, faPause, faVolumeUp, faVolumeMute
} from '@fortawesome/free-solid-svg-icons'

export default function VideoPlayer({ video, onEnded, active }) {
  const videoRef = useRef(null)
  const [loading, setLoading] = useState(true)
  const [paused, setPaused] = useState(false)
  const [muted, setMuted] = useState(false)
  const [progress, setProgress] = useState(0)
  const [descExpanded, setDescExpanded] = useState(false)

  // Play/pause when active changes
  useEffect(() => {
    const v = videoRef.current
    if (!v) return
    if (active) {
      v.play().catch(() => {})
    } else {
      v.pause()
    }
  }, [active])

  function handleLoadStart() { setLoading(true) }
  function handleCanPlay() { setLoading(false) }
  function handleWaiting() { setLoading(true) }
  function handlePlaying() {
    setLoading(false)
    setPaused(false)
  }
  function handlePause() { setPaused(true) }
  function handleTimeUpdate() {
    const v = videoRef.current
    if (v && v.duration) setProgress((v.currentTime / v.duration) * 100)
  }

  function togglePlayPause() {
    const v = videoRef.current
    if (!v) return
    if (v.paused) { v.play() } else { v.pause() }
  }

  function toggleMute() {
    const v = videoRef.current
    if (!v) return
    v.muted = !v.muted
    setMuted(v.muted)
  }

  function handleProgressClick(e) {
    const v = videoRef.current
    if (!v || !v.duration) return
    const rect = e.currentTarget.getBoundingClientRect()
    const ratio = (e.clientX - rect.left) / rect.width
    v.currentTime = ratio * v.duration
  }

  const poster = video?.fileId ? `https://images.canh-labs.com/${video.fileId}.jpg` : undefined

  // HTML5 <video> cannot send custom headers, so append auth tokens as query params
  // so the streaming endpoint can authenticate the request
  function buildStreamUrl(src) {
    if (!src) return src
    try {
      const url = new URL(src)
      const jwt = localStorage.getItem('jwt')
      const guestToken = localStorage.getItem('guestToken')
      if (jwt) url.searchParams.set('token', jwt)
      if (guestToken) url.searchParams.set('guestToken', guestToken)
      return url.toString()
    } catch {
      return src
    }
  }

  return (
    <div className="video-player" style={{ position: 'relative', width: '100%', height: '100%', background: '#000', overflow: 'hidden' }}>
      <video
        ref={videoRef}
        src={buildStreamUrl(video?.src)}
        poster={poster}
        autoPlay={active}
        playsInline
        preload="auto"
        style={{ width: '100%', height: '100%', objectFit: 'contain' }}
        onLoadStart={handleLoadStart}
        onCanPlay={handleCanPlay}
        onWaiting={handleWaiting}
        onPlaying={handlePlaying}
        onPause={handlePause}
        onTimeUpdate={handleTimeUpdate}
        onEnded={onEnded}
        onClick={togglePlayPause}
      />

      {/* Loading spinner */}
      {loading && (
        <div style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%,-50%)', zIndex: 10 }}>
          <div className="spinner-border text-light" role="status" />
        </div>
      )}

      {/* Center play icon when paused */}
      {paused && !loading && (
        <div
          style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%,-50%)', zIndex: 10, opacity: 0.8, pointerEvents: 'none' }}
        >
          <FontAwesomeIcon icon={faPlay} size="3x" color="white" />
        </div>
      )}

      {/* Overlay controls */}
      <div style={{ position: 'absolute', top: 8, right: 8, display: 'flex', gap: 8, zIndex: 20 }}>
        <button className="overlay-button" onClick={togglePlayPause} style={btnStyle}>
          <FontAwesomeIcon icon={paused ? faPlay : faPause} color="white" />
        </button>
        <button className="overlay-button" onClick={toggleMute} style={btnStyle}>
          <FontAwesomeIcon icon={muted ? faVolumeMute : faVolumeUp} color="white" />
        </button>
      </div>

      {/* Description overlay */}
      <div style={{ position: 'absolute', bottom: 40, left: 0, right: 60, padding: '0 12px', zIndex: 10 }}>
        <h4 style={{ color: 'white', fontSize: '0.95rem', margin: 0, textShadow: '0 1px 3px rgba(0,0,0,0.8)' }}>
          {video?.title}
        </h4>
        {video?.desc && (
          <div>
            <p style={{
              color: 'rgba(255,255,255,0.9)', fontSize: '0.8rem', margin: '4px 0',
              display: '-webkit-box', WebkitLineClamp: descExpanded ? 'unset' : 2,
              WebkitBoxOrient: 'vertical', overflow: descExpanded ? 'visible' : 'hidden'
            }}>
              {video.desc}
            </p>
            {video.desc.length > 80 && (
              <button
                onClick={e => { e.stopPropagation(); setDescExpanded(x => !x) }}
                style={{ background: 'none', border: 'none', color: 'white', fontSize: '0.75rem', padding: 0, cursor: 'pointer' }}
              >
                {descExpanded ? 'See less' : 'See more'}
              </button>
            )}
          </div>
        )}
      </div>

      {/* Progress bar */}
      <div
        onClick={handleProgressClick}
        style={{ position: 'absolute', bottom: 0, left: 0, right: 0, height: 4, background: 'rgba(255,255,255,0.3)', cursor: 'pointer', zIndex: 20 }}
      >
        <div style={{ width: `${progress}%`, height: '100%', background: 'white', transition: 'width 0.1s linear' }} />
      </div>
    </div>
  )
}

const btnStyle = {
  background: 'rgba(0,0,0,0.5)',
  border: 'none',
  borderRadius: 4,
  padding: '4px 8px',
  cursor: 'pointer',
}
