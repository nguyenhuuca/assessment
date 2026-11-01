import React, { useEffect, useRef, useState } from 'react'
import { useAdminAccounts, useUpdateAccountRole, useUpdateAccountPermissions, useDeleteAccount, useBulkAccounts } from '../../hooks/useAdmin.js'
import BulkActionBar from './BulkActionBar.jsx'
import { useBulkSelection } from '../../hooks/useBulkSelection.js'
import { formatBulkResult } from '../../utils/bulkResult.js'
import { useConfirm } from '../../hooks/useConfirm.jsx'
import { isBusyFor } from '../../utils/mutationBusy.js'
import { useAuth } from '../../hooks/useAuth.js'
import { useTranslation } from 'react-i18next'
import { formatDate } from '../../i18n/format.js'
import { PERMISSIONS, AUTOPILOT_PERMISSIONS, hasPermission, togglePermission } from '../../utils/permissions.js'
import { isSuperAdmin } from '../../utils/roles.js'

// Mirrors the server: you can only manage accounts, and assign roles, strictly below your own
const ROLE_RANK = { USER: 0, CONTRIBUTOR: 1, ADMIN: 2, SUPER_ADMIN: 3 }
const SELECTABLE_ROLES = ['USER', 'CONTRIBUTOR', 'ADMIN']
const BADGE_CLASS = { SUPER_ADMIN: 'admin-badge-super', ADMIN: 'admin-badge-admin', CONTRIBUTOR: 'admin-badge-contributor' }
const rank = (role) => ROLE_RANK[role] ?? 0

export default function AdminAccountTable() {
  const { t } = useTranslation('admin')
  const [page, setPage] = useState(0)
  const { user } = useAuth()
  const myRank = rank(user?.role)
  const viewerIsSuper = isSuperAdmin(user?.role)
  const { data, isLoading } = useAdminAccounts(page)
  const updateRole    = useUpdateAccountRole()
  const updatePerms   = useUpdateAccountPermissions()
  const deleteAccount = useDeleteAccount()

  const pageData      = data?.data ?? data ?? {}
  const accounts      = pageData?.content ?? []
  const totalPages    = pageData?.totalPages ?? 0
  const totalElements = pageData?.totalElements ?? 0

  const [confirm, confirmDialog] = useConfirm()

  // The capability list mirrors what ROLE_ADMIN unlocks server-side: AdminController, AdminReportController,
  // AdminVideoSuggestionController, AdminVideoImportController (hasRole('ADMIN')); AdminJobController edit/run is SUPER_ADMIN only
  const confirmGrantAdmin = (message) => confirm({
    title: t('grantAdmin.title'),
    message,
    confirmLabel: t('grantAdmin.confirm'),
    children: (
      <ul style={{ margin: 0, paddingLeft: 20 }}>
        {t('grantAdmin.capabilities', { returnObjects: true }).map(c => <li key={c}>{c}</li>)}
      </ul>
    ),
  })

  const bulk = useBulkAccounts()
  const [bulkError, setBulkError] = useState('')
  const [notice, setNotice] = useState(null)
  const [bulkPerm, setBulkPerm] = useState('')
  const headerBox = useRef(null)
  // Same lock as the row controls: never yourself, only accounts ranked below you
  const isLocked = (a) => a.email === user?.email || myRank <= rank(a.role)
  const sel = useBulkSelection({
    pageIds: accounts.map(a => a.id),
    resetKey: page,
    max: 100,
    isSelectable: id => { const a = accounts.find(x => x.id === id); return !!a && !isLocked(a) },
  })
  useEffect(() => {
    if (headerBox.current) headerBox.current.indeterminate = sel.someSelected && !sel.allSelected
  })
  const bulkRoles = viewerIsSuper ? SELECTABLE_ROLES : ['USER', 'CONTRIBUTOR']
  const bulkPerms = viewerIsSuper ? [...PERMISSIONS, ...AUTOPILOT_PERMISSIONS] : PERMISSIONS

  function runBulk(body) {
    setBulkError('')
    setNotice(null)
    bulk.mutate({ ids: sel.selectedIds, ...body }, {
      onSuccess: res => {
        const r = res?.data ?? res ?? {}
        const counts = {}
        ;(r.skipped ?? []).forEach(x => { counts[x.code] = (counts[x.code] ?? 0) + 1 })
        setNotice({ summary: formatBulkResult(r, t), reasons: Object.entries(counts).map(([code, count]) => ({ code, count })) })
        sel.clear()
      },
      onError: e => setBulkError(e?.message || t('actionFailed')),
    })
  }

  async function bulkRole(role) {
    if (!role) return
    if (role === 'ADMIN' && !await confirmGrantAdmin(t('bulkGrantAdminMessage', { count: sel.count }))) return
    if (role === 'USER' && !await confirm({ title: t('bulkDemoteTitle'), message: t('bulkDemoteMessage', { count: sel.count }), confirmLabel: t('bulkDemoteConfirm'), danger: true })) return
    runBulk({ action: 'ROLE', role })
  }

  async function bulkDelete() {
    if (await confirm({ title: t('bulkDeleteAccountsTitle'), message: t('bulkDeleteAccountsMessage', { count: sel.count }), confirmLabel: t('delete'), danger: true })) runBulk({ action: 'DELETE' })
  }

  if (isLoading) return <div className="admin-loading"><div className="vid-spinner" /></div>

  return (
    <div className="admin-table-wrap">
      {confirmDialog}
      {bulkError && <div className="app-alert error" role="alert" style={{ margin: 12 }}>{bulkError}</div>}
      {notice && (
        <div className="app-alert success" role="status" style={{ margin: 12 }}>
          {notice.summary}
          {notice.reasons.length > 0 && (
            <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>
              {notice.reasons.map(r => (
                <li key={r.code}>{t('bulkSkippedLabel')}: {t(`bulk.reason.${r.code}`, { defaultValue: t('bulk.reason.unknown') })} ({r.count})</li>
              ))}
            </ul>
          )}
        </div>
      )}
      <BulkActionBar count={sel.count} onClear={sel.clear}>
        <select className="admin-status-select" aria-label={t('bulkAccountsRoleAria')} value="" disabled={bulk.isPending} onChange={e => bulkRole(e.target.value)}>
          <option value="">{t('bulkAccountsRolePlaceholder')}</option>
          {bulkRoles.map(r => <option key={r} value={r}>{r}</option>)}
        </select>
        <select className="admin-status-select" aria-label={t('bulkAccountsPermAria')} value={bulkPerm} onChange={e => setBulkPerm(e.target.value)}>
          <option value="">{t('bulkAccountsPermPlaceholder')}</option>
          {bulkPerms.map(p => <option key={p.key} value={p.key}>{p.key}</option>)}
        </select>
        <button className="app-btn secondary" disabled={!bulkPerm || bulk.isPending} onClick={() => runBulk({ action: 'GRANT_PERMISSION', permission: bulkPerm })}>{t('bulkGrant')}</button>
        <button className="app-btn secondary" disabled={!bulkPerm || bulk.isPending} onClick={() => runBulk({ action: 'REVOKE_PERMISSION', permission: bulkPerm })}>{t('bulkRevoke')}</button>
        <button className="app-btn danger" onClick={bulkDelete} disabled={bulk.isPending} aria-busy={bulk.isPending}>{t('delete')}</button>
      </BulkActionBar>
      {updatePerms.isError && <div className="app-alert error" role="alert" style={{ margin: 12 }}>{t('permissionsUpdateFailed')}</div>}
      <div className="admin-table-meta">{t('showing', { shown: accounts.length, total: totalElements.toLocaleString() })}</div>
      <div className="admin-table-scroll">
        <table className="admin-table">
          <thead>
            <tr>
              <th style={{ width: 36 }}>
                <input ref={headerBox} type="checkbox" aria-label={t('selectAll')} checked={sel.allSelected} onChange={sel.toggleAll} />
              </th>
              <th>{t('colEmail')}</th>
              <th>{t('colRole')}</th>
              <th>{t('colPermissions')}</th>
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
              // An ADMIN viewer toggles USER <-> CONTRIBUTOR; a SUPER_ADMIN picks any of USER / CONTRIBUTOR / ADMIN
              const nextRole = a.role === 'USER' ? 'CONTRIBUTOR' : 'USER'
              const showAutopilot = viewerIsSuper && (a.role === 'CONTRIBUTOR' || a.role === 'ADMIN')
              const lockReason = isSelf ? null
                : outranked ? t('lockLowerRole')
                : !viewerIsSuper && myRank <= rank(nextRole) ? t('lockAssignLower') : null
              const changeRole = async (role) => {
                if (role === a.role) return
                if (role === 'ADMIN' && !await confirmGrantAdmin(t('grantAdmin.message', { email: a.email }))) return
                updateRole.mutate({ id: a.id, role })
              }
              const roleLocked = isSelf || lockReason !== null
              const deleteLocked = isSelf || outranked
              // Same rule as the server: never yourself, only accounts ranked below you
              const permsLocked = isSelf || outranked
              const permsTitle = isSelf ? t('cannotChangeOwnPermissions') : outranked ? t('lockLowerRole') : t('permissionsHint')
              return (
                <tr key={a.id} className={`admin-table-row${sel.isSelected(a.id) ? ' selected' : ''}`}>
                  <td>
                    <input type="checkbox" aria-label={t('selectAccount', { email: a.email })} checked={sel.isSelected(a.id)} disabled={isSelf || outranked} onChange={() => sel.toggle(a.id)} />
                  </td>
                  <td>{a.email}</td>
                  <td>
                    <span className={`admin-badge ${BADGE_CLASS[a.role] ?? 'admin-badge-user'}`}>
                      {isSuper ? t('roleSuper') : a.role === 'CONTRIBUTOR' ? t('roleContributor') : a.role}
                    </span>
                  </td>
                  <td>
                    <div className="perm-chips" role="group" aria-label={`${t('colPermissions')} ${a.email}`}>
                      {PERMISSIONS.map(p => {
                        const on = hasPermission(a.permissions, p.bit)
                        return (
                          <button
                            key={p.key}
                            type="button"
                            className={`perm-chip${on ? ' on' : ''}`}
                            aria-pressed={on}
                            disabled={permsLocked || updatePerms.isPending}
                            title={permsTitle}
                            onClick={() => updatePerms.mutate({ id: a.id, permissions: togglePermission(a.permissions, p.bit) })}
                          >
                            {p.key}
                          </button>
                        )
                      })}
                    </div>
                    {showAutopilot && (
                      <div className="perm-chips" role="group" aria-label={`${t('colAutopilot')} ${a.email}`} style={{ marginTop: 4 }}>
                        {AUTOPILOT_PERMISSIONS.map(p => {
                          const on = hasPermission(a.permissions, p.bit)
                          return (
                            <button
                              key={p.key}
                              type="button"
                              className={`perm-chip${on ? ' on' : ''}`}
                              aria-pressed={on}
                              disabled={permsLocked || updatePerms.isPending}
                              title={t('autopilotHint')}
                              onClick={() => updatePerms.mutate({ id: a.id, permissions: togglePermission(a.permissions, p.bit) })}
                            >
                              {t(p.labelKey)}
                            </button>
                          )
                        })}
                      </div>
                    )}
                  </td>
                  <td className="admin-cell-muted">{a.mfaEnabled ? t('mfaOn') : t('mfaOff')}</td>
                  <td className="admin-cell-muted">{a.createdAt ? formatDate(a.createdAt) : '—'}</td>
                  <td>
                    <div className="admin-actions">
                      {viewerIsSuper ? (
                        <select
                          className="app-input"
                          aria-label={t('roleSelectLabel', { email: a.email })}
                          value={a.role}
                          disabled={roleLocked || isSuper}
                          aria-busy={isBusyFor(updateRole, a.id, x => x?.id)}
                          title={isSelf ? t('cannotChangeOwnRole') : (lockReason ?? '')}
                          onChange={e => changeRole(e.target.value)}
                        >
                          {(SELECTABLE_ROLES.includes(a.role) ? SELECTABLE_ROLES : [a.role, ...SELECTABLE_ROLES]).map(r => <option key={r} value={r}>{r}</option>)}
                        </select>
                      ) : (
                        <button
                          className="admin-action-btn"
                          disabled={roleLocked}
                          aria-busy={isBusyFor(updateRole, a.id, x => x?.id)}
                          title={isSelf ? t('cannotChangeOwnRole') : (lockReason ?? (a.role === 'CONTRIBUTOR' ? t('demoteContributor') : t('promoteContributor')))}
                          onClick={() => changeRole(nextRole)}
                        >
                          <span className="material-symbols-outlined" style={{ fontSize: 16 }}>
                            {a.role === 'CONTRIBUTOR' ? 'person_remove' : 'person_add'}
                          </span>
                        </button>
                      )}
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
      </div>
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
