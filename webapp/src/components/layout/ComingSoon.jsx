import React from 'react'

const PAGE_ICONS = {
  explore:  { icon: 'explore',       color: 'var(--accent-cyan)' },
  library:  { icon: 'video_library', color: 'var(--primary)' },
  history:  { icon: 'history',       color: '#f39c12' },
  settings: { icon: 'settings',      color: 'var(--text-muted)' },
}

export default function ComingSoon({ page = 'explore' }) {
  const { icon, color } = PAGE_ICONS[page] ?? PAGE_ICONS.explore

  return (
    <div style={{
      display: 'flex', flexDirection: 'column',
      alignItems: 'center', justifyContent: 'center',
      gap: 20, height: '100%',
      animation: 'fadeIn 0.25s ease',
      userSelect: 'none',
    }}>
      <span className="material-symbols-outlined" style={{
        fontSize: 72, color, opacity: 0.25,
        fontVariationSettings: "'FILL' 0, 'wght' 200, 'GRAD' 0, 'opsz' 48",
      }}>
        {icon}
      </span>
      <div style={{ textAlign: 'center' }}>
        <p style={{
          fontFamily: 'var(--font-brand)',
          fontSize: 28, letterSpacing: 4,
          color: 'var(--text)', marginBottom: 8,
        }}>
          Coming Soon
        </p>
        <p style={{ fontSize: 13, color: 'var(--text-muted)' }}>
          This section is under construction.
        </p>
      </div>
    </div>
  )
}
