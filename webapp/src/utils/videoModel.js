export const FUNNY_KEYWORDS = ['funny', 'hài', 'hài hước', 'comedy', 'vui', 'cười']

const THUMBNAIL_BASE = 'https://images.canh-labs.com'

/** Poster image for a Drive-backed video, or '' when it has no file id. */
export function thumbnailUrl(fileId) {
  return fileId ? `${THUMBNAIL_BASE}/${fileId}.jpg` : ''
}

/**
 * Stream URL for <video src>. Never put the JWT or guest token in the URL: the stream endpoint
 * does not read them, and query strings end up in nginx/Cloudflare logs (token leak, CWE-598)
 * and break shared caching of video bytes.
 */
export function buildStreamUrl(src) {
  if (!src) return src
  try {
    const url = new URL(src)
    url.searchParams.delete('token')
    url.searchParams.delete('guestToken')
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
    viewCount: raw.viewCount || 0,
    isPrivate: raw.isPrivate || raw.is_private || false,
    hlsUrl: raw.hlsUrl || null,
    fileId: raw.fileId || raw.file_id || null,
    category: determineCategory(raw.title, raw.desc || raw.description),
    poster: thumbnailUrl(raw.fileId),
  }
}
