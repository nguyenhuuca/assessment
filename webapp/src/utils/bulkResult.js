/** Short summary of a bulk result: "3 updated · 1 unchanged · 2 skipped · 1 not found" (zero parts omitted). */
export function formatBulkResult(result, t) {
  const r = result ?? {}
  return [
    ['updated', r.updated ?? 0],
    ['unchanged', r.unchanged ?? 0],
    ['skipped', r.skipped?.length ?? 0],
    ['notFound', r.notFound?.length ?? 0],
  ]
    .filter(([, n]) => n > 0)
    .map(([key, count]) => t(`bulk.${key}`, { count }))
    .join(' · ')
}

/** Summary line plus one entry per skip reason code: { summary, reasons: [{ code, label, count }] }. */
export function buildBulkNotice(result, t) {
  const r = result ?? {}
  const counts = {}
  ;(r.skipped ?? []).forEach(x => { counts[x.code] = (counts[x.code] ?? 0) + 1 })
  return {
    summary: formatBulkResult(r, t),
    reasons: Object.entries(counts).map(([code, count]) => ({
      code,
      label: t(`bulk.reason.${code}`, { defaultValue: t('bulk.reason.unknown') }),
      count,
    })),
  }
}
