import React, { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { compactNumber } from '../../i18n/format.js'
import { useAuth } from '../../hooks/useAuth.js'
import { useVideoReaction } from '../../hooks/useVideoReaction.js'

const HINT_MS = 3000
export default function VoteButtons({ video }) {
  const { t } = useTranslation('feed')
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
      showHint(t('loginToLike'))
      return
    }
    react(myReaction === target ? null : target, {
      onError: () => showHint(t('reactionFailed')),
    })
  }

  return (
    <>
      <button
        className={`action-btn${myReaction === 'LIKE' ? ' voted' : ''}`}
        onClick={() => toggle('LIKE')}
        disabled={isPending}
        title={t('like')}
      >
        <span className="icon material-symbols-outlined">favorite</span>
        <span className="label">{compactNumber(likeCount)}</span>
      </button>

      <button
        className={`action-btn${myReaction === 'DISLIKE' ? ' voted-down' : ''}`}
        onClick={() => toggle('DISLIKE')}
        disabled={isPending}
        title={t('dislike')}
      >
        <span className="icon material-symbols-outlined">thumb_down</span>
        <span className="label">{t('dislike')}</span>
      </button>

      {hint && <span className="vote-hint" role="status">{hint}</span>}
    </>
  )
}
