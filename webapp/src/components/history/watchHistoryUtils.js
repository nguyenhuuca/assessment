/** Group keys in display order. */
export const DAY_GROUPS = ['today', 'yesterday', 'earlier']

const startOfDay = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()

/** Which day bucket (local time) a watch timestamp falls in. */
export function dayGroupOf(watchedAt, now = new Date()) {
  const today = startOfDay(now)
  const yesterday = startOfDay(new Date(today - 12 * 3600 * 1000)) // robust to 23/25 h DST days
  const at = new Date(watchedAt).getTime()
  if (at >= today) return 'today'
  if (at >= yesterday) return 'yesterday'
  return 'earlier'
}

/** Splits an already-sorted list into [{ key, items }] for the non-empty buckets, keeping order. */
export function groupByDay(items, now = new Date()) {
  const buckets = { today: [], yesterday: [], earlier: [] }
  for (const it of items) buckets[dayGroupOf(it.watchedAt, now)].push(it)
  return DAY_GROUPS.filter(k => buckets[k].length).map(key => ({ key, items: buckets[key] }))
}

/** 83 -> "1:23", 3725 -> "1:02:05" */
export function formatClock(totalSec) {
  const s = Math.max(0, Math.floor(totalSec || 0))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = String(s % 60).padStart(2, '0')
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`
}
