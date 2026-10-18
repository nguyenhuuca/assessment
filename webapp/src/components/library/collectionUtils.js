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

/** Copy of `items` with the element at `from` moved to index `to` (same semantics as dnd-kit's arrayMove). */
export function moveItem(items, from, to) {
  const next = [...items]
  next.splice(to, 0, next.splice(from, 1)[0])
  return next
}

/** `afterVideoId` for the move API: the id now preceding the moved item (list after the move), or null at the top. */
export function moveAfterId(ids, fromIndex, toIndex) {
  if (toIndex <= 0) return null
  return moveItem(ids, fromIndex, toIndex)[toIndex - 1]
}

/** The same move by neighbour id, as the API sees it: `videoId` goes right after `afterVideoId` (null = top). Unknown ids leave the list as is. */
export function moveAfter(items, videoId, afterVideoId, getId = x => x) {
  const from = items.findIndex(x => getId(x) === videoId)
  if (from < 0) return items
  const rest = items.filter((_, i) => i !== from)
  const at = afterVideoId == null ? 0 : rest.findIndex(x => getId(x) === afterVideoId) + 1
  if (afterVideoId != null && at === 0) return items
  rest.splice(at, 0, items[from])
  return rest
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
