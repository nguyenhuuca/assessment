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
