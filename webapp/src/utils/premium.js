/** True while premiumUntil (ISO string) is in the future. */
export function isPremiumActive(premiumUntil, now = Date.now()) {
  if (!premiumUntil) return false
  const ts = new Date(premiumUntil).getTime()
  return Number.isFinite(ts) && ts > now
}
