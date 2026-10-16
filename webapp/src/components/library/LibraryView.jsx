import React, { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../hooks/useAuth.js'
import { useConfirm } from '../../hooks/useConfirm.jsx'
import {
  useMyCollections,
  usePublicCollections,
  useCollectionVideos,
  useCollectionPlaylist,
  useDeleteCollection,
  useToggleCollectionItem,
} from '../../hooks/useCollections.js'
import VideoSwiper from '../video/VideoSwiper.jsx'
import CollectionFormModal from './CollectionFormModal.jsx'
import AddVideosModal from './AddVideosModal.jsx'
import { thumbnailUrl } from '../../utils/videoModel.js'
import { isPublic, pinDefaultFirst, collectionName } from './collectionUtils.js'

function CollectionCard({ collection, onOpen, onPlay }) {
  const { t } = useTranslation('library')
  const name = collectionName(collection)
  const count = collection.itemCount ?? 0
  const cover = thumbnailUrl(collection.coverFileId)
  const empty = !collection.itemCount
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <div
        className="explore-reel"
        role="button"
        tabIndex={0}
        title={name}
        aria-label={t('cardLabel', { name, count })}
        onClick={() => onOpen(collection.id)}
        onKeyDown={e => e.key === 'Enter' && onOpen(collection.id)}
      >
        {cover
          ? <img src={cover} alt="" className="explore-reel-img" loading="lazy" />
          : <div className="explore-reel-fallback"><span className="material-symbols-outlined" style={{ fontSize: 36 }}>video_library</span></div>}
        <div className="explore-reel-scrim" />
        <span className="explore-reel-views" style={{ flexDirection: 'column', alignItems: 'flex-start' }}>
          <strong>{name}</strong>
          <span style={{ fontSize: 12 }}>
            {t('videoCount', { count })}
            {isPublic(collection) && ` · ${t('public')}`}
          </span>
        </span>
      </div>
      <button
        className="app-btn primary"
        style={{ justifyContent: 'center' }}
        aria-label={t('playCollection', { name })}
        disabled={empty}
        title={empty ? t('emptyCannotPlay') : undefined}
        onClick={() => onPlay(collection.id)}
      >
        <span className="material-symbols-outlined" style={{ fontSize: 18 }}>play_arrow</span> {t('play')}
      </button>
    </div>
  )
}

function VideoTile({ video, onPlay, onRemove, removing }) {
  const { t } = useTranslation('library')
  const thumb = thumbnailUrl(video.fileId) || video.poster
  const title = video.title || t('untitled')
  return (
    <div
      className="explore-reel"
      role="button"
      tabIndex={0}
      title={title}
      aria-label={title}
      onClick={onPlay}
      onKeyDown={e => e.key === 'Enter' && onPlay()}
    >
      {thumb
        ? <img src={thumb} alt="" className="explore-reel-img" loading="lazy" />
        : <div className="explore-reel-fallback"><span className="material-symbols-outlined" style={{ fontSize: 36 }}>movie</span></div>}
      <div className="explore-reel-scrim" />
      <span className="explore-reel-title">{title}</span>
      {onRemove && (
        <button
          className="app-btn secondary"
          style={{ position: 'absolute', top: 8, right: 8, padding: '4px 8px' }}
          aria-label={t('removeVideoAria', { title })}
          aria-busy={removing}
          disabled={removing}
          onClick={e => { e.stopPropagation(); onRemove() }}
        >
          <span className="material-symbols-outlined" style={{ fontSize: 16 }}>close</span>
        </button>
      )}
    </div>
  )
}

const PLAYER_BOX = { position: 'relative', height: 'calc(100vh - var(--topnav-h))', display: 'flex', alignItems: 'center', justifyContent: 'center' }

/** Plays a list of videos in the swiper, with a back button to the grid it came from. */
function PlayerView({ videos, index, onBack, muted, onMutedChange, onShowComments, currentUser }) {
  const { t } = useTranslation('common')
  return (
    <div style={PLAYER_BOX}>
      <button className="app-btn secondary" style={{ position: 'absolute', top: 12, left: 12, zIndex: 5 }} onClick={onBack}>
        <span className="material-symbols-outlined" style={{ fontSize: 18 }}>arrow_back</span> {t('back')}
      </button>
      <VideoSwiper
        videos={videos}
        initialIndex={index}
        muted={muted}
        onMutedChange={onMutedChange}
        onShowComments={onShowComments}
        currentUser={currentUser}
      />
    </div>
  )
}

/** "Phát" on a card: loads the whole collection and plays it from the first video. */
function CollectionPlayer({ collectionId, onBack, muted, onMutedChange, onShowComments, currentUser }) {
  const { t } = useTranslation('library')
  const { data: videos = [], isLoading, error } = useCollectionPlaylist(collectionId)
  // Same box as the player, so the spinner sits where the video will appear
  if (isLoading) return <div style={PLAYER_BOX} role="status" aria-label={t('loadingAria')}><div className="vid-spinner" /></div>
  if (error || videos.length === 0) {
    return (
      <div className="explore-root">
        <button className="app-btn secondary" onClick={onBack}>
          <span className="material-symbols-outlined" style={{ fontSize: 18 }}>arrow_back</span> {t('library')}
        </button>
        {error
          ? <div className="app-alert error" role="alert" style={{ marginTop: 12 }}>{error.message}</div>
          : <p className="explore-empty-grid">{t('emptyCollection')}</p>}
      </div>
    )
  }
  return (
    <PlayerView
      videos={videos}
      index={0}
      onBack={onBack}
      muted={muted}
      onMutedChange={onMutedChange}
      onShowComments={onShowComments}
      currentUser={currentUser}
    />
  )
}

function CollectionDetail({ collection, collectionId, onBack, onEdit, onDelete, deleting, muted, onMutedChange, onShowComments, currentUser }) {
  const { t } = useTranslation(['library', 'common'])
  const [page, setPage] = useState(0)
  const [playIndex, setPlayIndex] = useState(null)
  const [adding, setAdding] = useState(false)
  const { data, isLoading, error } = useCollectionVideos(collectionId, page)
  const toggle = useToggleCollectionItem()
  const videos = data?.videos ?? []
  const totalPages = data?.totalPages ?? 0
  const canManage = !!collection?.canManage

  // Removing the last video of the last page would leave an empty page behind: step back
  useEffect(() => {
    if (totalPages > 0 && page > totalPages - 1) setPage(totalPages - 1)
  }, [page, totalPages])

  // Play inside the collection itself: the swiper runs over this page's videos
  if (playIndex !== null && videos.length > 0) {
    return (
      <PlayerView
        videos={videos}
        index={playIndex}
        onBack={() => setPlayIndex(null)}
        muted={muted}
        onMutedChange={onMutedChange}
        onShowComments={onShowComments}
        currentUser={currentUser}
      />
    )
  }

  function share() {
    const url = `${window.location.origin}${window.location.pathname}?l=${collectionId}`
    if (navigator.share) navigator.share({ title: collection ? collectionName(collection) : undefined, url }).catch(() => {})
    else navigator.clipboard?.writeText(url).catch(() => {})
  }

  return (
    <div className="explore-root">
      <section className="explore-section">
        <div className="explore-section-header" style={{ gap: 8, flexWrap: 'wrap' }}>
          <button className="app-btn secondary" onClick={onBack}>
            <span className="material-symbols-outlined" style={{ fontSize: 18 }}>arrow_back</span> {t('library')}
          </button>
          <h2 className="explore-section-title">{collection ? collectionName(collection) : t('collectionFallback')}</h2>
          <button className="app-btn secondary" onClick={share}>
            <span className="material-symbols-outlined" style={{ fontSize: 18 }}>share</span> {t('common:share')}
          </button>
          {canManage && (
            <button className="app-btn primary" onClick={() => setAdding(true)}>
              <span className="material-symbols-outlined" style={{ fontSize: 18 }}>add</span> {t('addVideo')}
            </button>
          )}
          {canManage && !collection.isDefault && (
            <>
              <button className="app-btn secondary" onClick={onEdit}>{t('edit')}</button>
              <button className="app-btn danger" onClick={onDelete} disabled={deleting} aria-busy={deleting}>{t('delete')}</button>
            </>
          )}
        </div>
        {canManage && collection.unavailableCount > 0 && (
          <p className="admin-cell-muted">{t('unavailableVideos', { count: collection.unavailableCount })}</p>
        )}
        {error && <div className="app-alert error" role="alert">{error.message}</div>}
        {toggle.error && <div className="app-alert error" role="alert">{toggle.error.message}</div>}
        {isLoading ? (
          <div className="vid-spinner" />
        ) : videos.length === 0 ? (
          <p className="explore-empty-grid">
            {canManage ? t('emptyCollectionHint') : t('emptyCollection')}
          </p>
        ) : (
          <div className="explore-reels-grid">
            {videos.map((v, i) => (
              <VideoTile
                key={v.id}
                video={v}
                onPlay={() => setPlayIndex(i)}
                onRemove={canManage ? () => toggle.mutate({ id: collectionId, videoId: v.id, inCollection: true, visibility: collection?.visibility }) : null}
                removing={toggle.isPending && toggle.variables?.videoId === v.id}
              />
            ))}
          </div>
        )}
        {totalPages > 1 && (
          <div style={{ display: 'flex', gap: 8, justifyContent: 'center', marginTop: 16 }}>
            <button className="app-btn secondary" disabled={page === 0} onClick={() => setPage(p => p - 1)}>{t('prev')}</button>
            <span className="admin-cell-muted">{page + 1}/{totalPages}</span>
            <button className="app-btn secondary" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>{t('next')}</button>
          </div>
        )}
      </section>
      {adding && (
        <AddVideosModal
          collection={collection}
          onClose={() => setAdding(false)}
        />
      )}
    </div>
  )
}

export default function LibraryView({ initialCollectionId = null, muted, onMutedChange, onShowComments }) {
  const { t } = useTranslation(['library', 'common'])
  const { isLoggedIn, user } = useAuth()
  const [confirm, confirmDialog] = useConfirm()
  const publicQuery = usePublicCollections()
  const mine = useMyCollections(isLoggedIn)
  const remove = useDeleteCollection()
  const [openId, setOpenId] = useState(initialCollectionId)
  const [formState, setFormState] = useState(null) // { collection? }
  const [playId, setPlayId] = useState(null)

  const publicList = publicQuery.data ?? []
  // Admins also receive public collections in "mine"; keep this section to private ones
  const mineList = pinDefaultFirst((mine.data ?? []).filter(c => !isPublic(c)))
  const current = [...(mine.data ?? []), ...publicList].find(c => String(c.id) === String(openId))

  async function handleDelete(c) {
    const ok = await confirm({
      title: t('deleteTitle'),
      message: t('deleteMessage', { name: collectionName(c) }),
      confirmLabel: t('delete'),
      danger: true,
    })
    if (ok) remove.mutate(c.id, { onSuccess: () => setOpenId(null) })
  }

  if (playId) {
    return (
      <CollectionPlayer
        collectionId={playId}
        onBack={() => setPlayId(null)}
        muted={muted}
        onMutedChange={onMutedChange}
        onShowComments={onShowComments}
        currentUser={user}
      />
    )
  }

  if (openId) {
    return (
      <>
        <CollectionDetail
          collection={current}
          collectionId={openId}
          onBack={() => setOpenId(null)}
          onEdit={() => setFormState({ collection: current })}
          onDelete={() => handleDelete(current)}
          deleting={remove.isPending}
          muted={muted}
          onMutedChange={onMutedChange}
          onShowComments={onShowComments}
          currentUser={user}
        />
        {formState && <CollectionFormModal collection={formState.collection} onClose={() => setFormState(null)} />}
        {confirmDialog}
      </>
    )
  }

  return (
    <div className="explore-root">
      <section className="explore-section">
        <div className="explore-section-header">
          <h2 className="explore-section-title">{t('featured')}</h2>
        </div>
        {publicQuery.error && <div className="app-alert error" role="alert">{publicQuery.error.message}</div>}
        {publicQuery.isLoading ? (
          <div className="vid-spinner" />
        ) : publicList.length === 0 ? (
          <p className="explore-empty-grid">{t('noPublic')}</p>
        ) : (
          <div className="explore-reels-grid">
            {publicList.map(c => <CollectionCard key={c.id} collection={c} onOpen={setOpenId} onPlay={setPlayId} />)}
          </div>
        )}
      </section>

      <section className="explore-section">
        <div className="explore-section-header" style={{ gap: 8 }}>
          <h2 className="explore-section-title">{t('mine')}</h2>
          {isLoggedIn && (
            <button className="app-btn primary" onClick={() => setFormState({})}>
              <span className="material-symbols-outlined" style={{ fontSize: 18 }}>add</span> {t('create')}
            </button>
          )}
        </div>
        {!isLoggedIn ? (
          <p className="explore-empty-grid" role="status">{t('loginToCreate')}</p>
        ) : (
          <>
            {mine.error && <div className="app-alert error" role="alert">{mine.error.message}</div>}
            {remove.error && <div className="app-alert error" role="alert">{remove.error.message}</div>}
            {mine.isLoading ? (
              <div className="vid-spinner" />
            ) : (
              <div className="explore-reels-grid">
                {mineList.map(c => (
                  <div key={c.id} style={{ position: 'relative' }}>
                    <CollectionCard collection={c} onOpen={setOpenId} onPlay={setPlayId} />
                    {!c.isDefault && c.canManage && (
                      <button
                        className="app-btn secondary"
                        style={{ position: 'absolute', top: 8, right: 8, padding: '4px 8px' }}
                        aria-label={t('deleteCollectionAria', { name: collectionName(c) })}
                        aria-busy={remove.isPending && remove.variables === c.id}
                        onClick={() => handleDelete(c)}
                      >
                        <span className="material-symbols-outlined" style={{ fontSize: 16 }}>delete</span>
                      </button>
                    )}
                  </div>
                ))}
              </div>
            )}
          </>
        )}
      </section>

      {formState && <CollectionFormModal collection={formState.collection} onClose={() => setFormState(null)} />}
      {confirmDialog}
    </div>
  )
}
