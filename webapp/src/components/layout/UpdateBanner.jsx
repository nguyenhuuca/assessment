import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNewVersion } from '../../pwa/useNewVersion.js'

// "A new version is available" notice. Dismissal is remembered per build id.
export default function UpdateBanner() {
  const { t } = useTranslation('shell')
  const { updateAvailable, latestBuild } = useNewVersion()
  const [dismissedBuild, setDismissedBuild] = useState(null)

  if (!updateAvailable || dismissedBuild === latestBuild) return null

  return (
    <div className="update-banner" role="status">
      <span>{t('updateAvailable')}</span>
      <button type="button" className="update-banner-reload" onClick={() => window.location.reload()}>
        {t('updateReload')}
      </button>
      <button type="button" className="update-banner-close" aria-label={t('updateDismiss')} onClick={() => setDismissedBuild(latestBuild)}>
        ×
      </button>
    </div>
  )
}
