import React, { useState } from 'react'
import { useAuth } from '../../hooks/useAuth.js'
import { useCreateCollection, useUpdateCollection } from '../../hooks/useCollections.js'
import { validateName, isPublic, NAME_MAX } from './collectionUtils.js'

/** Create (collection omitted) or rename / change visibility (collection given). */
export default function CollectionFormModal({ collection, onClose }) {
  const { user } = useAuth()
  const isAdmin = !!user?.isAdmin
  const editing = !!collection
  const create = useCreateCollection()
  const update = useUpdateCollection()
  const mutation = editing ? update : create
  const [name, setName] = useState(collection?.name ?? '')
  const [makePublic, setMakePublic] = useState(isPublic(collection))
  const [formError, setFormError] = useState('')

  function handleSubmit(e) {
    e.preventDefault()
    const { name: trimmed, error } = validateName(name)
    setFormError(error)
    if (error) return
    const visibility = isAdmin ? (makePublic ? 'PUBLIC' : 'PRIVATE') : undefined
    const done = { onSuccess: onClose }
    if (editing) update.mutate({ id: collection.id, name: trimmed, visibility }, done)
    else create.mutate({ name: trimmed, ...(visibility === 'PUBLIC' ? { visibility } : {}) }, done)
  }

  const title = editing ? 'Sửa collection' : 'Tạo collection'
  const apiError = mutation.error?.message

  return (
    <div className="app-modal-backdrop" onClick={e => { if (e.target === e.currentTarget && !mutation.isPending) onClose() }}>
      <form className="app-modal" role="dialog" aria-modal="true" aria-label={title} onSubmit={handleSubmit}>
        <div className="app-modal-header">
          <span className="app-modal-title">{title}</span>
          <button type="button" className="app-modal-close" onClick={onClose} aria-label="Close">
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>close</span>
          </button>
        </div>
        <div className="app-modal-body">
          {(formError || apiError) && <div className="app-alert error" role="alert">{formError || apiError}</div>}
          <label className="app-label" htmlFor="collection-name">Tên collection</label>
          <input
            id="collection-name"
            className="app-input"
            value={name}
            maxLength={NAME_MAX}
            autoFocus
            onChange={e => setName(e.target.value)}
          />
          {isAdmin && (
            <label style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 12, fontSize: 13 }}>
              <input type="checkbox" checked={makePublic} onChange={e => setMakePublic(e.target.checked)} />
              Công khai
            </label>
          )}
        </div>
        <div className="app-modal-footer">
          <button type="button" className="app-btn secondary" onClick={onClose} disabled={mutation.isPending}>Huỷ</button>
          <button type="submit" className="app-btn primary" disabled={mutation.isPending} aria-busy={mutation.isPending}>
            {editing ? 'Lưu' : 'Tạo'}
          </button>
        </div>
      </form>
    </div>
  )
}
