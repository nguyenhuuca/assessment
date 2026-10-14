import React from 'react'
import { useTranslation } from 'react-i18next'
import { setLanguage } from '../../i18n/index.js'

const OPTIONS = [
  { code: 'vi', labelKey: 'langShortVi', nameKey: 'vietnamese' },
  { code: 'en', labelKey: 'langShortEn', nameKey: 'english' },
]

export default function LanguageSwitcher() {
  const { t, i18n } = useTranslation()
  const current = (i18n.resolvedLanguage || i18n.language || 'en').split('-')[0]

  return (
    <div className="lang-switch" role="group" aria-label={t('language')}>
      {OPTIONS.map(({ code, labelKey, nameKey }) => (
        <button
          key={code}
          type="button"
          className={`lang-switch-btn${current === code ? ' active' : ''}`}
          aria-pressed={current === code}
          title={t(nameKey)}
          onClick={() => setLanguage(code)}
        >
          {t(labelKey)}
        </button>
      ))}
    </div>
  )
}
