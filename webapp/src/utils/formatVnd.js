const fmt = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 })

/** Formats an amount in VND, e.g. 50000 -> "50.000 ₫". Missing or non-numeric input yields an empty string. */
export function formatVnd(amount) {
  if (amount === null || amount === undefined || amount === '') return ''
  const n = Number(amount)
  return Number.isFinite(n) ? fmt.format(n) : ''
}
