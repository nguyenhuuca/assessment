import React, { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMarkAllRead, useMarkRead, useNotificationList, useUnreadCount } from '../../hooks/useNotifications.js'
import { useAuth } from '../../hooks/useAuth.js'
import { userCanReport } from '../../utils/roles.js'
import { notificationTitle } from '../../utils/notificationText.js'
import { formatTime } from '../comments/commentUtils.js'

const MAX_ITEMS = 10
const TABS = ['GENERAL', 'ADMIN']

const hasUnreadIn = list => (list.data?.content ?? []).some(n => !n.read)

export default function NotificationDropdown({ onClose, onOpenNotification, anchorRef }) {
  const { t } = useTranslation('notifications')
  const rootRef = useRef(null)
  const { user } = useAuth()
  // Admins get two tabs: admin-feature notifications (e.g. a closed report) apart from the rest
  const admin = userCanReport(user)
  const [tab, setTab] = useState('GENERAL')
  const general = useNotificationList(0, { category: admin ? 'GENERAL' : undefined })
  const adminList = useNotificationList(0, { category: 'ADMIN', enabled: admin })
  const { data, isLoading, isError } = admin && tab === 'ADMIN' ? adminList : general
  const { data: unread } = useUnreadCount()
  const markRead = useMarkRead()
  const markAllRead = useMarkAllRead()

  const items = (data?.content ?? []).slice(0, MAX_ITEMS)
  // Mark-all is global, so it follows the total unread count rather than the open tab
  const hasUnread = (unread?.count ?? 0) > 0 || items.some(n => !n.read)
  const tabDot = { GENERAL: hasUnreadIn(general), ADMIN: hasUnreadIn(adminList) }

  useEffect(() => {
    function onKey(e) {
      if (e.key === 'Escape') { e.stopPropagation(); onClose(true) }
    }
    function onPointer(e) {
      const target = e.target
      if (rootRef.current?.contains(target)) return
      if (anchorRef?.current?.contains(target)) return
      onClose(false)
    }
    document.addEventListener('keydown', onKey)
    document.addEventListener('mousedown', onPointer)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('mousedown', onPointer)
    }
  }, [onClose, anchorRef])

  function handleClick(n) {
    if (!n.read) markRead.mutate({ id: n.id, wasUnread: true })
    onOpenNotification?.(n)
    onClose()
  }

  return (
    <div className="notif-dropdown" ref={rootRef} role="dialog" aria-label={t('title')}>
      <div className="notif-dropdown-header">
        <span className="notif-dropdown-title">{t('title')}</span>
        <button
          type="button"
          className="comment-action-link"
          onClick={() => markAllRead.mutate()}
          disabled={!hasUnread || markAllRead.isPending}
          aria-busy={markAllRead.isPending}
        >
          {t('markAllRead')}
        </button>
      </div>

      {admin && (
        <div className="notif-tabs" role="tablist" aria-label={t('tabsLabel')}>
          {TABS.map(key => (
            <button key={key} type="button" role="tab" aria-selected={tab === key}
              className={`notif-tab${tab === key ? ' active' : ''}`} onClick={() => setTab(key)}>
              {t(key === 'ADMIN' ? 'tabAdmin' : 'tabGeneral')}
              {tabDot[key] && <span className="notif-tab-dot" aria-label={t('tabUnread')} />}
            </button>
          ))}
        </div>
      )}

      <div className="notif-dropdown-list" role={admin ? 'tabpanel' : undefined}>
        {isLoading ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: 24 }}>
            <div className="vid-spinner" />
          </div>
        ) : isError ? (
          <div className="notif-empty" role="alert">{t('loadFailed')}</div>
        ) : items.length === 0 ? (
          <div className="notif-empty">{t('empty')}</div>
        ) : (
          <ul className="notif-list">
            {items.map(n => (
              <li key={n.id}>
                <button
                  type="button"
                  className={`notif-item${n.read ? '' : ' unread'}`}
                  onClick={() => handleClick(n)}
                >
                  <span className="notif-item-title">{notificationTitle(n)}</span>
                  {n.type === 'COMMENT_REPLY' && n.snippet && (
                    <span className="notif-item-snippet">{n.snippet}</span>
                  )}
                  <span className="notif-item-time">{formatTime(n.updatedAt)}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
