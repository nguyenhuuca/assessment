import { localToIso } from './importUtils.js'

const MINUTE = 60000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

export const TIME_PRESETS = {
  '1h': HOUR,
  '12h': 12 * HOUR,
  '24h': DAY,
  '7d': 7 * DAY,
  '30d': 30 * DAY,
}

export const DEFAULT_TIME_FILTER = { mode: 'relative', preset: '12h' }

/**
 * Turns a time filter into `GET /admin/drive-sync/files` query params.
 * - { mode: 'all' } -> {}
 * - { mode: 'relative', preset } -> { from: now - preset }
 * - { mode: 'custom', from, to } (datetime-local, VN time) -> `to` gets +1 min so its minute is included
 * Returns { params, error }; from > to gives { params: null, error: 'admin:timeRangeInvalid' }.
 */
export function toRangeParams(filter, now = Date.now()) {
  if (filter?.mode === 'relative') {
    const ms = TIME_PRESETS[filter.preset]
    return { params: ms ? { from: new Date(now - ms).toISOString() } : {}, error: '' }
  }
  if (filter?.mode === 'custom') {
    const from = localToIso(filter.from)
    const toIso = localToIso(filter.to)
    // both are toISOString() output, so string order is time order
    if (from && toIso && from > toIso) {
      return { params: null, error: 'admin:timeRangeInvalid' }
    }
    const toMs = toIso ? new Date(toIso).getTime() + MINUTE : null
    const params = {}
    if (from) params.from = from
    if (toMs !== null) params.to = new Date(toMs).toISOString()
    return { params, error: '' }
  }
  return { params: {}, error: '' }
}
