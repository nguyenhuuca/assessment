import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'

// Every locales/<lng>/<namespace>.json is bundled automatically (namespace = file name).
const modules = import.meta.glob('./locales/*/*.json', { eager: true })

const resources = {}
for (const [path, mod] of Object.entries(modules)) {
  const [, lng, file] = path.match(/\.\/locales\/([^/]+)\/([^/]+)\.json$/)
  resources[lng] = resources[lng] || {}
  resources[lng][file] = mod.default ?? mod
}

export const SUPPORTED_LANGUAGES = ['vi', 'en']

function syncHtmlLang(lng) {
  if (typeof document !== 'undefined') {
    document.documentElement.lang = (lng || 'en').split('-')[0]
  }
}

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    initImmediate: false,
    supportedLngs: SUPPORTED_LANGUAGES,
    nonExplicitSupportedLngs: true,
    load: 'languageOnly',
    fallbackLng: 'en',
    defaultNS: 'common',
    interpolation: { escapeValue: false },
    react: { useSuspense: false },
    returnNull: false,
    detection: {
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: 'lang',
      caches: ['localStorage'],
    },
  })

syncHtmlLang(i18n.language)
i18n.on('languageChanged', syncHtmlLang)

export function setLanguage(lng) {
  return i18n.changeLanguage(lng)
}

export default i18n
