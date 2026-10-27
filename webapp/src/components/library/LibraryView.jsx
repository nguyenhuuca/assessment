import React, { memo, useEffect, useMemo, useState } from 'react'
import {
  DndContext, closestCenter, KeyboardSensor, PointerSensor, TouchSensor, useSensor, useSensors,
} from '@dnd-kit/core'
import {
  SortableContext, rectSortingStrategy, sortableKeyboardCoordinates, useSortable,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
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
  useMoveCollectionItem,
} from '../../hooks/useCollections.js'
import VideoSwiper from '../video/VideoSwiper.jsx'
import CollectionFormModal from './CollectionFormModal.jsx'
import AddVideosModal from './AddVideosModal.jsx'
import { thumbnailUrl } from '../../utils/videoModel.js'
import { isPublic, pinDefaultFirst, collectionName, moveAfterId } from './collectionUtils.js'

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

/** Thumbnail tile. Without `onPlay` it is inert (sort mode); `children` render on top, e.g. a drag handle. */
function VideoTile({ video, onPlay, onRemove, removing, children }) {
  const { t } = useTranslation('library')
  const thumb = thumbnailUrl(video.fileId) || video.poster
  const title = video.title || t('untitled')
  return (
    <div
      className="explore-reel"
      role={onPlay ? 'button' : undefined}
      tabIndex={onPlay ? 0 : undefined}
      title={title}
      aria-label={onPlay ? title : undefined}
      onClick={onPlay}
      onKeyDown={onPlay ? e => e.key === 'Enter' && onPlay() : undefined}
      style={onPlay ? undefined : { cursor: 'default' }}
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
      {children}
    </div>
  )
}

const HANDLE_STYLE = { position: 'absolute', top: 8, left: 8, padding: '6px 8px', cursor: 'grab', touchAction: 'none' }

/** A tile that can be dragged by its handle only, so the rest of the page still scrolls and swipes on touch. */
const SortableTile = memo(function SortableTile({ video }) {
  const { t } = useTranslation('library')
  const { attributes, listeners, setNodeRef, setActivatorNodeRef, transform, transition, isDragging } = useSortable({ id: video.id })
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.6 : 1,
    zIndex: isDragging ? 2 : undefined,
    position: 'relative',
  }
  return (
    <div ref={setNodeRef} style={style}>
      <VideoTile video={video}>
        <button
          ref={setActivatorNodeRef}
          className="app-btn secondary"
          style={HANDLE_STYLE}
          aria-label={t('dragHandleAria', { title: video.title || t('untitled') })}
          {...attributes}
          {...listeners}
        >
          <span className="material-symbols-outlined" style={{ fontSize: 18 }}>drag_indicator</span>
        </button>
      </VideoTile>
    </div>
  )
})

/** "Sắp xếp" mode: the whole list (not one page) as a sortable grid; a drop saves the new position. */
function ReorderGrid({ collectionId, visibility }) {
  const { t } = useTranslation('library')
  const { data: videos = [], isLoading, error } = useCollectionPlaylist(collectionId)
  const move = useMoveCollectionItem()
  const ids = useMemo(() => videos.map(v => v.id), [videos])
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 200, tolerance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  const titleOf = id => videos.find(v => v.id === id)?.title || t('untitled')
  const infoOf = id => ({ title: titleOf(id), position: ids.indexOf(id) + 1, total: ids.length })
  const announcements = {
    onDragStart: ({ active }) => t('dragStart', infoOf(active.id)),
    onDragOver: ({ active, over }) => (over ? t('dragOver', { ...infoOf(active.id), position: ids.indexOf(over.id) + 1 }) : undefined),
    onDragEnd: ({ active, over }) => t('dragDrop', { ...infoOf(active.id), position: ids.indexOf(over?.id ?? active.id) + 1 }),
    onDragCancel: ({ active }) => t('dragCancel', infoOf(active.id)),
  }

  function handleDragEnd({ active, over }) {
    if (!over || active.id === over.id) return
    const from = ids.indexOf(active.id)
    const to = ids.indexOf(over.id)
    if (from < 0 || to < 0) return
    move.mutate({ id: collectionId, videoId: active.id, afterVideoId: moveAfterId(ids, from, to), visibility })
  }

  if (isLoading) return <div className="vid-spinner" />
  return (
    <>
      <p className="admin-cell-muted">{t('reorderHint')}</p>
      {error && <div className="app-alert error" role="alert">{error.message}</div>}
      {move.isError && <div className="app-alert error" role="alert">{t('moveError')}</div>}
      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        onDragEnd={handleDragEnd}
        accessibility={{ announcements, screenReaderInstructions: { draggable: t('dragInstructions') } }}
      >
        <SortableContext items={ids} strategy={rectSortingStrategy}>
          <div className="explore-reels-grid">
            {videos.map(v => <SortableTile key={v.id} video={v} />)}
          </div>
        </SortableContext>
      </DndContext>
    </>
  )
}

// Fills the main area, which runs full height behind the transparent header while a player is open
const PLAYER_BOX = { position: 'relative', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }

/** Plays a list of videos in the swiper, with a back button to the grid it came from. */
function PlayerView({ videos, index, onBack, muted, onMutedChange, onShowComments, currentUser, onPlayerChange }) {
  const { t } = useTranslation('common')
  // Tells the shell a reel is on screen, so it switches to the same overlay layout as Home
  useEffect(() => {
    onPlayerChange?.(true)
    return () => onPlayerChange?.(false)
  }, [onPlayerChange])
  return (
    <div style={PLAYER_BOX}>
      <button className="app-btn back library-player-back" style={{ position: 'absolute', left: 12, zIndex: 5 }} onClick={onBack}>
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
function CollectionPlayer({ collectionId, onBack, muted, onMutedChange, onShowComments, currentUser, onPlayerChange }) {
  const { t } = useTranslation('library')
  const { data: videos = [], isLoading, error } = useCollectionPlaylist(collectionId)
  // Same box as the player, so the spinner sits where the video will appear
  if (isLoading) return <div style={PLAYER_BOX} role="status" aria-label={t('loadingAria')}><div className="vid-spinner" /></div>
  if (error || videos.length === 0) {
    return (
      <div className="explore-root">
        <button className="app-btn back" onClick={onBack}>
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
      onPlayerChange={onPlayerChange}
    />
  )
}

function CollectionDetail({ collection, collectionId, onBack, onEdit, onDelete, deleting, muted, onMutedChange, onShowComments, currentUser, onPlayerChange }) {
  const { t } = useTranslation(['library', 'common'])
  const [page, setPage] = useState(0)
  const [playIndex, setPlayIndex] = useState(null)
  const [adding, setAdding] = useState(false)
  const [sorting, setSorting] = useState(false)
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
        onPlayerChange={onPlayerChange}
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
          <button className="app-btn back" onClick={onBack}>
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
          {canManage && (
            <button className="app-btn secondary" aria-pressed={sorting} onClick={() => setSorting(s => !s)}>
              <span className="material-symbols-outlined" style={{ fontSize: 18 }}>{sorting ? 'check' : 'swap_vert'}</span> {sorting ? t('reorderDone') : t('reorder')}
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
        {canManage && sorting ? (
          <ReorderGrid collectionId={collectionId} visibility={collection?.visibility} />
        ) : isLoading ? (
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
        {totalPages > 1 && !sorting && (
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

export default function LibraryView({ initialCollectionId = null, muted, onMutedChange, onShowComments, onPlayerChange }) {
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
        onPlayerChange={onPlayerChange}
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
          onPlayerChange={onPlayerChange}
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
            <button className="app-btn outline" onClick={() => setFormState({})}>
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
