import React, { useState } from 'react'
import { useAdminVideos, useUpdateVideoStatus, useDeleteVideo } from '../../hooks/useAdmin.js'

const STATUS_COLORS = {
  PUBLISHED: '#00c853',
  PENDING:   '#ffd600',
  FLAGGED:   '#ff1744',
}

const STATUSES = ['PUBLISHED', 'PENDING', 'FLAGGED']

export default function AdminVideoTable({ statusFilter }) {
  const [page, setPage] = useState(0)
  const { data, isLoading } = useAdminVideos(page, statusFilter)
  const updateStatus = useUpdateVideoStatus()
  const deleteVideo  = useDeleteVideo()

  const pageData      = data?.data ?? data ?? {}
  const videos        = pageData?.content ?? []
  const totalPages    = pageData?.totalPages ?? 0
  const totalElements = pageData?.totalElements ?? 0

  if (isLoading) return <div className="admin-loading"><div className="vid-spinner" /></div>

  return (
    <div className="admin-table-wrap">
      <div className="admin-table-meta">SHOWING {videos.length} OF {totalElements.toLocaleString()} ENTRIES</div>
      <table className="admin-table">
        <thead>
          <tr>
            <th>ASSET</th>
            <th>CREATOR</th>
            <th>STATUS</th>
            <th>METRICS</th>
            <th>ACTIONS</th>
          </tr>
        </thead>
        <tbody>
          {videos.map(v => (
            <tr key={v.id} className="admin-table-row">
              <td>
                <div className="admin-asset-cell">
                  {v.thumbnailPath && (
                    <img src={v.thumbnailPath} alt="" className="admin-thumb" />
                  )}
                  <span className="admin-asset-title">{v.title || 'Untitled'}</span>
                </div>
              </td>
              <td className="admin-cell-muted">{v.creatorEmail || '—'}</td>
              <td>
                <span
                  className="admin-badge"
                  style={{
                    background: (STATUS_COLORS[v.status] ?? '#888') + '22',
                    color: STATUS_COLORS[v.status] ?? '#888',
                    border: `1px solid ${STATUS_COLORS[v.status] ?? '#888'}`,
                  }}
                >
                  {v.status}
                </span>
              </td>
              <td className="admin-cell-muted">{v.viewCount?.toLocaleString() ?? 0} views</td>
              <td>
                <div className="admin-actions">
                  <select
                    className="admin-status-select"
                    value={v.status}
                    onChange={e => updateStatus.mutate({ id: v.id, status: e.target.value })}
                  >
                    {STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
                  </select>
                  <button
                    className="admin-action-btn danger"
                    onClick={() => { if (window.confirm('Delete this video?')) deleteVideo.mutate(v.id) }}
                    title="Delete"
                  >
                    <span className="material-symbols-outlined" style={{ fontSize: 16 }}>delete</span>
                  </button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {totalPages > 1 && (
        <div className="admin-pagination">
          <button className="admin-page-btn" disabled={page === 0} onClick={() => setPage(p => p - 1)}>‹</button>
          {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => (
            <button key={i} className={`admin-page-btn${page === i ? ' active' : ''}`} onClick={() => setPage(i)}>{i + 1}</button>
          ))}
          <button className="admin-page-btn" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>›</button>
        </div>
      )}
    </div>
  )
}
