const UNITS = ['B', 'KB', 'MB', 'GB', 'TB']

// 1024-based human-readable size; '—' when the size is unknown.
export function formatBytes(bytes) {
  if (bytes === null || bytes === undefined) return '—'
  let v = bytes
  let i = 0
  while (v >= 1024 && i < UNITS.length - 1) { v /= 1024; i++ }
  return `${i === 0 ? v : v.toFixed(1)} ${UNITS[i]}`
}
