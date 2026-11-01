import React, { useEffect, useId, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../hooks/useAuth.js'
import {
  useMyCollections,
  useCollectionMembership,
  useToggleCollectionItem,
  useCreateCollection,
} from '../../hooks/useCollections.js'
import { isBusyFor } from '../../utils/mutationBusy.js'
import { useStableOrder } from '../../hooks/useStableOrder.js'
import { isPublic, validateName, collectionName, NAME_MAX } from './collectionUtils.js'

export default function AddToCollectionModal({ video, onClose }) {
  const { t } = useTranslation(['library', 'common'])
  const { isLoggedIn, user } = useAuth()
  const isAdmin = !!user?.isAdmin
  const videoId = video?.id
  const mine = useMyCollections(isLoggedIn)
  const membership = useCollectionMembership(videoId, isLoggedIn)
  const toggle = useToggleCollectionItem()
  const create = useCreateCollection()
  const [name, setName] = useState('')
  const [makePublic, setMakePublic] = useState(false)
  const [formError, setFormError] = useState('')
  const uid = useId()

  useEffect(() => {
    const onKey = e => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  // The server sorts by updatedAt, which every add/remove bumps; keep the order from when the modal opened
  // so a toggled row does not jump to the top after the refetch.
  const ordered = useStableOrder(mine.data)

  // Only collections the viewer can manage (own collections; public ones only for admins)
  const collections = ordered.filter(c => c.canManage !== false)
  const savedIds = new Set((membership.data ?? []).map(String))
  const apiError = toggle.error?.message || create.error?.message || ''

  function handleToggle(c) {
    toggle.mutate({ id: c.id, videoId, inCollection: savedIds.has(String(c.id)), visibility: c.visibility })
  }

  function handleCreate(e) {
    e.preventDefault()
    const { name: trimmed, error } = validateName(name)
    setFormError(error)
    if (error) return
    create.mutate(
      { name: trimmed, videoId, ...(isAdmin && makePublic ? { visibility: 'PUBLIC' } : {}) },
      { onSuccess: () => { setName(''); setMakePublic(false) } },
    )
  }

  return (
    <div className="app-modal-backdrop" onClick={e => { if (e.target === e.currentTarget) onClose() }}>
      <div className="app-modal atc-modal" role="dialog" aria-modal="true" aria-label={t('saveToCollection')}>
        <div className="app-modal-header atc-header">
          <span className="atc-header-icon" aria-hidden="true">
            <span className="material-symbols-outlined">bookmark_add</span>
          </span>
          <div className="atc-header-text">
            <span className="app-modal-title">{t('saveToCollection')}</span>
            {video?.title && <span className="atc-header-sub" title={video.title}>{video.title}</span>}
          </div>
          <button className="app-modal-close" onClick={onClose} aria-label={t('common:close')}>
            <span className="material-symbols-outlined" style={{ fontSize: 20 }} aria-hidden="true">close</span>
          </button>
        </div>
        <div className="app-modal-body atc-body">
          {!isLoggedIn ? (
            <p className="admin-cell-muted" role="status" style={{ margin: 0 }}>
              {t('loginToSave')}
            </p>
          ) : (
            <>
              {(apiError || mine.error?.message) && (
                <div className="app-alert error" role="alert">{apiError || mine.error.message}</div>
              )}
              {mine.isLoading ? (
                <div className="vid-spinner" />
              ) : (
                <ul className="atc-list">
                  {collections.map(c => {
                    const checked = savedIds.has(String(c.id))
                    const busy = isBusyFor(toggle, c.id, v => v?.id)
                    const showPublic = isAdmin && isPublic(c)
                    const nameId = `${uid}-c-${c.id}`
                    const iconProps = showPublic
                      ? { title: t('public'), 'aria-label': t('public'), role: 'img' }
                      : { 'aria-hidden': true }
                    return (
                      <li key={c.id}>
                        <label className={`atc-row${checked ? ' is-checked' : ''}`} aria-busy={busy}>
                          <span
                            className={`atc-icon${c.isDefault ? ' is-default' : ''}${showPublic ? ' is-public' : ''}`}
                            {...iconProps}
                          >
                            <span className="material-symbols-outlined" aria-hidden="true">
                              {c.isDefault ? 'schedule' : showPublic ? 'public' : 'folder'}
                            </span>
                          </span>
                          <span className="atc-text">
                            <span className="atc-name" id={nameId}>{collectionName(c)}</span>
                            {c.itemCount != null && (
                              <span className="atc-meta">{t('videoCount', { count: c.itemCount })}</span>
                            )}
                          </span>
                          <input
                            type="checkbox"
                            className="atc-input"
                            aria-labelledby={nameId}
                            checked={checked}
                            disabled={membership.isLoading || busy}
                            onChange={() => handleToggle(c)}
                          />
                          <span className="atc-check" aria-hidden="true">
                            <span className="material-symbols-outlined">{busy ? 'progress_activity' : 'check'}</span>
                          </span>
                        </label>
                      </li>
                    )
                  })}
                </ul>
              )}

              <form onSubmit={handleCreate} className="atc-create">
                <label className="app-label" htmlFor="new-collection-name">{t('createNew')}</label>
                <div className="atc-create-row">
                  <input
                    id="new-collection-name"
                    className="app-input"
                    value={name}
                    maxLength={NAME_MAX}
                    placeholder={t('nameLabel')}
                    onChange={e => setName(e.target.value)}
                  />
                  <button
                    type="submit"
                    className="app-btn primary atc-create-btn"
                    disabled={create.isPending}
                    aria-busy={create.isPending}
                  >
                    <span className="material-symbols-outlined" aria-hidden="true">add</span>
                    {t('createAndSave')}
                  </button>
                </div>
                {isAdmin && (
                  <label className="atc-switch">
                    <input type="checkbox" checked={makePublic} onChange={e => setMakePublic(e.target.checked)} />
                    <span className="atc-switch-track" aria-hidden="true" />
                    {t('public')}
                  </label>
                )}
                {formError && (
                  <div className="app-alert error" role="alert" style={{ marginTop: 8, marginBottom: 0 }}>{formError}</div>
                )}
              </form>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
