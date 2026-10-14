import React, { useState } from 'react'
import { useAdminAccounts, useUpdateAccountRole, useDeleteAccount } from '../../hooks/useAdmin.js'
import { useConfirm } from '../../hooks/useConfirm.jsx'
import { isBusyFor } from '../../utils/mutationBusy.js'
import { useAuth } from '../../hooks/useAuth.js'
import { useTranslation } from 'react-i18next'
import { formatDate } from '../../i18n/format.js'

// Mirrors the server: you can only manage accounts, and assign roles, strictly below your own
const ROLE_RANK = { USER: 0, ADMIN: 1, SUPER_ADMIN: 2 }
const rank = (role) => ROLE_RANK[role] ?? 0

export default function AdminAccountTable() {
  const { t } = useTranslation('admin')
  const [page, setPage] = useState(0)
  const { user } = useAuth()
  const myRank = rank(user?.role)
  const { data, isLoading } = useAdminAccounts(page)
  const updateRole    = useUpdateAccountRole()
  const deleteAccount = useDeleteAccount()

  const pageData      = data?.data ?? data ?? {}
  const accounts      = pageData?.content ?? []
  const totalPages    = pageData?.totalPages ?? 0
  const totalElements = pageData?.totalElements ?? 0

  const [confirm, confirmDialog] = useConfirm()

  if (isLoading) return <div className="admin-loading"><div className="vid-spinner" /></div>

  return (
    <div className="admin-table-wrap">
      {confirmDialog}
      <div className="admin-table-meta">{t('showing', { shown: accounts.length, total: totalElements.toLocaleString() })}</div>
      <table className="admin-table">
        <thead>
          <tr>
            <th>{t('colEmail')}</th>
            <th>{t('colRole')}</th>
            <th>{t('colMfa')}</th>
            <th>{t('colJoined')}</th>
            <th>{t('colActions')}</th>
          </tr>
        </thead>
        <tbody>
          {accounts.map(a => {
            const isSelf = a.email === user?.email
            const isSuper = a.role === 'SUPER_ADMIN'
            const outranked = myRank <= rank(a.role)
            const nextRole = a.role === 'ADMIN' ? 'USER' : 'ADMIN'
            const lockReason = isSelf ? null
              : outranked ? t('lockLowerRole')
              : myRank <= rank(nextRole) ? t('lockAssignLower') : null
            const roleLocked = isSelf || lockReason !== null
            const deleteLocked = isSelf || outranked
            return (
              <tr key={a.id} className="admin-table-row">
                <td>{a.email}</td>
                <td>
                  <span className={`admin-badge ${isSuper ? 'admin-badge-super' : a.role === 'ADMIN' ? 'admin-badge-admin' : 'admin-badge-user'}`}>
                    {isSuper ? t('roleSuper') : a.role}
                  </span>
                </td>
                <td className="admin-cell-muted">{a.mfaEnabled ? t('mfaOn') : t('mfaOff')}</td>
                <td className="admin-cell-muted">{a.createdAt ? formatDate(a.createdAt) : '—'}</td>
                <td>
                  <div className="admin-actions">
                    <button
                      className="admin-action-btn"
                      disabled={roleLocked}
                      aria-busy={isBusyFor(updateRole, a.id, x => x?.id)}
                      title={isSelf ? t('cannotChangeOwnRole') : (lockReason ?? (a.role === 'ADMIN' ? t('demote') : t('promote')))}
                      onClick={() => updateRole.mutate({ id: a.id, role: nextRole })}
                    >
                      <span className="material-symbols-outlined" style={{ fontSize: 16 }}>
                        {a.role === 'ADMIN' ? 'person_remove' : 'person_add'}
                      </span>
                    </button>
                    <button
                      className="admin-action-btn danger"
                      disabled={deleteLocked}
                      aria-busy={isBusyFor(deleteAccount, a.id)}
                      title={isSelf ? t('cannotDeleteSelf') : outranked ? t('lockLowerRole') : t('deleteAccount')}
                      onClick={async () => {
                        if (await confirm({ title: t('deleteAccount'), message: t('deleteAccountMessage', { email: a.email }), confirmLabel: t('delete'), danger: true })) deleteAccount.mutate(a.id)
                      }}
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
