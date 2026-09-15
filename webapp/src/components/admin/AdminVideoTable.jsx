import React, { useState } from 'react'
import { useAdminVideos, useUpdateVideoStatus, useUpdateVideoPriority, useDeleteVideo } from '../../hooks/useAdmin.js'

const STATUS_COLORS = {
  PUBLISHED: '#00c853',
  PENDING:   '#ffd600',
  FLAGGED:   '#ff1744',
}

const STATUSES = ['PUBLISHED', 'PENDING', 'FLAGGED']

// Small inline indicator so admins can see whether a field edit actually saved.
// Reserves a fixed-size slot at all times so the icon appearing/disappearing
// doesn't resize the surrounding cell/table.
function SaveIndicator({ state }) {
  let icon = null
  let color = 'transparent'
  let title
  let spin = false
  if (state === 'saving') {
    icon = 'sync'; color = '#adaaaa'; title = 'Saving…'; spin = true
  } else if (state === 'saved') {
    icon = 'check_circle'; color = '#00c853'; title = 'Saved'
  } else if (state === 'error') {
    icon = 'error'; color = '#ff1744'; title = 'Failed to save'
  }

  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: 16,
        height: 16,
        flexShrink: 0,
      }}
    >
      {icon && (
        <span
          className={`material-symbols-outlined${spin ? ' admin-save-spin' : ''}`}
          style={{ fontSize: 16, color }}
          title={title}
        >
          {icon}
        </span>
      )}
    </span>
  )
}

export default function AdminVideoTable({ statusFilter }) {
  const [page, setPage] = useState(0)
  const [saveState, setSaveState] = useState({}) // { [`${id}:${field}`]: 'saving' | 'saved' | 'error' }
  const { data, isLoading } = useAdminVideos(page, statusFilter)
  const updateStatus   = useUpdateVideoStatus()
  const updatePriority = useUpdateVideoPriority()
  const deleteVideo    = useDeleteVideo()

  const pageData      = data?.data ?? data ?? {}
  const videos        = pageData?.content ?? []
  const totalPages    = pageData?.totalPages ?? 0
  const totalElements = pageData?.totalElements ?? 0

  function markSaving(key) {
    setSaveState(prev => ({ ...prev, [key]: 'saving' }))
  }
  function markResult(key, ok) {
    setSaveState(prev => ({ ...prev, [key]: ok ? 'saved' : 'error' }))
    if (ok) {
      setTimeout(() => {
        setSaveState(prev => {
          const { [key]: _removed, ...rest } = prev
          return rest
        })
      }, 2000)
    }
  }

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
            <th>PRIORITY</th>
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
              <td>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <input
                    type="number"
                    className="admin-status-select"
                    style={{ width: 64 }}
                    min={0}
                    max={9999}
                    key={`${v.id}-${v.priority ?? 0}`}
                    defaultValue={v.priority ?? 0}
                    title="Higher number shows first in the public feed"
                    onBlur={e => {
                      const next = Number(e.target.value)
                      if (!Number.isFinite(next) || next === (v.priority ?? 0)) return
                      const key = `${v.id}:priority`
                      markSaving(key)
                      updatePriority.mutate({ id: v.id, priority: next }, {
                        onSuccess: () => markResult(key, true),
                        onError: () => markResult(key, false),
                      })
                    }}
                  />
                  <SaveIndicator state={saveState[`${v.id}:priority`]} />
                </div>
              </td>
              <td className="admin-cell-muted">{v.viewCount?.toLocaleString() ?? 0} views</td>
              <td>
                <div className="admin-actions">
                  <select
                    className="admin-status-select"
                    value={v.status}
                    onChange={e => {
                      const key = `${v.id}:status`
                      markSaving(key)
                      updateStatus.mutate({ id: v.id, status: e.target.value }, {
                        onSuccess: () => markResult(key, true),
                        onError: () => markResult(key, false),
                      })
                    }}
                  >
                    {STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
                  </select>
                  <SaveIndicator state={saveState[`${v.id}:status`]} />
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
