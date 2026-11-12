import React from 'react'
import { useTranslation } from 'react-i18next'
import { formatDate } from '../../i18n/format.js'
import { isPremiumActive } from '../../utils/premium.js'

export default function PremiumSection({ premiumUntil, onBuy }) {
  const { t } = useTranslation('settings')
  const active = isPremiumActive(premiumUntil)

  return (
    <section className="settings-card settings-col-4" data-testid="premium-section">
      <div className="settings-card-header">
        <div className="settings-card-icon cyan">
          <span className="material-symbols-outlined" style={{ fontSize: 22 }}>workspace_premium</span>
        </div>
        <h2 className="settings-card-title">{t('premium')}</h2>
      </div>
      <p className="settings-status-renew">
        {active ? t('premiumActiveUntil', { date: formatDate(premiumUntil) }) : t('premiumFree')}
      </p>
      {onBuy && (
        <button className="app-btn secondary" onClick={onBuy}>
          {active ? t('premiumRenew') : t('premiumBuy')}
        </button>
      )}
    </section>
  )
}
