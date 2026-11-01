import React from 'react'
import { useTranslation } from 'react-i18next'

/**
 * Shared header for `.app-modal` dialogs: icon tile, title, optional one-line subtitle and a close button.
 * `tone="danger"` tints the icon for destructive dialogs. Omit `onClose` for dialogs without an X.
 */
export default function ModalHeader({ icon, title, titleId, subtitle, tone, onClose, closeDisabled = false }) {
  const { t } = useTranslation('common')
  return (
    <div className="app-modal-header">
      {icon && (
        <span className={`app-modal-icon${tone === 'danger' ? ' danger' : ''}`} aria-hidden="true">
          <span className="material-symbols-outlined">{icon}</span>
        </span>
      )}
      <div className="app-modal-heading">
        <span className="app-modal-title" id={titleId}>{title}</span>
        {subtitle && <span className="app-modal-subtitle" title={subtitle}>{subtitle}</span>}
      </div>
      {onClose && (
        <button type="button" className="app-modal-close" onClick={onClose} disabled={closeDisabled} aria-label={t('close')}>
          <span className="material-symbols-outlined" style={{ fontSize: 20 }} aria-hidden="true">close</span>
        </button>
      )}
    </div>
  )
}
