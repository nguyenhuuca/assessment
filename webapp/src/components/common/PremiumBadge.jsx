import React from 'react'
import { useTranslation } from 'react-i18next'
import { isPremiumActive } from '../../utils/premium.js'

export default function PremiumBadge({ premiumUntil }) {
  const { t } = useTranslation('settings')
  if (!isPremiumActive(premiumUntil)) return null
  return (
    <span
      className="icon-btn"
      role="img"
      aria-label={t('premium')}
      title={t('premium')}
      style={{ color: '#f5b301', cursor: 'default' }}
    >
      <span className="material-symbols-outlined" style={{ fontSize: 20 }}>workspace_premium</span>
    </span>
  )
}
