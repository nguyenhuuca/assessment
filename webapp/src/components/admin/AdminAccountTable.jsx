import React, { useState } from 'react'
import { useAdminAccounts, useUpdateAccountRole, useDeleteAccount } from '../../hooks/useAdmin.js'
import { useAuth } from '../../hooks/useAuth.js'

export default function AdminAccountTable() {
  const [page, setPage] = useState(0)
  const { user } = useAuth()
  const { data, isLoading } = useAdminAccounts(page)
  const updateRole    = useUpdateAccountRole()
  const deleteAccount = useDeleteAccount()

  const pageData      = data?.data ?? data ?? {}
  const accounts      = pageData?.content ?? []
  const totalPages    = pageData?.totalPages ?? 0
  const totalElements = pageData?.totalElements ?? 0

  if (isLoading) return <div className="admin-loading"><div className="vid-spinner" /></div>

  return (
    <div className="admin-table-wrap">
      <div className="admin-table-meta">SHOWING {accounts.length} OF {totalElements.toLocaleString()} ENTRIES</div>
      <table className="admin-table">
        <thead>
          <tr>
            <th>EMAIL</th>
            <th>ROLE</th>
            <th>MFA</th>
            <th>JOINED</th>
            <th>ACTIONS</th>
          </tr>
        </thead>
        <tbody>
          {accounts.map(a => {
            const isSelf = a.email === user?.email
            return (
              <tr key={a.id} className="admin-table-row">
                <td>{a.email}</td>
                <td>
                  <span className={`admin-badge ${a.role === 'ADMIN' ? 'admin-badge-admin' : 'admin-badge-user'}`}>
                    {a.role}
                  </span>
                </td>
                <td className="admin-cell-muted">{a.mfaEnabled ? 'ON' : 'OFF'}</td>
                <td className="admin-cell-muted">{a.createdAt ? new Date(a.createdAt).toLocaleDateString() : '—'}</td>
                <td>
                  <div className="admin-actions">
                    <button
                      className="admin-action-btn"
                      disabled={isSelf}
                      title={isSelf ? 'Cannot change own role' : (a.role === 'ADMIN' ? 'Demote to USER' : 'Promote to ADMIN')}
                      onClick={() => updateRole.mutate({ id: a.id, role: a.role === 'ADMIN' ? 'USER' : 'ADMIN' })}
                    >
                      <span className="material-symbols-outlined" style={{ fontSize: 16 }}>
                        {a.role === 'ADMIN' ? 'person_remove' : 'person_add'}
                      </span>
                    </button>
                    <button
                      className="admin-action-btn danger"
                      disabled={isSelf}
                      title={isSelf ? 'Cannot delete own account' : 'Delete account'}
                      onClick={() => { if (window.confirm(`Delete ${a.email}?`)) deleteAccount.mutate(a.id) }}
                    >
                      <span className="material-symbols-outlined" style={{ fontSize: 16 }}>delete</span>
                    </button>
                  </div>
                </td>
              </tr>
            )
          })}
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
