import React, { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import ModalHeader from './ModalHeader.jsx'

// App-styled replacement for window.confirm (native dialogs ignore the theme and
// Chrome can silently suppress them, which turns the action into a no-op).
export default function ConfirmModal({
  title,
  message,
  children,
  confirmLabel,
  cancelLabel,
  danger = false,
  icon,
  pending = false,
  ariaLabel,
  onCancel,
  onConfirm,
}) {
  const { t } = useTranslation(['shell', 'common'])
  const confirmRef = useRef(null)

  // Focus once on open: onCancel is usually a new arrow each parent render, and refocusing then
  // would steal focus from an input inside the modal after every keystroke
  useEffect(() => { confirmRef.current?.focus() }, [])

  useEffect(() => {
    const onKey = e => { if (e.key === 'Escape' && !pending) onCancel() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onCancel, pending])

  return (
    <div className="app-modal-backdrop" onClick={pending ? undefined : onCancel}>
      <div
        className="app-modal"
        role="dialog"
        aria-modal="true"
        aria-label={ariaLabel || title}
        onClick={e => e.stopPropagation()}
      >
        <ModalHeader icon={icon || (danger ? 'warning' : 'help')} tone={danger ? 'danger' : undefined} title={title} onClose={onCancel} closeDisabled={pending} />
        <div className="app-modal-body">
          {message && <p className="admin-cell-muted" style={{ margin: 0, marginBottom: children ? 12 : 0 }}>{message}</p>}
          {children}
        </div>
        <div className="app-modal-footer">
          <button className="app-btn secondary" onClick={onCancel} disabled={pending}>{cancelLabel ?? t('common:cancel')}</button>
          <button
            ref={confirmRef}
            className={`app-btn ${danger ? 'danger' : 'primary'}`}
            onClick={onConfirm}
            disabled={pending}
            aria-busy={pending}
          >
            {confirmLabel ?? t('shell:confirm')}
          </button>
        </div>
      </div>
    </div>
  )
}

