import React, { useEffect, useId, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../hooks/useAuth.js'
import { useMyCollections, useCreateCollection, useBulkAddToCollection } from '../../hooks/useCollections.js'
import { useStableOrder } from '../../hooks/useStableOrder.js'
import { isPublic, validateName, collectionName, NAME_MAX } from '../library/collectionUtils.js'
import ModalHeader from '../common/ModalHeader.jsx'

/** Pick ONE collection (or create one) and add all `videoIds` to it. Calls onDone(result) on success. */
export default function BulkAddToCollectionModal({ videoIds, onClose, onDone }) {
  const { t } = useTranslation(['admin', 'library'])
  const { user } = useAuth()
  const isAdmin = !!user?.isAdmin
  const mine = useMyCollections(true)
  const create = useCreateCollection()
  const add = useBulkAddToCollection()
  const [selected, setSelected] = useState(null)
  const [name, setName] = useState('')
  const [makePublic, setMakePublic] = useState(false)
  const [formError, setFormError] = useState('')
  const uid = useId()
  const count = videoIds.length

  useEffect(() => {
    const onKey = e => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const ordered = useStableOrder(mine.data)
  const collections = ordered.filter(c => c.canManage !== false)
  const pending = add.isPending || create.isPending
  const apiError = add.error?.message || create.error?.message || mine.error?.message || ''

  function submit(id, visibility) {
    add.mutate({ id, videoIds, visibility }, { onSuccess: res => onDone(res) })
  }

  function handleAdd() {
    const c = collections.find(x => String(x.id) === String(selected))
    if (c) submit(c.id, c.visibility)
  }

  function handleCreate(e) {
    e.preventDefault()
    const { name: trimmed, error } = validateName(name)
    setFormError(error)
    if (error) return
    const visibility = isAdmin && makePublic ? 'PUBLIC' : undefined
    create.mutate(
      { name: trimmed, ...(visibility ? { visibility } : {}) },
      { onSuccess: created => { if (created?.id) submit(created.id, visibility ?? 'PRIVATE') } },
    )
  }

  return (
    <div className="app-modal-backdrop" onClick={e => { if (e.target === e.currentTarget) onClose() }}>
      <div className="app-modal atc-modal" role="dialog" aria-modal="true" aria-label={t('bulkAddTitle')}>
        <ModalHeader
          icon="bookmark_add"
          title={t('bulkAddTitle')}
          subtitle={t('bulkAddSubtitle', { count })}
          onClose={onClose}
        />
        <div className="app-modal-body atc-body">
          {apiError && <div className="app-alert error" role="alert">{apiError}</div>}
          {mine.isLoading ? (
            <div className="vid-spinner" />
          ) : collections.length === 0 ? (
            <p className="admin-cell-muted" style={{ margin: 0 }}>{t('bulkAddNone')}</p>
          ) : (
            <ul className="atc-list" role="radiogroup" aria-label={t('bulkAddTitle')}>
              {collections.map(c => {
                const checked = String(selected) === String(c.id)
                const showPublic = isAdmin && isPublic(c)
                const nameId = `${uid}-c-${c.id}`
                return (
                  <li key={c.id}>
                    <label className={`atc-row${checked ? ' is-checked' : ''}`}>
                      <span className={`atc-icon${c.isDefault ? ' is-default' : ''}${showPublic ? ' is-public' : ''}`} aria-hidden="true">
                        <span className="material-symbols-outlined">
                          {c.isDefault ? 'schedule' : showPublic ? 'public' : 'folder'}
                        </span>
                      </span>
                      <span className="atc-text">
                        <span className="atc-name" id={nameId}>{collectionName(c)}</span>
                        {c.itemCount != null && (
                          <span className="atc-meta">{t('library:videoCount', { count: c.itemCount })}</span>
                        )}
                      </span>
                      <input
                        type="radio"
                        name={`${uid}-collection`}
                        className="atc-input"
                        aria-labelledby={nameId}
                        checked={checked}
                        disabled={pending}
                        onChange={() => setSelected(c.id)}
                      />
                      <span className="atc-check" aria-hidden="true">
                        <span className="material-symbols-outlined">check</span>
                      </span>
                    </label>
                  </li>
                )
              })}
            </ul>
          )}

          <form onSubmit={handleCreate} className="atc-create">
            <label className="app-label" htmlFor={`${uid}-new`}>{t('library:createNew')}</label>
            <div className="atc-create-row">
              <input
                id={`${uid}-new`}
                className="app-input"
                value={name}
                maxLength={NAME_MAX}
                placeholder={t('library:nameLabel')}
                onChange={e => setName(e.target.value)}
              />
              <button type="submit" className="app-btn secondary atc-create-btn" disabled={pending}>
                <span className="material-symbols-outlined" aria-hidden="true">add</span>
                {t('bulkAddCreateAndAdd')}
              </button>
            </div>
            {isAdmin && (
              <label className="atc-switch">
                <input type="checkbox" checked={makePublic} onChange={e => setMakePublic(e.target.checked)} />
                <span className="atc-switch-track" aria-hidden="true" />
                {t('library:public')}
              </label>
            )}
            {formError && (
              <div className="app-alert error" role="alert" style={{ marginTop: 8, marginBottom: 0 }}>{formError}</div>
            )}
          </form>

          <button
            type="button"
            className="app-btn primary"
            style={{ marginTop: 12, width: '100%' }}
            disabled={selected == null || pending}
            aria-busy={pending}
            onClick={handleAdd}
          >
            {t('bulkAddSubmit', { count })}
          </button>
        </div>
      </div>
    </div>
  )
}
