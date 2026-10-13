export const NAME_MAX = 60
export const WATCH_LATER_NAME = 'Xem sau'

/** Trimmed name or an error message (1–60 chars). */
export function validateName(raw) {
  const name = (raw || '').trim()
  if (!name) return { name, error: 'Vui lòng nhập tên collection' }
  if (name.length > NAME_MAX) return { name, error: `Tên tối đa ${NAME_MAX} ký tự` }
  return { name, error: '' }
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
