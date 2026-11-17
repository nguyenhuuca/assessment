const MAX_DIGITS = 12
const grouper = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 })

/** Keeps digits only, drops leading zeros and caps the length, e.g. "1,234,567 ₫" -> "1234567". */
export function toDigits(raw) {
  return String(raw ?? '').replace(/\D/g, '').replace(/^0+/, '').slice(0, MAX_DIGITS)
}

/** Groups a digit string with vi-VN separators, e.g. "1234567" -> "1.234.567". Empty stays empty. */
export function formatGrouped(digits) {
  const d = toDigits(digits)
  return d === '' ? '' : grouper.format(Number(d))
}
