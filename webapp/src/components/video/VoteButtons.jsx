import React, { useEffect, useRef, useState } from 'react'
import { useAuth } from '../../hooks/useAuth.js'
import { useVideoReaction } from '../../hooks/useVideoReaction.js'

const HINT_MS = 3000
const LOGIN_HINT = 'Đăng nhập để thích video'
const ERROR_HINT = 'Không lưu được, thử lại sau'

function fmtCount(n) {
  if (n >= 1000) return `${(n / 1000).toFixed(1)}K`
  return String(n)
}

export default function VoteButtons({ video }) {
  const { isLoggedIn } = useAuth()
  const { likeCount, myReaction, isPending, react } = useVideoReaction(video?.id)
  const [hint, setHint] = useState('')
  const timer = useRef(null)

  // Reset hint when video changes (reaction state is keyed by video id in the query cache)
  useEffect(() => {
    setHint('')
    return () => clearTimeout(timer.current)
  }, [video?.id])

  function showHint(message) {
    setHint(message)
    clearTimeout(timer.current)
    timer.current = setTimeout(() => setHint(''), HINT_MS)
  }

  function toggle(target) {
    if (!isLoggedIn) {
      showHint(LOGIN_HINT)
      return
    }
    react(myReaction === target ? null : target, {
      onError: () => showHint(ERROR_HINT),
    })
  }

  return (
    <>
      <button
        className={`action-btn${myReaction === 'LIKE' ? ' voted' : ''}`}
        onClick={() => toggle('LIKE')}
        disabled={isPending}
        title="Like"
      >
        <span className="icon material-symbols-outlined">favorite</span>
        <span className="label">{fmtCount(likeCount)}</span>
      </button>

      <button
        className={`action-btn${myReaction === 'DISLIKE' ? ' voted-down' : ''}`}
        onClick={() => toggle('DISLIKE')}
        disabled={isPending}
        title="Dislike"
      >
        <span className="icon material-symbols-outlined">thumb_down</span>
        <span className="label">Dislike</span>
      </button>

      {hint && <span className="vote-hint" role="status">{hint}</span>}
    </>
  )
}
