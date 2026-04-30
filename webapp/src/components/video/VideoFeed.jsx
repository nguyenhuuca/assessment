import React from 'react'
import { useVideos, usePrivateVideos } from '../../hooks/useVideos.js'
import VideoSwiper from './VideoSwiper.jsx'

export function PublicFeed({ category, initialIndex, mobileSearchOpen, onCloseMobileSearch, onShowComments, onDeleteVideo, currentUser }) {
  const { data: videos = [], isLoading, error } = useVideos(category)

  if (isLoading) return <LoadingState />
  if (error) return <ErrorState message={error.message} />
  return (
    <VideoSwiper
      videos={videos}
      initialIndex={initialIndex}
      mobileSearchOpen={mobileSearchOpen}
      onCloseMobileSearch={onCloseMobileSearch}
      onShowComments={onShowComments}
      onDeleteVideo={onDeleteVideo}
      currentUser={currentUser}
    />
  )
}

export function PrivateFeed({ mobileSearchOpen, onCloseMobileSearch, onShowComments, onDeleteVideo, currentUser }) {
  const { data: videos = [], isLoading, error } = usePrivateVideos()

  if (isLoading) return <LoadingState />
  if (error) return <ErrorState message={error.message} />
  return (
    <VideoSwiper
      videos={videos}
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
