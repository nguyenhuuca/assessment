import React, { useCallback, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useUnreadCount } from '../../hooks/useNotifications.js'
import { badgeLabel } from '../../utils/notificationText.js'
import NotificationDropdown from './NotificationDropdown.jsx'

export default function NotificationBell({ onOpenNotification, overlay = false }) {
  const { t } = useTranslation('notifications')
  const [open, setOpen] = useState(false)
  const btnRef = useRef(null)
  const { data } = useUnreadCount()
  const count = data?.count ?? 0
  const close = useCallback((restoreFocus = false) => {
    setOpen(false)
    if (restoreFocus === true) btnRef.current?.focus()
  }, [])

  return (
    <div className="notif-bell-wrap">
      <button
        type="button"
        ref={btnRef}
        className={`icon-btn notif-bell${overlay ? " mobile-overlay-icon" : ""}${open ? " active" : ""}`}
        aria-label={t('bellLabel', { count })}
        aria-haspopup="dialog"
        aria-expanded={open}
        title={t('title')}
        onClick={() => setOpen(v => !v)}
      >
        <span className="material-symbols-outlined" style={{ fontSize: 20 }}>notifications</span>
        {count > 0 && <span className="notif-badge" aria-hidden="true">{badgeLabel(count)}</span>}
      </button>
      {open && (
        <NotificationDropdown
          onClose={close}
          anchorRef={btnRef}
          onOpenNotification={onOpenNotification}
        />
      )}
    </div>
  )
}
