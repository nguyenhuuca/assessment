import React, { useMemo } from 'react'
import { useVideos, usePrivateVideos } from '../../hooks/useVideos.js'
import VideoSwiper from './VideoSwiper.jsx'

export function PublicFeed({ category, initialIndex, deepLinkId, onDeepLinkResolved, muted, onMutedChange, mobileSearchOpen, onCloseMobileSearch, onShowComments, onDeleteVideo, currentUser }) {
  const { data: videos = [], isLoading, error } = useVideos(category)

  const resolvedIndex = useMemo(() => {
    if (!deepLinkId || !videos.length) return initialIndex ?? 0
    const idx = videos.findIndex(v => String(v.id) === String(deepLinkId))
    if (idx >= 0) { onDeepLinkResolved?.(); return idx }
    return initialIndex ?? 0
  }, [deepLinkId, videos]) // eslint-disable-line react-hooks/exhaustive-deps

  if (isLoading) return <LoadingState />
  if (error) return <ErrorState message={error.message} />
  return (
    <VideoSwiper
      videos={videos}
      initialIndex={resolvedIndex}
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

export function PrivateFeed({ muted, onMutedChange, mobileSearchOpen, onCloseMobileSearch, onShowComments, onDeleteVideo, currentUser }) {
  const { data: videos = [], isLoading, error } = usePrivateVideos()

  if (isLoading) return <LoadingState />
  if (error) return <ErrorState message={error.message} />
  return (
    <VideoSwiper
      videos={videos}
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
  return (
    <div style={{ textAlign: 'center', padding: 40, color: '#ff6b6b' }}>
      Failed to load videos: {message}
    </div>
  )
}
