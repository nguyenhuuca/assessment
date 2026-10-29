import React, { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useVideos } from '../../hooks/useVideos.js'
import VideoSwiper from './VideoSwiper.jsx'

export function PublicFeed({ category, initialIndex, deepLinkId, deepLinkStartAt, onDeepLinkResolved, muted, onMutedChange, mobileSearchOpen, onCloseMobileSearch, onShowComments, onDeleteVideo, currentUser }) {
  const { data: videos = [], isLoading, error } = useVideos(category)

  // Deep-linked video and its resume point, captured when it resolves (the shell clears deepLinkId right after)
  const [deepLink, setDeepLink] = useState(null)
  if (deepLinkId && videos.length && deepLink?.id !== String(deepLinkId)) {
    const idx = videos.findIndex(v => String(v.id) === String(deepLinkId))
    if (idx >= 0) {
      setDeepLink({
        id: String(deepLinkId),
        index: idx,
        resume: deepLinkStartAt > 0 ? { videoId: String(deepLinkId), startAt: deepLinkStartAt } : null,
      })
    }
  }

  // Tell the shell once per resolved link, after the render that captured it
  useEffect(() => {
    if (deepLink) onDeepLinkResolved?.()
  }, [deepLink]) // eslint-disable-line react-hooks/exhaustive-deps

  const resolvedIndex = deepLink ? deepLink.index : (initialIndex ?? 0)

  if (isLoading) return <LoadingState />
  if (error) return <ErrorState message={error.message} />
  return (
    <VideoSwiper
      videos={videos}
      initialIndex={resolvedIndex}
      resume={deepLink?.resume ?? null}
      muted={muted}
      onMutedChange={onMutedChange}
      mobileSearchOpen={mobileSearchOpen}
      onCloseMobileSearch={onCloseMobileSearch}
      onShowComments={onShowComments}
      onDeleteVideo={onDeleteVideo}
      currentUser={currentUser}
    />
  )
}

function LoadingState() {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: 400 }}>
      <div className="spinner-border text-light" role="status" />
    </div>
  )
}

function ErrorState({ message }) {
  const { t } = useTranslation('feed')
  return (
    <div style={{ textAlign: 'center', padding: 40, color: '#ff6b6b' }}>
      {t('loadFailed', { message })}
    </div>
  )
}
