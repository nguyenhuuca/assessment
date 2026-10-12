import React, { useEffect, useRef } from 'react'

// App-styled replacement for window.confirm (native dialogs ignore the theme and
// Chrome can silently suppress them, which turns the action into a no-op).
export default function ConfirmModal({
  title,
  message,
  children,
  confirmLabel = 'Xác nhận',
  cancelLabel = 'Huỷ',
  danger = false,
  pending = false,
  ariaLabel,
  onCancel,
  onConfirm,
}) {
  const confirmRef = useRef(null)

  useEffect(() => {
    confirmRef.current?.focus()
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
        <div className="app-modal-header">
          <span className="app-modal-title">{title}</span>
          <button className="app-modal-close" onClick={onCancel} disabled={pending} aria-label="Close">
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>close</span>
          </button>
        </div>
        <div className="app-modal-body">
          {message && <p className="admin-cell-muted" style={{ margin: 0, marginBottom: children ? 12 : 0 }}>{message}</p>}
          {children}
        </div>
        <div className="app-modal-footer">
          <button className="app-btn secondary" onClick={onCancel} disabled={pending}>{cancelLabel}</button>
          <button
            ref={confirmRef}
            className={`app-btn ${danger ? 'danger' : 'primary'}`}
            onClick={onConfirm}
            disabled={pending}
          >
            {pending ? 'Đang xử lý…' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}

