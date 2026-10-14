import i18n from './index.js'

const lang = () => i18n.language || 'en'

const UNITS = [
  ['year', 365 * 24 * 3600],
  ['month', 30 * 24 * 3600],
  ['week', 7 * 24 * 3600],
  ['day', 24 * 3600],
  ['hour', 3600],
  ['minute', 60],
  ['second', 1],
]

export function relativeTime(date, now = Date.now()) {
  const diffSec = Math.round((new Date(date).getTime() - now) / 1000)
  const abs = Math.abs(diffSec)
  const rtf = new Intl.RelativeTimeFormat(lang(), { numeric: 'auto' })
  for (const [unit, secs] of UNITS) {
    if (abs >= secs || unit === 'second') {
      return rtf.format(Math.trunc(diffSec / secs), unit)
    }
  }
  return ''
}

export function compactNumber(n) {
  return new Intl.NumberFormat(lang(), { notation: 'compact', maximumFractionDigits: 1 }).format(n ?? 0)
}

export function formatDate(date, options = { dateStyle: 'medium' }) {
  return new Intl.DateTimeFormat(lang(), options).format(new Date(date))
}
