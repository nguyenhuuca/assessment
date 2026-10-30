import React, { useEffect, useLayoutEffect, useRef, useState } from 'react'

/**
 * ⋮ menu for a table row's secondary actions (pattern of WatchHistoryCard's menu).
 * The popover is position: fixed under the button, so the table's horizontal scroll box cannot clip it.
 * items: [{ key, icon, label, onSelect, danger?, busy? }]
 */
export default function RowActionsMenu({ label, items }) {
  const [open, setOpen] = useState(false)
  const [pos, setPos] = useState(null)
  const rootRef = useRef(null)
  const btnRef = useRef(null)

  useLayoutEffect(() => {
    if (!open) return
    const r = btnRef.current.getBoundingClientRect()
    setPos({ top: r.bottom + 4, right: window.innerWidth - r.right })
  }, [open])

  useEffect(() => {
    if (!open) return undefined
    const close = () => setOpen(false)
    const onDown = e => { if (!rootRef.current?.contains(e.target)) close() }
    const onKey = e => { if (e.key === 'Escape') close() }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    // A fixed popover would drift away from its row: close it instead
    window.addEventListener('scroll', close, true)
    window.addEventListener('resize', close)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
      window.removeEventListener('scroll', close, true)
      window.removeEventListener('resize', close)
    }
  }, [open])

  return (
    <div className="row-menu" ref={rootRef}>
      <button
        type="button"
        ref={btnRef}
        className="admin-action-btn"
        aria-label={label}
        title={label}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen(o => !o)}
      >
        <span className="material-symbols-outlined" style={{ fontSize: 16 }} aria-hidden="true">more_vert</span>
      </button>
      {open && (
        <div className="row-menu-popover" role="menu" style={pos ?? undefined}>
          {items.map(it => (
            <button
              key={it.key}
              type="button"
              role="menuitem"
              className={`row-menu-item${it.danger ? ' danger' : ''}`}
              aria-busy={it.busy || undefined}
              onClick={() => { setOpen(false); it.onSelect() }}
            >
              <span className="material-symbols-outlined" aria-hidden="true">{it.icon}</span>
              {it.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
