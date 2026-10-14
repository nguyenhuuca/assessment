import i18n from '../i18n/index.js'

const CODES = ['SPAM', 'HARASSMENT', 'HATE_SPEECH', 'SEXUAL', 'VIOLENCE', 'PERSONAL_INFO', 'OTHER']

/** Translated label for a known reason code (resolved at call time, so it follows the current language). */
const translate = (code) => i18n.t(`moderation:${code}`)

/** `label` is a getter: it is looked up each time it is read, in the current language. */
export const REASONS = CODES.map(code => ({
  code,
  get label() { return translate(code) },
}))

/** Localized label for a moderation reason code; falls back to the raw code. */
export function reasonLabel(code) {
  return CODES.includes(code) ? translate(code) : (code ?? '')
}
