import React, { useMemo, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { useVideos } from '../../hooks/useVideos.js'
import VideoSwiper from './VideoSwiper.jsx'

export function PublicFeed({ category, initialIndex, deepLinkId, deepLinkStartAt, onDeepLinkResolved, muted, onMutedChange, mobileSearchOpen, onCloseMobileSearch, onShowComments, onDeleteVideo, currentUser }) {
  const { data: videos = [], isLoading, error } = useVideos(category)

  // Resume point of the deep-linked video, captured when it resolves (the shell clears deepLinkId right after)
  const resumeRef = useRef(null)

  const resolvedIndex = useMemo(() => {
    if (!deepLinkId || !videos.length) return initialIndex ?? 0
    const idx = videos.findIndex(v => String(v.id) === String(deepLinkId))
    if (idx >= 0) {
      resumeRef.current = deepLinkStartAt > 0 ? { videoId: String(deepLinkId), startAt: deepLinkStartAt } : null
      onDeepLinkResolved?.()
      return idx
    }
    return initialIndex ?? 0
  }, [deepLinkId, videos]) // eslint-disable-line react-hooks/exhaustive-deps

  if (isLoading) return <LoadingState />
  if (error) return <ErrorState message={error.message} />
  return (
    <VideoSwiper
      videos={videos}
      initialIndex={resolvedIndex}
      resume={resumeRef.current}
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
