import React, { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import ModalHeader from '../common/ModalHeader.jsx'

export const TITLE_MAX = 200
export const DESC_MAX = 2000

/** Edit the title and description of one video (admin Vault tab). Saving sends trimmed values. */
export default function VideoEditModal({ video, pending, error, onCancel, onSave }) {
  const { t } = useTranslation('admin')
  const [title, setTitle] = useState(video.title ?? '')
  const [description, setDescription] = useState(video.description ?? '')
  const inputRef = useRef(null)

  useEffect(() => { inputRef.current?.focus() }, [])

  useEffect(() => {
    const onKey = e => { if (e.key === 'Escape' && !pending) onCancel() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onCancel, pending])

  const trimmedTitle = title.trim()
  const trimmedDesc = description.trim()
  const unchanged = trimmedTitle === (video.title ?? '').trim() && trimmedDesc === (video.description ?? '').trim()
  const canSave = trimmedTitle.length > 0 && !unchanged && !pending

  return (
    <div className="app-modal-backdrop" onClick={pending ? undefined : onCancel}>
      <div className="app-modal" role="dialog" aria-modal="true"
        aria-label={t('editVideo.title')} onClick={e => e.stopPropagation()}>
        <ModalHeader icon="edit" title={t('editVideo.title')} onClose={onCancel} closeDisabled={pending} />
        <form onSubmit={e => { e.preventDefault(); if (canSave) onSave({ title: trimmedTitle, description: trimmedDesc }) }}>
          <div className="app-modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              {t('editVideo.titleLabel')}
              <input ref={inputRef} className="app-input" maxLength={TITLE_MAX} value={title}
                aria-label={t('editVideo.titleLabel')} onChange={e => setTitle(e.target.value)} />
            </label>
            <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              {t('editVideo.descLabel')}
              <textarea className="app-input" rows={5} maxLength={DESC_MAX} value={description}
                aria-label={t('editVideo.descLabel')} onChange={e => setDescription(e.target.value)} />
            </label>
            <span className="admin-cell-muted">{t('editVideo.descHint')}</span>
            {error && <div className="app-alert error" role="alert">{error}</div>}
          </div>
          <div className="app-modal-footer">
            <button type="button" className="app-btn secondary" onClick={onCancel} disabled={pending}>{t('common:cancel')}</button>
            <button type="submit" className="app-btn primary" disabled={!canSave} aria-busy={pending}>{t('common:save')}</button>
          </div>
        </form>
      </div>
    </div>
  )
}
