import React, { useCallback, useEffect, useState } from 'react'
import { useSwipeable } from 'react-swipeable'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faChevronLeft, faChevronRight } from '@fortawesome/free-solid-svg-icons'
import VideoPlayer from './VideoPlayer.jsx'
import VoteButtons from './VoteButtons.jsx'

export default function VideoSwiper({ videos = [], onShowComments, onDeleteVideo, currentUser }) {
  const [index, setIndex] = useState(0)
  const [flash, setFlash] = useState(null) // 'left' | 'right' | null

  const total = videos.length

  const goTo = useCallback((newIndex) => {
    if (total === 0) return
    const clamped = Math.max(0, Math.min(newIndex, total - 1))
    setIndex(clamped)
  }, [total])

  const prev = useCallback(() => {
    setFlash('left')
    goTo(index - 1)
    setTimeout(() => setFlash(null), 300)
  }, [index, goTo])

  const next = useCallback(() => {
    setFlash('right')
    goTo(index + 1)
    setTimeout(() => setFlash(null), 300)
  }, [index, goTo])

  // Keyboard navigation
  useEffect(() => {
    function onKey(e) {
      if (e.key === 'ArrowLeft') prev()
      if (e.key === 'ArrowRight') next()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [prev, next])

  // Reset index when videos array changes
  useEffect(() => {
    setIndex(0)
  }, [videos.length])

  const swipeHandlers = useSwipeable({
    onSwipedLeft: next,
    onSwipedRight: prev,
    preventScrollOnSwipe: true,
    trackMouse: false,
  })

  if (total === 0) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 400, color: '#888' }}>
        No videos available
      </div>
    )
  }

  const video = videos[index]
  const canDelete = video?.isPrivate && currentUser?.email === video?.userShared

  return (
    <div style={{ position: 'relative', width: '100%', maxWidth: 420, margin: '0 auto' }}>
      {/* Flash overlay */}
      {flash && (
        <div style={{
          position: 'absolute', inset: 0, zIndex: 30, pointerEvents: 'none',
          background: flash === 'left' ? 'rgba(255,255,255,0.15)' : 'rgba(255,255,255,0.15)',
          transition: 'opacity 0.3s',
        }} />
      )}

      {/* Video + swipe area */}
      <div {...swipeHandlers} style={{ position: 'relative', height: 560 }}>
        <VideoPlayer
          video={video}
          active={true}
          onEnded={next}
        />

        {/* Navigation buttons */}
        {index > 0 && (
          <button onClick={prev} style={navBtnStyle('left')}>
            <FontAwesomeIcon icon={faChevronLeft} />
          </button>
        )}
        {index < total - 1 && (
          <button onClick={next} style={navBtnStyle('right')}>
            <FontAwesomeIcon icon={faChevronRight} />
          </button>
        )}
      </div>

      {/* Action sidebar */}
      <div style={{ position: 'absolute', right: -52, top: '30%', display: 'flex', flexDirection: 'column', gap: 12 }}>
        <VoteButtons video={video} />
        <button className="action-button" onClick={() => onShowComments?.(video)} style={sideBtn}>
          <i className="fas fa-comment" />
          <span style={{ fontSize: '0.7rem', display: 'block' }}>0</span>
        </button>
        {canDelete && (
          <button className="action-button" onClick={() => onDeleteVideo?.(video)} style={{ ...sideBtn, color: '#ff4444' }}>
            <i className="fas fa-trash" />
            <span style={{ fontSize: '0.7rem', display: 'block' }}>Delete</span>
          </button>
        )}
      </div>

      {/* Index indicator */}
      <div style={{ textAlign: 'center', marginTop: 8, color: '#888', fontSize: '0.75rem' }}>
        {index + 1} / {total}
      </div>
    </div>
  )
}

function navBtnStyle(side) {
  return {
    position: 'absolute',
    top: '50%',
    [side]: 8,
    transform: 'translateY(-50%)',
    background: 'rgba(0,0,0,0.5)',
    border: 'none',
    borderRadius: '50%',
    width: 36,
    height: 36,
    color: 'white',
    cursor: 'pointer',
    zIndex: 20,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  }
}

const sideBtn = {
  background: 'rgba(0,0,0,0.6)',
  border: 'none',
  borderRadius: 8,
  color: 'white',
  padding: '8px 10px',
  cursor: 'pointer',
  textAlign: 'center',
}
