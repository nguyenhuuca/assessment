import cronstrue from 'cronstrue'
import 'cronstrue/locales/vi'
import i18n from '../i18n/index.js'

// Spring 6-field cron (ADR-0023): second minute hour day-of-month month day-of-week
export const CRON_FIELDS = ['second', 'minute', 'hour', 'dayOfMonth', 'month', 'dayOfWeek']

/** Human text for a cron ("Every 15 minutes" / "Mỗi 15 phút"), or null when it does not parse. */
export function describeCron(expr, lang = i18n.language) {
  if (!expr || !expr.trim()) return null
  try {
    return cronstrue.toString(expr.trim(), {
      locale: (lang || 'en').startsWith('vi') ? 'vi' : 'en',
      use24HourTimeFormat: true,
      throwExceptionOnParseError: true,
    })
  } catch {
    return null
  }
}

/** Same shape rules the server applies before it calls Spring (6 fields, seconds = one number). */
export function cronShapeError(expr) {
  const fields = (expr || '').trim().split(/\s+/).filter(Boolean)
  if (fields.length !== 6) return 'fieldCount'
  if (!/^[0-5]?\d$/.test(fields[0])) return 'tooFrequent'
  return null
}

/** Index of the cron field under the caret, so the editor can highlight it (like crontab.guru). */
export function fieldIndexAt(expr, caret) {
  let index = 0
  for (let i = 0; i < Math.min(caret, expr.length); i++) {
    if (/\s/.test(expr[i]) && i > 0 && !/\s/.test(expr[i - 1])) index++
  }
  return Math.min(index, CRON_FIELDS.length - 1)
}
