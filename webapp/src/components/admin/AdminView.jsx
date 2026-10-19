import React, { useState } from 'react'
import { useIsFetching, useQueryClient } from '@tanstack/react-query'
import { useAdminStats } from '../../hooks/useAdmin.js'
import { useTranslation } from 'react-i18next'
import AdminVideoTable from './AdminVideoTable.jsx'
import AdminAccountTable from './AdminAccountTable.jsx'
import AdminCommentTable from './AdminCommentTable.jsx'
import AdminImportPanel from './AdminImportPanel.jsx'
import DriveSyncPanel from './DriveSyncPanel.jsx'
import AdminJobsTab from './AdminJobsTab.jsx'
import AdminSuggestionTable from './AdminSuggestionTable.jsx'
import { useSuggestionPendingCount } from '../../hooks/useAdminSuggestions.js'

const TABS = ['VIDEO_VAULT', 'USER_ACCOUNTS', 'FLAGGED_LOGS', 'COMMENTS', 'SUGGESTIONS', 'IMPORT_VIDEO', 'DRIVE_SYNC', 'SCHEDULED_JOBS']

// Query key prefix (see hooks/useAdmin.js) that each tab reads from
const TAB_QUERY = {
  VIDEO_VAULT:   ['admin', 'videos'],
  FLAGGED_LOGS:  ['admin', 'videos'],
  USER_ACCOUNTS: ['admin', 'accounts'],
  COMMENTS:      ['admin', 'comments'],
  SUGGESTIONS:   ['admin', 'suggestions'],
  IMPORT_VIDEO:  ['admin', 'imports'],
  DRIVE_SYNC:    ['admin', 'drive-sync'],
  SCHEDULED_JOBS: ['admin', 'jobs'],
}

export default function AdminView({ initialTab }) {
  const { t } = useTranslation('admin')
  const [activeTab, setActiveTab] = useState(TABS.includes(initialTab) ? initialTab : 'VIDEO_VAULT')
  const qc = useQueryClient()
  const refreshing = useIsFetching({ queryKey: TAB_QUERY[activeTab] }) > 0

  function refresh() {
    qc.invalidateQueries({ queryKey: TAB_QUERY[activeTab] })
    qc.invalidateQueries({ queryKey: ['admin', 'stats'] })
  }
  const { data: countData } = useSuggestionPendingCount()
  const pending = Number(countData?.data ?? countData ?? 0) || 0
  const { data: statsData } = useAdminStats()
  const stats = statsData?.data ?? statsData ?? {}

  return (
    <div className="admin-root">
      <div className="admin-header">
        <div>
          <h1 className="admin-title">{t('pageTitle')}</h1>
          <p className="admin-subtitle">{t('subtitle', { total: stats.totalVideos?.toLocaleString() ?? '—' })}</p>
        </div>
      </div>

      <div className="admin-stat-cards">
        <div className="admin-stat-card">
          <span className="admin-stat-label">{t('statTotalVideos')}</span>
          <span className="admin-stat-value">{stats.totalVideos?.toLocaleString() ?? '—'}</span>
        </div>
        <div className="admin-stat-card">
          <span className="admin-stat-label">{t('statPendingReview')}</span>
          <span className="admin-stat-value" style={{ color: '#ffd600' }}>{stats.pendingCount ?? '—'}</span>
        </div>
        <div className="admin-stat-card">
          <span className="admin-stat-label">{t('statSecurityStatus')}</span>
          <span className="admin-stat-value" style={{ color: '#ff1744' }}>
            {stats.flaggedCount ?? '—'} <span style={{ fontSize: 11 }}>{t('statFlagged')}</span>
          </span>
        </div>
        <div className="admin-stat-card">
          <span className="admin-stat-label">{t('statRemovedComments')}</span>
          <span className="admin-stat-value" style={{ color: '#ff1744' }}>{stats.removedComments?.toLocaleString() ?? '—'}</span>
        </div>
        <div className="admin-stat-card">
          <span className="admin-stat-label">{t('statUserGrowth')}</span>
          <span className="admin-stat-value" style={{ color: 'var(--accent-cyan)' }}>{stats.totalUsers?.toLocaleString() ?? '—'}</span>
        </div>
      </div>

      <div className="admin-tabs">
        {/* Narrow screens: the tab row collapses into this menu (see .admin-tab-select in index.css) */}
        <select
          className="admin-tab-select"
          aria-label={t('sectionLabel')}
          value={activeTab}
          onChange={e => setActiveTab(e.target.value)}
        >
          {TABS.map(key => (
            <option key={key} value={key}>
              {t(`tabs.${key}`)}{key === 'SUGGESTIONS' && pending > 0 ? ` (${pending})` : ''}
            </option>
          ))}
        </select>
        {TABS.map(key => (
          <button
            key={key}
            className={`admin-tab${activeTab === key ? ' active' : ''}`}
            onClick={() => setActiveTab(key)}
          >
            {t(`tabs.${key}`)}
            {key === 'SUGGESTIONS' && pending > 0 && (
              <span className="admin-badge pending" style={{ marginLeft: 6 }} aria-label={t('sugg.pendingBadge', { count: pending })}>{pending}</span>
            )}
          </button>
        ))}
        <button
          className="admin-refresh-btn"
          onClick={refresh}
          disabled={refreshing}
          title={t('refreshTitle')}
          aria-label={t('refreshAria')}
        >
          <span className={`material-symbols-outlined${refreshing ? ' spinning' : ''}`} style={{ fontSize: 18 }}>refresh</span>
          {t('refresh')}
        </button>
      </div>

      <div className="admin-content">
        {activeTab === 'VIDEO_VAULT'   && <AdminVideoTable statusFilter={null} />}
        {activeTab === 'FLAGGED_LOGS'  && <AdminVideoTable statusFilter="FLAGGED" />}
        {activeTab === 'USER_ACCOUNTS' && <AdminAccountTable />}
        {activeTab === 'COMMENTS'      && <AdminCommentTable />}
        {activeTab === 'SUGGESTIONS'   && <AdminSuggestionTable />}
        {activeTab === 'IMPORT_VIDEO'  && <AdminImportPanel />}
        {activeTab === 'DRIVE_SYNC'    && <DriveSyncPanel />}
        {activeTab === 'SCHEDULED_JOBS' && <AdminJobsTab />}
      </div>
    </div>
  )
}
