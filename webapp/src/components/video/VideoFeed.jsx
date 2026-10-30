import React, { useEffect, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useVideos } from '../../hooks/useVideos.js'
import { videosApi } from '../../api/videos.js'
import { mapApiVideo } from '../../utils/videoModel.js'
import VideoSwiper from './VideoSwiper.jsx'

export function PublicFeed({ category, initialIndex, deepLinkId, deepLinkStartAt, onDeepLinkResolved, muted, onMutedChange, mobileSearchOpen, onCloseMobileSearch, onShowComments, onDeleteVideo, currentUser }) {
  const { data: videos = [], isLoading, error } = useVideos(category)

  // Deep-linked video, its resume point and (when it is not in the feed) the fetched video, captured when the
  // link resolves: the shell clears deepLinkId right after, so none of this can be derived from props later.
  const [deepLink, setDeepLink] = useState(null)
  const wantedId = deepLinkId ? String(deepLinkId) : null
  const pending = wantedId !== null && deepLink?.id !== wantedId
  const feedIndex = pending ? videos.findIndex(v => String(v.id) === wantedId) : -1
  const needsFetch = pending && !isLoading && !error && feedIndex < 0

  // Not in the feed (e.g. a COLLECTION_ONLY video): fetch it by id (FR-11)
  const fetched = useQuery({
    queryKey: ['video', wantedId],
    queryFn: async () => {
      const res = await videosApi.get(wantedId)
      return mapApiVideo(res.data || res)
    },
    enabled: needsFetch,
    retry: false,
    staleTime: 5 * 60 * 1000,
  })

  const resume = deepLinkStartAt > 0 ? { videoId: wantedId, startAt: deepLinkStartAt } : null
  if (pending && feedIndex >= 0) {
    setDeepLink({ id: wantedId, index: feedIndex, inserted: null, resume })
  } else if (needsFetch && fetched.isSuccess) {
    setDeepLink({ id: wantedId, index: 0, inserted: fetched.data, resume })
  } else if (needsFetch && fetched.isError) {
    // Hidden or unknown video: show the feed as today, with no message (EC-6)
    setDeepLink({ id: wantedId, index: initialIndex ?? 0, inserted: null, resume: null })
  }

  // Tell the shell once per resolved link, after the render that captured it
  useEffect(() => {
    if (deepLink) onDeepLinkResolved?.()
  }, [deepLink]) // eslint-disable-line react-hooks/exhaustive-deps

  const resolvedIndex = deepLink ? deepLink.index : (initialIndex ?? 0)
  const inserted = deepLink?.inserted ?? null
  // A later feed refetch may contain the inserted video too: keep it once, as slide 0
  const slides = useMemo(
    () => (inserted ? [inserted, ...videos.filter(v => String(v.id) !== String(inserted.id))] : videos),
    [inserted, videos],
  )

  if (isLoading) return <LoadingState />
  if (error) return <ErrorState message={error.message} />
  if (needsFetch) return <LoadingState />
  return (
    <VideoSwiper
      videos={slides}
      searchVideos={videos}
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
