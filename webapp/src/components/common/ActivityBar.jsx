import React, { useEffect, useState } from 'react'
import { useIsMutating } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

// Global "talking to the backend" indicator for every React Query mutation.
// Delayed so instant responses don't flash it.
export default function ActivityBar({ delayMs = 150 }) {
  const { t } = useTranslation('shell')
  const active = useIsMutating() > 0
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    if (!active) return undefined
    const t = setTimeout(() => setVisible(true), delayMs)
    return () => {
      clearTimeout(t)
      setVisible(false)
    }
  }, [active, delayMs])

  return visible ? <div className="activity-bar" role="progressbar" aria-label={t('processing')} /> : null
}
