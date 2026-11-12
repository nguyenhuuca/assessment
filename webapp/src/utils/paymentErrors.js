const ERROR_KEYS = {
  'error.payment.amountTooLow': 'errors.amountTooLow',
  'error.payment.nameTooLong': 'errors.nameTooLong',
  'error.payment.messageTooLong': 'errors.messageTooLong',
  'error.payment.planInvalid': 'errors.planInvalid',
  'error.payment.notConfigured': 'errors.notConfigured',
  'error.auth.required': 'errors.authRequired',
}

/** i18n key (payments namespace) for a backend error code; unknown codes get the generic message. */
export function paymentErrorKey(err) {
  return ERROR_KEYS[err?.code] ?? 'errors.generic'
}
