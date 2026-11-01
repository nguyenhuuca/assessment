import React from 'react'
import { useTranslation } from 'react-i18next'

export default function BulkActionBar({ count, onClear, children }) {
  const { t } = useTranslation('admin')
  if (!count) return null
  return (
    <div className="admin-bulk-bar" role="toolbar" aria-label={t('bulkActions')}>
      <span>{t('selectedBar', { count })}</span>
      {children}
      <button className="app-btn secondary" onClick={onClear}>{t('clearSelection')}</button>
    </div>
  )
}
