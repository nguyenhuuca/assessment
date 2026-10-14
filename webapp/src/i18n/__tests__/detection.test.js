import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

async function freshI18n(navLang, stored) {
  vi.resetModules()
  localStorage.clear()
  if (stored) localStorage.setItem('lang', stored)
  vi.spyOn(window.navigator, 'languages', 'get').mockReturnValue([navLang])
  vi.spyOn(window.navigator, 'language', 'get').mockReturnValue(navLang)
  return (await import('../index.js')).default
}

describe('language detection', () => {
  beforeEach(() => vi.restoreAllMocks())
  afterEach(() => vi.restoreAllMocks())

  it('navigator vi-VN loads Vietnamese', async () => {
    const i18n = await freshI18n('vi-VN')
    expect(i18n.resolvedLanguage).toBe('vi')
    expect(i18n.t('logout')).toBe('Đăng xuất')
    expect(document.documentElement.lang).toBe('vi')
  })

  it('navigator en-US loads English', async () => {
    const i18n = await freshI18n('en-US')
    expect(i18n.resolvedLanguage).toBe('en')
    expect(i18n.t('logout')).toBe('Log out')
    expect(document.documentElement.lang).toBe('en')
  })

  it('unsupported navigator language falls back to English', async () => {
    const i18n = await freshI18n('fr-FR')
    expect(i18n.t('logout')).toBe('Log out')
  })

  it('stored localStorage language wins over navigator', async () => {
    const i18n = await freshI18n('en-US', 'vi')
    expect(i18n.resolvedLanguage).toBe('vi')
  })
})
