import { useEffect, useRef } from 'react'
import i18n, { SUPPORTED_LANGUAGES } from '../i18n/index.js'
import { useAuth } from './useAuth.js'
import { useSettings, useUpdateSettings } from './useSettings.js'

const short = (lng) => (lng || 'en').split('-')[0]

/**
 * Keeps the UI language and the server-side `user_settings.locale` in sync while logged in.
 * - Once per login, a stored server locale wins over the local one (no PATCH back).
 * - Any later language change (header switcher, settings page) is PATCHed once.
 * `knownServerLocale` tracks what the server holds so applying it never loops back as a PATCH.
 */
export function useLocaleSync() {
  const { isLoggedIn } = useAuth()
  const { data: settings } = useSettings({ enabled: isLoggedIn })
  const { mutate } = useUpdateSettings()
  const knownServerLocale = useRef(null)
  const applied = useRef(false)

  useEffect(() => {
    if (!isLoggedIn) {
      applied.current = false
      knownServerLocale.current = null
      return
    }
    if (applied.current || !settings) return
    applied.current = true
    const locale = settings.locale
    if (!SUPPORTED_LANGUAGES.includes(locale)) return
    knownServerLocale.current = locale
    if (short(i18n.language) !== locale) i18n.changeLanguage(locale)
  }, [isLoggedIn, settings])

  useEffect(() => {
    if (!isLoggedIn) return undefined
    function onChanged(lng) {
      const next = short(lng)
      if (!applied.current || next === knownServerLocale.current) return
      knownServerLocale.current = next
      mutate({ locale: next })
    }
    i18n.on('languageChanged', onChanged)
    return () => i18n.off('languageChanged', onChanged)
  }, [isLoggedIn, mutate])
}
