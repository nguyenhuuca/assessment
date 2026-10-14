import React from 'react'
import { useTranslation } from 'react-i18next'
import { displayStatus } from './suggestionUtils.js'

// Reuses the admin badge colours: pending=yellow, approved=cyan, live=green, rejected=red.
const CLASS = { PENDING: 'pending', APPROVED: 'downloading', LIVE: 'done', REJECTED: 'failed' }

/** Status chip plus the reject reason (when rejected) for one suggestion. */
export default function SuggestionStatusChip({ suggestion }) {
  const { t } = useTranslation('suggestions')
  const status = displayStatus(suggestion)
  const reason = suggestion.rejectReason
  return (
    <span style={{ display: 'inline-flex', flexWrap: 'wrap', alignItems: 'center', gap: 6 }}>
      <span className={`admin-badge ${CLASS[status] ?? 'pending'}`}>{t(`status.${status}`, { defaultValue: status })}</span>
      {status === 'REJECTED' && reason && (
        <span className="admin-cell-muted">
          {t('rejectedBecause', { reason: t(`rejectReason.${reason}`, { defaultValue: reason }) })}
          {suggestion.rejectNote ? ` — ${suggestion.rejectNote}` : ''}
        </span>
      )}
    </span>
  )
}
