export const TITLE_MAX = 200
export const NOTE_MAX = 300

const HOSTS = ['youtube.com', 'youtu.be', 'facebook.com', 'fb.watch']

/** Effective display status: an approved suggestion that is already on the app counts as LIVE. */
export function displayStatus(s) {
  return s?.status === 'APPROVED' && s.live ? 'LIVE' : (s?.status ?? 'PENDING')
}

/** Returns an error key ('urlRequired' | 'urlInvalid') or '' when the URL is an https YouTube/Facebook link. */
export function validateSuggestionUrl(raw) {
  const value = (raw ?? '').trim()
  if (!value) return 'urlRequired'
  let url
  try { url = new URL(value) } catch { return 'urlInvalid' }
  if (url.protocol !== 'https:') return 'urlInvalid'
  const host = url.hostname.toLowerCase()
  return HOSTS.some(h => host === h || host.endsWith(`.${h}`)) ? '' : 'urlInvalid'
}

/** Maps a backend error code (error.suggestion.*) to a `suggestions` namespace key, or null when unknown. */
export function errorKeyFor(code) {
  switch (code) {
    case 'error.suggestion.duplicate': return 'errorDuplicate'
    case 'error.suggestion.dailyLimit': return 'errorDailyLimit'
    case 'error.suggestion.invalidUrl': return 'urlInvalid'
    default: return null
  }
}
