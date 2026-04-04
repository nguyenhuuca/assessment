export const FUNNY_KEYWORDS = ['funny', 'hài', 'hài hước', 'comedy', 'vui', 'cười']

export function buildStreamUrl(src) {
  if (!src) return src
  try {
    const url = new URL(src)
    const jwt = localStorage.getItem('jwt')
    const guest = localStorage.getItem('guestToken')
    if (jwt)   url.searchParams.set('token', jwt)
    if (guest) url.searchParams.set('guestToken', guest)
    return url.toString()
  } catch { return src }
}

export function determineCategory(title = '', desc = '') {
  const text = (title + ' ' + desc).toLowerCase()
  return FUNNY_KEYWORDS.some(kw => text.includes(kw)) ? 'funny' : 'regular'
}

export function mapApiVideo(raw) {
  return {
    id: raw.id,
    userShared: raw.userShared || raw.user_shared,
    title: raw.title || '',
    src: raw.embedLink || raw.src || raw.url || '',
    desc: raw.desc || raw.description || '',
    upvotes: raw.upvotes || 0,
    downvotes: raw.downvotes || 0,
    isPrivate: raw.isPrivate || raw.is_private || false,
    fileId: raw.fileId || raw.file_id || null,
    category: determineCategory(raw.title, raw.desc || raw.description),
    poster: raw.fileId ? `https://images.canh-labs.com/${raw.fileId}.jpg` : '',
  }
}
