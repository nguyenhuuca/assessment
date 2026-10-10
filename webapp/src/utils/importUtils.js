export const MAX_LINES = 20
export const MAX_SCHEDULE_DAYS = 30
/** The server worker ticks every 5 minutes on the clock; schedules must fall on those slots. */
export const SCHEDULE_STEP_MINUTES = 5
const VN_OFFSET = '+07:00' // Asia/Ho_Chi_Minh has no DST

const HOSTS = ['youtube.com', 'youtu.be', 'facebook.com', 'fb.watch']

export const ERROR_MESSAGES = {
  INVALID_URL: 'URL không hợp lệ',
  IMPORT_DISABLED: 'Tính năng import đang tắt trên server (VIDEO_IMPORT_ENABLED)',
  DUPLICATE_ACTIVE: 'Video này đang được nhập',
  UNSUPPORTED_URL: 'Nguồn video không được hỗ trợ',
  LOGIN_REQUIRED: 'Video yêu cầu đăng nhập hoặc riêng tư',
  TOO_LARGE: 'Video quá lớn hoặc quá dài',
  EXTRACTOR_ERROR: 'Không tải được video từ nguồn',
  TIMEOUT: 'Quá thời gian xử lý',
  CANCELLED: 'Đã huỷ',
  NOT_INSTALLED: 'Máy chủ chưa cài công cụ tải (yt-dlp/ffmpeg)',
  DRIVE_NOT_CONFIGURED: 'Chưa cấu hình Google Drive',
  DRIVE_AUTH_FAILED: 'Token Google Drive không hợp lệ hoặc đã hết hạn',
  DRIVE_UPLOAD_FAILED: 'Upload lên Google Drive thất bại',
  DISK_LOW: 'Ổ đĩa server không đủ dung lượng trống',
  INTERRUPTED: 'Bị gián đoạn do khởi động lại máy chủ',
}
export const GENERIC_ERROR = 'Đã xảy ra lỗi, vui lòng thử lại'

export const errorText = (code) => ERROR_MESSAGES[code] || GENERIC_ERROR

export function isSupportedUrl(raw) {
  let u
  try { u = new URL(raw) } catch { return false }
  if (u.protocol !== 'https:') return false
  const h = u.hostname.toLowerCase()
  return HOSTS.some(d => h === d || h.endsWith(`.${d}`))
}

/**
 * Parse textarea text. Returns { items: [{url,title?}], errors: [{line,message}] }.
 * `line` is the 1-based number of the physical line; blank lines are skipped.
 */
export function parseLines(text) {
  const items = []
  const errors = []
  const lines = (text || '').split(/\r?\n/)
  lines.forEach((rawLine, idx) => {
    const trimmed = rawLine.trim()
    if (!trimmed) return
    const sep = trimmed.indexOf('|')
    const url = (sep >= 0 ? trimmed.slice(0, sep) : trimmed).trim()
    const title = sep >= 0 ? trimmed.slice(sep + 1).trim() : ''
    if (!isSupportedUrl(url)) {
      errors.push({ line: idx + 1, message: 'URL phải là https và thuộc YouTube hoặc Facebook' })
      return
    }
    items.push(title ? { url, title } : { url })
  })
  if (items.length + errors.length > MAX_LINES) {
    errors.push({ line: 0, message: `Tối đa ${MAX_LINES} dòng mỗi lần` })
  }
  return { items, errors }
}

/** datetime-local value (Asia/Ho_Chi_Minh) -> ISO UTC string, or null if invalid. */
export function localToIso(value) {
  if (!value) return null
  const full = value.length === 16 ? `${value}:00` : value
  const d = new Date(`${full}${VN_OFFSET}`)
  return Number.isNaN(d.getTime()) ? null : d.toISOString()
}

/** Returns an error message or '' for a datetime-local value. */
export function validateSchedule(value, now = Date.now()) {
  const iso = localToIso(value)
  if (!iso) return 'Vui lòng chọn thời gian hẹn giờ'
  const t = new Date(iso).getTime()
  if (new Date(iso).getUTCMinutes() % SCHEDULE_STEP_MINUTES !== 0) {
    return `Phút phải chia hết cho ${SCHEDULE_STEP_MINUTES} (vd 09:00, 09:05, 09:10)`
  }
  if (t <= now) return 'Thời gian hẹn giờ phải ở tương lai'
  if (t > now + MAX_SCHEDULE_DAYS * 86400000) return `Chỉ được hẹn tối đa ${MAX_SCHEDULE_DAYS} ngày`
  return ''
}

export const isRunning = (j) => j?.status === 'DOWNLOADING' || j?.status === 'UPLOADING'

export function hasActive(resp, now = Date.now()) {
  const page = resp?.data ?? resp
  const jobs = page?.content ?? []
  return jobs.some(j =>
    isRunning(j) ||
    (j.status === 'PENDING' && (!j.scheduledAt || new Date(j.scheduledAt).getTime() <= now)))
}

export function formatTime(s) {
  return s ? new Date(s).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' }) : '—'
}

export function formatMb(bytes) {
  return `${((bytes || 0) / 1048576).toFixed(1)} MB`
}

export const PLATFORM_LABELS = { YOUTUBE: 'YouTube', FACEBOOK: 'Facebook' }
export const platformLabel = (p) => PLATFORM_LABELS[String(p || '').toUpperCase()] || p || '—'

export const canRunNow = (j) => j.status === 'PENDING'
export const canCancel = (j) => ['PENDING', 'DOWNLOADING', 'UPLOADING'].includes(j.status)
export const canRetry  = (j) => j.status === 'FAILED' || j.status === 'CANCELLED'
