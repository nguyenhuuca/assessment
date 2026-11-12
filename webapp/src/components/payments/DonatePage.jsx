import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../hooks/useAuth.js'
import { useCreateOrder, usePlans } from '../../hooks/usePayments.js'
import { formatVnd } from '../../utils/formatVnd.js'
import { paymentErrorKey } from '../../utils/paymentErrors.js'
import PaymentQr from './PaymentQr.jsx'

const MIN_DONATION = 10000
const PRESETS = [20000, 50000, 100000]
const NAME_MAX = 50
const MESSAGE_MAX = 200

function DonateForm({ onSubmit, busy }) {
  const { t } = useTranslation('payments')
  const [preset, setPreset] = useState(PRESETS[1])
  const [custom, setCustom] = useState('')
  const [name, setName] = useState('')
  const [message, setMessage] = useState('')

  const amount = preset ?? Number(custom)
  const valid = Number.isInteger(amount) && amount >= MIN_DONATION

  function submit(e) {
    e.preventDefault()
    if (!valid || busy) return
    onSubmit({
      type: 'DONATION',
      amount,
      ...(name.trim() ? { donorName: name.trim() } : {}),
      ...(message.trim() ? { message: message.trim() } : {}),
    })
  }

  return (
    <form className="pay-form" onSubmit={submit}>
      <p className="pay-muted">{t('donateIntro')}</p>
      <div className="pay-presets" role="group" aria-label={t('amount')}>
        {PRESETS.map(p => (
          <button
            key={p}
            type="button"
            className={`pay-chip${preset === p ? ' active' : ''}`}
            aria-pressed={preset === p}
            onClick={() => { setPreset(p); setCustom('') }}
          >
            {formatVnd(p)}
          </button>
        ))}
        <button
          type="button"
          className={`pay-chip${preset === null ? ' active' : ''}`}
          aria-pressed={preset === null}
          onClick={() => setPreset(null)}
        >
          {t('customAmount')}
        </button>
      </div>
      {preset === null && (
        <input
          className="app-input"
          type="number"
          inputMode="numeric"
          min={MIN_DONATION}
          step={1000}
          aria-label={t('amount')}
          placeholder={t('amountPlaceholder')}
          value={custom}
          onChange={e => setCustom(e.target.value)}
        />
      )}
      <label className="pay-field">
        <span>{t('name')}</span>
        <input
          className="app-input"
          maxLength={NAME_MAX}
          value={name}
          onChange={e => setName(e.target.value)}
        />
        <small className="pay-counter">{name.length}/{NAME_MAX}</small>
      </label>
      <label className="pay-field">
        <span>{t('message')}</span>
        <textarea
          className="app-input"
          rows={3}
          maxLength={MESSAGE_MAX}
          value={message}
          onChange={e => setMessage(e.target.value)}
        />
        <small className="pay-counter">{message.length}/{MESSAGE_MAX}</small>
      </label>
      <button type="submit" className="app-btn primary" disabled={!valid || busy}>
        {busy ? t('creating') : t('donateButton')}
      </button>
    </form>
  )
}

function PremiumPlans({ onBuy, busy }) {
  const { t } = useTranslation('payments')
  const { isLoggedIn } = useAuth()
  const { data: plans, isLoading } = usePlans()

  return (
    <div className="pay-form">
      <p className="pay-muted">{t('premiumIntro')}</p>
      {!isLoggedIn && (
        <p className="pay-warning" role="note" data-testid="pay-login-prompt">
          <span className="material-symbols-outlined" style={{ fontSize: 18 }}>login</span>
          {t('loginRequired')}
        </p>
      )}
      {isLoading && <p className="pay-muted">{t('plansLoading')}</p>}
      {!isLoading && !plans?.length && <p className="pay-muted">{t('plansEmpty')}</p>}
      <ul className="pay-plans">
        {(plans ?? []).map(plan => (
          <li key={plan.id} className="pay-plan">
            <div>
              <div className="pay-plan-name">{plan.name}</div>
              <div className="pay-muted">{t('days', { count: plan.durationDays })}</div>
            </div>
            <div className="pay-plan-price">{formatVnd(plan.priceVnd)}</div>
            {isLoggedIn && (
              <button
                type="button"
                className="app-btn primary"
                disabled={busy}
                onClick={() => onBuy({ type: 'PREMIUM', planId: plan.id })}
              >
                {t('buyPlan')}
              </button>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}

export default function DonatePage({ initialTab = 'donate' }) {
  const { t } = useTranslation('payments')
  const [tab, setTab] = useState(initialTab)
  const [order, setOrder] = useState(null)
  const createOrder = useCreateOrder()

  function submit(body) {
    createOrder.mutate(body, { onSuccess: setOrder })
  }

  function reset() {
    createOrder.reset()
    setOrder(null)
  }

  return (
    <div className="pay-page">
      <h1 className="pay-heading">{t('title')}</h1>
      {order ? (
        <PaymentQr order={order} onNewOrder={reset} />
      ) : (
        <>
          <div className="app-tabs" role="tablist">
            {['donate', 'premium'].map(key => (
              <button
                key={key}
                role="tab"
                aria-selected={tab === key}
                className={`app-tab${tab === key ? ' active' : ''}`}
                onClick={() => { createOrder.reset(); setTab(key) }}
              >
                {t(key === 'donate' ? 'tabDonate' : 'tabPremium')}
              </button>
            ))}
          </div>
          {createOrder.isError && (
            <p className="pay-error" role="alert">{t(paymentErrorKey(createOrder.error))}</p>
          )}
          {tab === 'donate'
            ? <DonateForm onSubmit={submit} busy={createOrder.isPending} />
            : <PremiumPlans onBuy={submit} busy={createOrder.isPending} />}
        </>
      )}
    </div>
  )
}
