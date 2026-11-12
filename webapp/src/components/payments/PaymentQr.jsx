import React, { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../hooks/useAuth.js'
import { useOrderStatus } from '../../hooks/usePayments.js'
import { authApi } from '../../api/auth.js'
import { formatVnd } from '../../utils/formatVnd.js'

function formatRemaining(ms) {
  const total = Math.max(0, Math.floor(ms / 1000))
  const m = String(Math.floor(total / 60)).padStart(2, '0')
  const s = String(total % 60).padStart(2, '0')
  return `${m}:${s}`
}

function CopyRow({ label, value, display, testId }) {
  const { t } = useTranslation('payments')
  const [copied, setCopied] = useState(false)

  async function copy() {
    try {
      await navigator.clipboard?.writeText(String(value))
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      // clipboard unavailable or denied: the value stays visible for manual copy
    }
  }

  return (
    <div className="pay-row">
      <div className="pay-row-text">
        <span className="pay-row-label">{label}</span>
        <span className="pay-row-value" data-testid={testId}>{display ?? value}</span>
      </div>
      <button type="button" className="icon-btn" onClick={copy} title={t('copy')} aria-label={`${t('copy')} ${label}`}>
        <span className="material-symbols-outlined" style={{ fontSize: 18 }}>{copied ? 'check' : 'content_copy'}</span>
      </button>
    </div>
  )
}

function Outcome({ icon, color, title, body, onNew, newLabel }) {
  return (
    <div className="pay-outcome" role="status">
      <span className="material-symbols-outlined" style={{ fontSize: 56, color }}>{icon}</span>
      <h2 className="pay-title">{title}</h2>
      {body && <p className="pay-muted">{body}</p>}
      {onNew && <button type="button" className="app-btn primary" onClick={onNew}>{newLabel}</button>}
    </div>
  )
}

export default function PaymentQr({ order, onNewOrder }) {
  const { t } = useTranslation('payments')
  const { user, updateUser } = useAuth()
  const { data } = useOrderStatus(order.code)
  const [now, setNow] = useState(() => Date.now())

  const status = data?.status ?? order.status
  const expiresAt = new Date(data?.expiresAt ?? order.expiresAt).getTime()
  const remaining = expiresAt - now
  const pending = status === 'PENDING'

  useEffect(() => {
    if (!pending) return
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [pending])

  // Premium paid: refresh the current user so the badge and settings pick up premiumUntil
  const premiumPaid = status === 'PAID' && order.type === 'PREMIUM'
  useEffect(() => {
    if (!premiumPaid) return
    let cancelled = false
    authApi.me()
      .then((res) => {
        const me = res?.data ?? res
        if (cancelled || !me || !user) return
        updateUser({ ...user, premium: me.premium ?? user.premium, premiumUntil: me.premiumUntil ?? user.premiumUntil })
      })
      .catch(() => {})
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [premiumPaid])

  if (status === 'PAID') {
    return order.type === 'PREMIUM'
      ? <Outcome icon="workspace_premium" color="var(--accent-cyan)" title={t('paidPremiumTitle')} body={t('paidPremiumBody')} />
      : <Outcome icon="favorite" color="#ff6b8a" title={t('paidDonationTitle')} body={t('paidDonationBody', { amount: formatVnd(order.amount) })} />
  }
  if (status === 'UNDERPAID') {
    return <Outcome icon="hourglass_top" color="#f39c12" title={t('underpaidTitle')} body={t('underpaidBody')} />
  }
  if (status === 'CANCELLED') {
    return <Outcome icon="cancel" color="var(--text-muted)" title={t('cancelledTitle')} onNew={onNewOrder} newLabel={t('newOrder')} />
  }
  if (status === 'RESOLVED') {
    return <Outcome icon="task_alt" color="var(--accent-cyan)" title={t('resolvedTitle')} />
  }
  if (status === 'EXPIRED' || remaining <= 0) {
    return <Outcome icon="timer_off" color="#f39c12" title={t('expiredTitle')} body={t('expiredBody')} onNew={onNewOrder} newLabel={t('newOrder')} />
  }

  return (
    <div className="pay-qr">
      <h2 className="pay-title">{t('scanTitle')}</h2>
      <img className="pay-qr-img" src={order.qrUrl} alt={t('qrAlt')} />
      <div className="pay-countdown" data-testid="pay-countdown">
        {t('expiresIn', { time: formatRemaining(remaining) })}
      </div>
      <div className="pay-details">
        <CopyRow label={t('bank')} value={order.bankCode} testId="pay-bank" />
        <CopyRow label={t('accountNumber')} value={order.bankAccount} testId="pay-account" />
        <CopyRow label={t('accountName')} value={order.accountName} testId="pay-holder" />
        <CopyRow label={t('transferAmount')} value={order.amount} display={formatVnd(order.amount)} testId="pay-amount" />
        <CopyRow label={t('memo')} value={order.memo} testId="pay-memo" />
      </div>
      <p className="pay-warning">
        <span className="material-symbols-outlined" style={{ fontSize: 18 }}>warning</span>
        {t('memoWarning')}
      </p>
      <p className="pay-muted">{t('waiting')}</p>
    </div>
  )
}
