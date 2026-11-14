import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'
import ConfirmModal from '../common/ConfirmModal.jsx'
import RowActionsMenu from './RowActionsMenu.jsx'
import { validatePlan } from '../../utils/validatePlan.js'
import { formatVnd } from '../../utils/formatVnd.js'
import { formatDate } from '../../i18n/format.js'
import {
  useAdminPaymentsSummary, useAdminPaymentOrders, useAdminUnmatched, useAdminPaymentPlans,
  useLinkTransaction, useIgnoreTransaction, useResolveOrder, useMarkOrderPaid, useCancelOrder, useCreatePlan, useUpdatePlan, useSetPlanActive,
} from '../../hooks/useAdminPayments.js'

const PAGE_SIZE = 10
const ORDER_TYPES = ['DONATION', 'PREMIUM']
const ORDER_STATUSES = ['PENDING', 'PAID', 'UNDERPAID', 'RESOLVED', 'EXPIRED', 'CANCELLED']
// Orders still waiting for money: admin can confirm a transfer seen on the bank statement, or cancel
const OPEN_STATUSES = ['PENDING', 'EXPIRED']
const DATE_TIME = { dateStyle: 'medium', timeStyle: 'short' }
const STATUS_BADGE = { PENDING: 'pending', UNDERPAID: 'pending', PAID: 'done', RESOLVED: 'done', EXPIRED: 'cancelled', CANCELLED: 'cancelled' }

function Pager({ page, totalPages, onPage }) {
  if (totalPages <= 1) return null
  return (
    <div className="admin-pagination">
      <button className="admin-page-btn" disabled={page === 0} onClick={() => onPage(page - 1)}>‹</button>
      {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => (
        <button key={i} className={`admin-page-btn${page === i ? ' active' : ''}`} onClick={() => onPage(i)}>{i + 1}</button>
      ))}
      <button className="admin-page-btn" disabled={page >= totalPages - 1} onClick={() => onPage(page + 1)}>›</button>
    </div>
  )
}

function SummaryCards() {
  const { t } = useTranslation('admin')
  const { data } = useAdminPaymentsSummary()
  const s = data ?? {}
  const money = v => (v === undefined || v === null ? '—' : formatVnd(v))
  const num = v => (v === undefined || v === null ? '—' : v)
  const cards = [
    ['donationTotal', money(s.donationTotal)], ['donation30d', money(s.donation30d)],
    ['premiumTotal', money(s.premiumTotal)], ['premium30d', money(s.premium30d)],
    ['pendingOrders', num(s.pendingOrders)], ['underpaidOrders', num(s.underpaidOrders)],
    ['unmatchedTransactions', num(s.unmatchedTransactions)],
  ]
  return (
    <div className="admin-stat-cards" data-testid="pay-summary">
      {cards.map(([k, v]) => (
        <div className="admin-stat-card" key={k}>
          <span className="admin-stat-label">{t(`pay.summary.${k}`)}</span>
          <span className="admin-stat-value">{v}</span>
        </div>
      ))}
    </div>
  )
}

function OrdersSection() {
  const { t } = useTranslation('admin')
  const [type, setType] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [resolving, setResolving] = useState(null)
  const [grant, setGrant] = useState(false)
  const [error, setError] = useState('')
  const { data, isLoading } = useAdminPaymentOrders({ type, status, page, size: PAGE_SIZE })
  const resolve = useResolveOrder()
  const markPaid = useMarkOrderPaid()
  const cancel = useCancelOrder()
  const [paying, setPaying] = useState(null)
  const [payAmount, setPayAmount] = useState('')
  const [cancelling, setCancelling] = useState(null)
  const orders = data?.content ?? []

  const onFail = e => setError(e?.message || t('pay.actionFailed'))
  function openMarkPaid(o) { setError(''); setPayAmount(o.expectedAmount ? String(o.expectedAmount) : ''); setPaying(o) }
  const payValue = Number(payAmount)
  const payValid = payAmount !== '' && Number.isInteger(payValue) && payValue > 0
  function confirmMarkPaid() {
    if (!payValid) return
    markPaid.mutate({ id: paying.id, amount: payValue }, {
      onSuccess: () => setPaying(null),
      onError: e => { setPaying(null); onFail(e) },
    })
  }
  function confirmCancel() {
    cancel.mutate({ id: cancelling.id }, {
      onSuccess: () => setCancelling(null),
      onError: e => { setCancelling(null); onFail(e) },
    })
  }
  function rowActions(o) {
    if (OPEN_STATUSES.includes(o.status)) {
      return [
        { key: 'paid', icon: 'check_circle', label: t('pay.markPaid'), onSelect: () => openMarkPaid(o) },
        { key: 'cancel', icon: 'cancel', label: t('pay.cancelOrder'), danger: true, onSelect: () => { setError(''); setCancelling(o) } },
      ]
    }
    if (o.status === 'UNDERPAID') {
      return [{ key: 'resolve', icon: 'task_alt', label: t('pay.resolve'), onSelect: () => openResolve(o) }]
    }
    return []
  }

  function openResolve(o) { setError(''); setGrant(o.type === 'PREMIUM'); setResolving(o) }
  function confirmResolve() {
    resolve.mutate({ id: resolving.id, grantPremium: resolving.type === 'PREMIUM' ? grant : false }, {
      onSuccess: () => setResolving(null),
      onError: e => { setResolving(null); setError(e?.message || t('pay.actionFailed')) },
    })
  }

  return (
    <section className="admin-table-wrap pay-section" aria-label={t('pay.ordersTitle')}>
      <div className="pay-section-head">
        <h2 className="pay-section-title">{t('pay.ordersTitle')}</h2>
        <div className="admin-filters">
          <select className="admin-status-select" aria-label={t('pay.filterType')} value={type}
            onChange={e => { setType(e.target.value); setPage(0) }}>
            <option value="">{t('pay.allTypes')}</option>
            {ORDER_TYPES.map(v => <option key={v} value={v}>{t(`pay.type.${v}`)}</option>)}
          </select>
          <select className="admin-status-select" aria-label={t('pay.filterStatus')} value={status}
            onChange={e => { setStatus(e.target.value); setPage(0) }}>
            <option value="">{t('pay.allStatuses')}</option>
            {ORDER_STATUSES.map(v => <option key={v} value={v}>{t(`pay.status.${v}`)}</option>)}
          </select>
        </div>
      </div>
      {error && <p role="alert" className="pay-section-error">{error}</p>}
      <div className="admin-table-scroll">
        <table className="admin-table">
          <thead>
            <tr>
              <th>{t('pay.col.code')}</th><th>{t('pay.col.type')}</th><th>{t('pay.col.status')}</th>
              <th>{t('pay.col.expected')}</th><th>{t('pay.col.paid')}</th><th>{t('pay.col.surplus')}</th>
              <th>{t('pay.col.donor')}</th><th>{t('pay.col.created')}</th><th />
            </tr>
          </thead>
          <tbody>
            {!isLoading && orders.length === 0 && (
              <tr className="admin-table-row"><td colSpan={9} className="admin-cell-muted">{t('pay.noOrders')}</td></tr>
            )}
            {orders.map(o => (
              <tr className="admin-table-row" key={o.id}>
                <td><code>{o.code}</code></td>
                <td>{t(`pay.type.${o.type}`, o.type)}</td>
                <td><span className={`admin-badge ${STATUS_BADGE[o.status] ?? ''}`}>{t(`pay.status.${o.status}`, o.status)}</span></td>
                <td>{formatVnd(o.expectedAmount)}</td>
                <td>{o.paidAmount ? formatVnd(o.paidAmount) : '—'}</td>
                <td>{o.surplus ? formatVnd(o.surplus) : '—'}</td>
                <td className="admin-cell-muted">{o.donorName || '—'}</td>
                <td className="admin-cell-muted">{o.createdAt ? formatDate(o.createdAt, DATE_TIME) : '—'}</td>
                <td>
                  {rowActions(o).length > 0 && (
                    <RowActionsMenu label={t('pay.actions', { code: o.code })} items={rowActions(o)} />
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pager page={page} totalPages={data?.totalPages ?? 0} onPage={setPage} />
      {paying && (
        <ConfirmModal
          title={t('pay.markPaidTitle', { code: paying.code })}
          message={t('pay.markPaidMessage')}
          confirmLabel={t('pay.markPaidConfirm')}
          pending={markPaid.isPending}
          onCancel={() => setPaying(null)}
          onConfirm={confirmMarkPaid}
        >
          <label style={{ display: 'block' }}>
            {t('pay.markPaidAmount')}
            <input className="admin-input" type="number" min="1" step="1" inputMode="numeric"
              value={payAmount} onChange={e => setPayAmount(e.target.value)} aria-invalid={!payValid} />
          </label>
          {!payValid && <p role="alert" className="admin-cell-muted">{t('pay.markPaidAmountInvalid')}</p>}
        </ConfirmModal>
      )}
      {cancelling && (
        <ConfirmModal
          title={t('pay.cancelTitle', { code: cancelling.code })}
          message={t('pay.cancelMessage')}
          confirmLabel={t('pay.cancelConfirm')}
          danger
          pending={cancel.isPending}
          onCancel={() => setCancelling(null)}
          onConfirm={confirmCancel}
        />
      )}
      {resolving && (
        <ConfirmModal
          title={t('pay.resolveTitle', { code: resolving.code })}
          message={t('pay.resolveMessage', { paid: formatVnd(resolving.paidAmount), expected: formatVnd(resolving.expectedAmount) })}
          confirmLabel={t('pay.resolveConfirm')}
          pending={resolve.isPending}
          onCancel={() => setResolving(null)}
          onConfirm={confirmResolve}
        >
          {resolving.type === 'PREMIUM' ? (
            <div role="radiogroup" aria-label={t('pay.resolveChoice')}>
              <label style={{ display: 'block' }}>
                <input type="radio" name="resolve-choice" checked={grant} onChange={() => setGrant(true)} /> {t('pay.resolveGrant')}
              </label>
              <label style={{ display: 'block' }}>
                <input type="radio" name="resolve-choice" checked={!grant} onChange={() => setGrant(false)} /> {t('pay.resolveDonation')}
              </label>
            </div>
          ) : null}
        </ConfirmModal>
      )}
    </section>
  )
}

function UnmatchedSection() {
  const { t } = useTranslation('admin')
  const [page, setPage] = useState(0)
  const [linking, setLinking] = useState(null)
  const [orderCode, setOrderCode] = useState('')
  const [error, setError] = useState('')
  const { data, isLoading } = useAdminUnmatched({ page, size: PAGE_SIZE })
  const link = useLinkTransaction()
  const ignore = useIgnoreTransaction()
  const [ignoring, setIgnoring] = useState(null)
  const rows = data?.content ?? []

  function confirmIgnore() {
    ignore.mutate({ sepayId: ignoring.sepayId }, {
      onSuccess: () => setIgnoring(null),
      onError: err => { setIgnoring(null); setError(err?.message || t('pay.actionFailed')) },
    })
  }

  function submit(e) {
    e.preventDefault()
    const code = orderCode.trim()
    if (!code) return
    setError('')
    link.mutate({ sepayId: linking.sepayId, orderCode: code }, {
      onSuccess: () => { setLinking(null); setOrderCode('') },
      onError: err => setError(err?.message || t('pay.actionFailed')),
    })
  }

  return (
    <section className="admin-table-wrap pay-section" aria-label={t('pay.unmatchedTitle')}>
      <div className="pay-section-head"><h2 className="pay-section-title">{t('pay.unmatchedTitle')}</h2></div>
      {error && !linking && <p role="alert" className="pay-section-error">{error}</p>}
      <div className="admin-table-scroll">
        <table className="admin-table">
          <thead>
            <tr>
              <th>{t('pay.col.amount')}</th><th>{t('pay.col.content')}</th><th>{t('pay.col.gateway')}</th>
              <th>{t('pay.col.date')}</th><th />
            </tr>
          </thead>
          <tbody>
            {!isLoading && rows.length === 0 && (
              <tr className="admin-table-row"><td colSpan={5} className="admin-cell-muted">{t('pay.noUnmatched')}</td></tr>
            )}
            {rows.map(r => (
              <React.Fragment key={r.sepayId}>
                <tr className="admin-table-row">
                  <td>{formatVnd(r.amount)}</td>
                  <td className="admin-cell-muted">{r.content}</td>
                  <td className="admin-cell-muted">{r.gateway}</td>
                  <td className="admin-cell-muted">{r.transactionDate ? formatDate(r.transactionDate, DATE_TIME) : '—'}</td>
                  <td>
                    <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                      <button className="app-btn secondary" onClick={() => { setLinking(r); setOrderCode(r.code || ''); setError('') }}>
                        {t('pay.linkToOrder')}
                      </button>
                      <button className="app-btn secondary" onClick={() => { setLinking(null); setError(''); setIgnoring(r) }}>
                        {t('pay.ignoreTx')}
                      </button>
                    </div>
                  </td>
                </tr>
                {linking?.sepayId === r.sepayId && (
                  <tr>
                    <td colSpan={5}>
                      <form onSubmit={submit} style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                        <input className="app-input" style={{ maxWidth: 240 }} aria-label={t('pay.orderCode')}
                          placeholder={t('pay.orderCode')} value={orderCode} onChange={e => setOrderCode(e.target.value)} />
                        <button type="submit" className="app-btn primary" disabled={link.isPending || !orderCode.trim()}>{t('pay.link')}</button>
                        <button type="button" className="app-btn secondary" onClick={() => setLinking(null)}>{t('pay.cancel')}</button>
                        {error && <span role="alert" style={{ color: '#ff1744' }}>{error}</span>}
                      </form>
                    </td>
                  </tr>
                )}
              </React.Fragment>
            ))}
          </tbody>
        </table>
      </div>
      <Pager page={page} totalPages={data?.totalPages ?? 0} onPage={setPage} />
      {ignoring && (
        <ConfirmModal
          title={t('pay.ignoreTitle', { amount: formatVnd(ignoring.amount) })}
          message={t('pay.ignoreMessage')}
          confirmLabel={t('pay.ignoreConfirm')}
          pending={ignore.isPending}
          onCancel={() => setIgnoring(null)}
          onConfirm={confirmIgnore}
        />
      )}
    </section>
  )
}

const EMPTY_PLAN = { code: '', name: '', durationDays: '', priceVnd: '', sortOrder: '', active: true }

function PlansSection() {
  const { t } = useTranslation('admin')
  const { data: plans = [] } = useAdminPaymentPlans()
  const create = useCreatePlan()
  const update = useUpdatePlan()
  const toggle = useSetPlanActive()
  const [form, setForm] = useState(null) // null = closed; { id?, ...fields }
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')

  const set = (k, v) => setForm(f => ({ ...f, [k]: v }))
  const fail = e => setError(e?.message || t('pay.actionFailed'))

  function save(e) {
    e.preventDefault()
    const errs = validatePlan(form)
    setErrors(errs)
    if (Object.keys(errs).length) return
    setError('')
    const body = {
      code: form.code, name: form.name.trim(),
      durationDays: Number(form.durationDays), priceVnd: Number(form.priceVnd),
      active: form.active,
      ...(form.sortOrder !== '' && form.sortOrder !== null ? { sortOrder: Number(form.sortOrder) } : {}),
    }
    const opts = { onSuccess: () => setForm(null), onError: fail }
    if (form.id) update.mutate({ id: form.id, body }, opts)
    else create.mutate(body, opts)
  }

  function field(key, type = 'text') {
    return (
      <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <span className="admin-cell-muted">{t(`pay.plan.${key}`)}</span>
        <input className="app-input" type={type} aria-label={t(`pay.plan.${key}`)} value={form[key] ?? ''}
          onChange={e => set(key, e.target.value)} />
        {errors[key] && <span role="alert" style={{ color: '#ff1744', fontSize: 12 }}>{t(`pay.plan.${errors[key]}`)}</span>}
      </label>
    )
  }

  return (
    <section className="admin-table-wrap pay-section" aria-label={t('pay.plansTitle')}>
      <div className="pay-section-head">
        <h2 className="pay-section-title">{t('pay.plansTitle')}</h2>
        <button className="app-btn primary" onClick={() => { setErrors({}); setError(''); setForm({ ...EMPTY_PLAN }) }}>{t('pay.plan.add')}</button>
      </div>
      {error && <p role="alert" className="pay-section-error">{error}</p>}
      {form && (
        <form onSubmit={save} style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'flex-start', margin: '12px 0' }}>
          {field('code')}{field('name')}{field('durationDays', 'number')}{field('priceVnd', 'number')}{field('sortOrder', 'number')}
          <label style={{ display: 'flex', gap: 6, alignItems: 'center', marginTop: 22 }}>
            <input type="checkbox" checked={form.active} onChange={e => set('active', e.target.checked)} />
            {t('pay.plan.active')}
          </label>
          <div style={{ display: 'flex', gap: 8, marginTop: 18 }}>
            <button type="submit" className="app-btn primary" disabled={create.isPending || update.isPending}>{t('pay.plan.save')}</button>
            <button type="button" className="app-btn secondary" onClick={() => setForm(null)}>{t('pay.cancel')}</button>
          </div>
        </form>
      )}
      <div className="admin-table-scroll">
        <table className="admin-table">
          <thead>
            <tr>
              <th>{t('pay.plan.code')}</th><th>{t('pay.plan.name')}</th><th>{t('pay.plan.durationDays')}</th>
              <th>{t('pay.plan.priceVnd')}</th><th>{t('pay.plan.active')}</th><th />
            </tr>
          </thead>
          <tbody>
            {plans.length === 0 && <tr className="admin-table-row"><td colSpan={6} className="admin-cell-muted">{t('pay.noPlans')}</td></tr>}
            {plans.map(p => (
              <tr className="admin-table-row" key={p.id}>
                <td><code>{p.code}</code></td>
                <td>{p.name}</td>
                <td>{p.durationDays}</td>
                <td>{formatVnd(p.priceVnd)}</td>
                <td>
                  <input type="checkbox" aria-label={t('pay.plan.activeAria', { code: p.code })} checked={!!p.active}
                    disabled={toggle.isPending}
                    onChange={e => { setError(''); toggle.mutate({ id: p.id, active: e.target.checked }, { onError: fail }) }} />
                </td>
                <td>
                  <button className="app-btn secondary" aria-label={t('pay.plan.editAria', { code: p.code })}
                    onClick={() => { setErrors({}); setError(''); setForm({ ...p, sortOrder: p.sortOrder ?? '' }) }}>
                    {t('pay.plan.edit')}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

export default function AdminPaymentsTab() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <SummaryCards />
      <OrdersSection />
      <UnmatchedSection />
      <PlansSection />
    </div>
  )
}
