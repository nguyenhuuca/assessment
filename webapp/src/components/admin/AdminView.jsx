import React, { useState } from 'react'
import { useAdminStats } from '../../hooks/useAdmin.js'
import AdminVideoTable from './AdminVideoTable.jsx'
import AdminAccountTable from './AdminAccountTable.jsx'

const TABS = [
  { key: 'VIDEO_VAULT',   label: 'VIDEO_VAULT'   },
  { key: 'USER_ACCOUNTS', label: 'USER_ACCOUNTS' },
  { key: 'FLAGGED_LOGS',  label: 'FLAGGED_LOGS'  },
]

export default function AdminView() {
  const [activeTab, setActiveTab] = useState('VIDEO_VAULT')
  const { data: statsData } = useAdminStats()
  const stats = statsData?.data ?? statsData ?? {}

  return (
    <div className="admin-root">
      <div className="admin-header">
        <div>
          <h1 className="admin-title">CONTENT_MANAGER</h1>
          <p className="admin-subtitle">Reviewing {stats.totalVideos?.toLocaleString() ?? '—'} total cinematic assets</p>
        </div>
      </div>

      <div className="admin-tabs">
        {TABS.map(t => (
          <button
            key={t.key}
            className={`admin-tab${activeTab === t.key ? ' active' : ''}`}
            onClick={() => setActiveTab(t.key)}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className="admin-content">
        {activeTab === 'VIDEO_VAULT'   && <AdminVideoTable statusFilter={null} />}
        {activeTab === 'FLAGGED_LOGS'  && <AdminVideoTable statusFilter="FLAGGED" />}
        {activeTab === 'USER_ACCOUNTS' && <AdminAccountTable />}
      </div>

      <div className="admin-stat-cards">
        <div className="admin-stat-card">
          <span className="admin-stat-label">TOTAL_VIDEOS</span>
          <span className="admin-stat-value">{stats.totalVideos?.toLocaleString() ?? '—'}</span>
        </div>
        <div className="admin-stat-card">
          <span className="admin-stat-label">PENDING_REVIEW</span>
          <span className="admin-stat-value" style={{ color: '#ffd600' }}>{stats.pendingCount ?? '—'}</span>
        </div>
        <div className="admin-stat-card">
          <span className="admin-stat-label">SECURITY_STATUS</span>
          <span className="admin-stat-value" style={{ color: '#ff1744' }}>
            {stats.flaggedCount ?? '—'} <span style={{ fontSize: 11 }}>FLAGGED</span>
          </span>
        </div>
        <div className="admin-stat-card">
          <span className="admin-stat-label">USER_GROWTH</span>
          <span className="admin-stat-value" style={{ color: 'var(--accent-cyan)' }}>{stats.totalUsers?.toLocaleString() ?? '—'}</span>
        </div>
      </div>
    </div>
  )
}
