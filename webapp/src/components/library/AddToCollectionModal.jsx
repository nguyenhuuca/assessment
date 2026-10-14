import React, { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../hooks/useAuth.js'
import {
  useMyCollections,
  useCollectionMembership,
  useToggleCollectionItem,
  useCreateCollection,
} from '../../hooks/useCollections.js'
import { isBusyFor } from '../../utils/mutationBusy.js'
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

  useEffect(() => {
    const onKey = e => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  // Only collections the viewer can manage (own collections; public ones only for admins)
  const collections = (mine.data ?? []).filter(c => c.canManage !== false)
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
      <div className="app-modal" role="dialog" aria-modal="true" aria-label={t('saveToCollection')}>
        <div className="app-modal-header">
          <span className="app-modal-title">{t('saveToCollection')}</span>
          <button className="app-modal-close" onClick={onClose} aria-label={t('common:close')}>
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>close</span>
          </button>
        </div>
        <div className="app-modal-body">
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
                <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 4 }}>
                  {collections.map(c => (
                    <li key={c.id}>
                      <label
                        style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '6px 0', cursor: 'pointer' }}
                        aria-busy={isBusyFor(toggle, c.id, v => v?.id)}
                      >
                        <input
                          type="checkbox"
                          checked={savedIds.has(String(c.id))}
                          disabled={membership.isLoading || isBusyFor(toggle, c.id, v => v?.id)}
                          onChange={() => handleToggle(c)}
                        />
                        <span style={{ flex: 1 }}>{collectionName(c)}</span>
                        {isAdmin && isPublic(c) && (
                          <span
                            className="material-symbols-outlined"
                            title={t('public')}
                            aria-label={t('public')}
                            style={{ fontSize: 18, color: 'var(--accent-cyan)' }}
                          >
                            public
                          </span>
                        )}
                      </label>
                    </li>
                  ))}
                </ul>
              )}

              <form onSubmit={handleCreate} style={{ marginTop: 16 }}>
                <label className="app-label" htmlFor="new-collection-name">{t('createNew')}</label>
                <input
                  id="new-collection-name"
                  className="app-input"
                  value={name}
                  maxLength={NAME_MAX}
                  placeholder={t('nameLabel')}
                  onChange={e => setName(e.target.value)}
                />
                {isAdmin && (
                  <label style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8, fontSize: 13 }}>
                    <input type="checkbox" checked={makePublic} onChange={e => setMakePublic(e.target.checked)} />
                    {t('public')}
                  </label>
                )}
                {formError && <div className="app-alert error" role="alert" style={{ marginTop: 8 }}>{formError}</div>}
                <button
                  type="submit"
                  className="app-btn primary"
                  style={{ marginTop: 10 }}
                  disabled={create.isPending}
                  aria-busy={create.isPending}
                >
                  {t('createAndSave')}
                </button>
              </form>
            </>
          )}
        </div>
        <div className="app-modal-footer">
          <button className="app-btn secondary" onClick={onClose}>{t('common:close')}</button>
        </div>
      </div>
    </div>
  )
}
