const CODE_RE = /^[A-Z0-9_]{3,32}$/

/** Client-side plan form validation; returns { field: i18nKeySuffix } for each invalid field. */
export function validatePlan({ code, durationDays, priceVnd }) {
  const errors = {}
  if (!CODE_RE.test(code || '')) errors.code = 'codeInvalid'
  if (!(Number(priceVnd) > 0)) errors.priceVnd = 'priceInvalid'
  if (!(Number(durationDays) > 0)) errors.durationDays = 'daysInvalid'
  return errors
}
