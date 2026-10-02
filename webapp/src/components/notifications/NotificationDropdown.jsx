import React, { useEffect, useRef } from 'react'
import { useMarkAllRead, useMarkRead, useNotificationList } from '../../hooks/useNotifications.js'
import { notificationTitle } from '../../utils/notificationText.js'
import { formatTime } from '../comments/commentUtils.js'

const MAX_ITEMS = 10

export default function NotificationDropdown({ onClose, onOpenNotification, anchorRef }) {
  const rootRef = useRef(null)
  const { data, isLoading, isError } = useNotificationList(0)
  const markRead = useMarkRead()
  const markAllRead = useMarkAllRead()

  const items = (data?.content ?? []).slice(0, MAX_ITEMS)
  const hasUnread = items.some(n => !n.read)

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
    <div className="notif-dropdown" ref={rootRef} role="dialog" aria-label="Thông báo">
      <div className="notif-dropdown-header">
        <span className="notif-dropdown-title">Thông báo</span>
        <button
          type="button"
          className="comment-action-link"
          onClick={() => markAllRead.mutate()}
          disabled={!hasUnread || markAllRead.isPending}
        >
          Đánh dấu đã đọc tất cả
        </button>
      </div>

      <div className="notif-dropdown-list">
        {isLoading ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: 24 }}>
            <div className="vid-spinner" />
          </div>
        ) : isError ? (
          <div className="notif-empty" role="alert">Không tải được thông báo</div>
        ) : items.length === 0 ? (
          <div className="notif-empty">Chưa có thông báo</div>
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
