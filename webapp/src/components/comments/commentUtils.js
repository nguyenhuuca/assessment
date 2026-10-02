function hashCode(str) {
  let hash = 0
  for (let i = 0; i < str.length; i++) hash = str.charCodeAt(i) + ((hash << 5) - hash)
  return hash
}

export function getAvatarColor(email = '') {
  const colors = ['#e74c3c', '#3498db', '#2ecc71', '#f39c12', '#9b59b6', '#1abc9c', '#e67e22']
  return colors[Math.abs(hashCode(email)) % colors.length]
}

export function getInitials(email = '') {
  return email ? email[0].toUpperCase() : '?'
}

export function formatTime(dateStr) {
  if (!dateStr) return ''
  const diff = Date.now() - new Date(dateStr).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins}m ago`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24) return `${hrs}h ago`
  return `${Math.floor(hrs / 24)}d ago`
}

export const authorEmail = (c) => c.userEmail || c.email || c.userId || c.guestName || ''

/** Display name: email local part, or guest name. */
export const authorName = (c) => authorEmail(c).split('@')[0]

export const isPlaceholder = (c) => !!(c.removed || c.deleted)

/** All descendants of a root (any depth), flattened and sorted by createdAt ASC. */
export function flattenReplies(root) {
  const out = []
  const walk = (node) => {
    for (const r of node.replies || []) {
      out.push(r)
      walk(r)
    }
  }
  walk(root)
  return out
    .map((c, i) => ({ c, i }))
    .sort((a, b) => {
      const ta = new Date(a.c.createdAt || 0).getTime()
      const tb = new Date(b.c.createdAt || 0).getTime()
      return ta - tb || a.i - b.i
    })
    .map(x => x.c)
}

/** Number of non-placeholder comments in a tree, replies included. */
export function countVisible(list) {
  return (list || []).reduce(
    (n, c) => n + (isPlaceholder(c) ? 0 : 1) + countVisible(c.replies),
    0,
  )
}
