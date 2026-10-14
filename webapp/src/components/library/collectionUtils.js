import i18n from '../../i18n/index.js'

export const NAME_MAX = 60
export const WATCH_LATER_NAME = 'Xem sau'

/** Trimmed name or a localized error message (1–60 chars). */
export function validateName(raw) {
  const name = (raw || '').trim()
  if (!name) return { name, error: i18n.t('library:nameRequired') }
  if (name.length > NAME_MAX) return { name, error: i18n.t('library:nameTooLong', { max: NAME_MAX }) }
  return { name, error: '' }
}

/** Display name: the default collection is shown in the current language; others as stored. */
export function collectionName(collection) {
  if (!collection) return ''
  return collection.isDefault ? i18n.t('library:watchLater') : collection.name
}

export function isPublic(collection) {
  return collection?.visibility === 'PUBLIC'
}

/** "Xem sau" first, then the rest in server order. */
export function pinDefaultFirst(collections = []) {
  return [...collections.filter(c => c.isDefault), ...collections.filter(c => !c.isDefault)]
}

/** Accent- and case-insensitive ("hai huoc" matches "Hài hước"). */
export function normalizeForSearch(value) {
  return (value || '')
    .normalize('NFKD')
    .replace(/\p{M}/gu, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLocaleLowerCase()
}
