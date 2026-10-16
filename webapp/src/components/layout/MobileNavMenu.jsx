import React, { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

/**
 * Last slot of the mobile bottom nav: a settings button that opens a list of
 * secondary actions above the nav. Items: { key, icon, labelKey, onSelect }.
 * `active` marks the button when the current page lives in this menu.
 */
export default function MobileNavMenu({ items, active }) {
  const { t } = useTranslation('shell')
  const [open, setOpen] = useState(false)

  useEffect(() => {
    if (!open) return
    const onKey = e => { if (e.key === 'Escape') setOpen(false) }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  const highlighted = open || active
  return (
    <>
      <button
        className="mobile-nav-tab"
        aria-label={t('navMenu')}
        title={t('navMenu')}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen(o => !o)}
        style={{ color: highlighted ? 'var(--accent-cyan)' : 'var(--text-muted)' }}
      >
        <span className="material-symbols-outlined" aria-hidden="true" style={{
          fontSize: 26,
          fontVariationSettings: highlighted ? "'FILL' 1, 'wght' 400, 'GRAD' 0, 'opsz' 24" : undefined,
        }}>
          settings
        </span>
      </button>

      {open && (
        <>
          <div className="mobile-nav-menu-backdrop" onClick={() => setOpen(false)} />
          <div className="mobile-nav-menu" role="menu" aria-label={t('navMenu')}>
            {items.map(item => (
              <button
                key={item.key}
                role="menuitem"
                className="mobile-nav-menu-item"
                onClick={() => { setOpen(false); item.onSelect() }}
              >
                <span className="material-symbols-outlined" aria-hidden="true">{item.icon}</span>
                {t(item.labelKey)}
              </button>
            ))}
          </div>
        </>
      )}
    </>
  )
}
