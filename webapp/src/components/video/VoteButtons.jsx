import React, { useEffect, useState } from 'react'
import { videosApi } from '../../api/videos.js'

function fmtCount(n) {
  if (n >= 1000) return `${(n / 1000).toFixed(1)}K`
  return String(n)
}

export default function VoteButtons({ video }) {
  const [vote,      setVote]      = useState('none') // 'up' | 'down' | 'none'
  const [upCount,   setUpCount]   = useState(video?.upvotes   || 0)
  const [downCount, setDownCount] = useState(video?.downvotes || 0)

  // Reset when video changes
  useEffect(() => {
    setVote('none')
    setUpCount(video?.upvotes   || 0)
    setDownCount(video?.downvotes || 0)
  }, [video?.id])

  async function handleUp() {
    if (vote === 'down') setDownCount(c => Math.max(0, c - 1))
    if (vote === 'up') {
      setUpCount(c => Math.max(0, c - 1))
      setVote('none')
    } else {
      setUpCount(c => c + 1)
      setVote('up')
      try { await videosApi.like(video?.id) } catch {}
    }
  }

  async function handleDown() {
    if (vote === 'up') setUpCount(c => Math.max(0, c - 1))
    if (vote === 'down') {
      setDownCount(c => Math.max(0, c - 1))
      setVote('none')
    } else {
      setDownCount(c => c + 1)
      setVote('down')
      try { await videosApi.unlike(video?.id) } catch {}
    }
  }

  return (
    <>
      {/* Like */}
      <button
        className={`action-btn${vote === 'up' ? ' voted' : ''}`}
        onClick={handleUp}
        title="Like"
      >
        <span className="icon material-symbols-outlined">favorite</span>
        <span className="label">{fmtCount(upCount)}</span>
      </button>

      {/* Dislike */}
      <button
        className={`action-btn${vote === 'down' ? ' voted-down' : ''}`}
        onClick={handleDown}
        title="Dislike"
      >
        <span className="icon material-symbols-outlined">thumb_down</span>
        <span className="label">Dislike</span>
      </button>
    </>
  )
}
