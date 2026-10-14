import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../hooks/useAuth.js'
import { useDeleteAccount } from '../../hooks/useSettings.js'

const Spinner = () => (
  <span style={{
    width: 14, height: 14, flexShrink: 0,
    border: '2px solid rgba(255,255,255,0.2)',
    borderTopColor: '#fff',
    borderRadius: '50%',
    display: 'inline-block',
    animation: 'spin 0.7s linear infinite',
  }} />
)

export default function DeleteAccountModal({ show, onHide, settings }) {
  const { t } = useTranslation(['settings', 'common'])
  const { user, logout } = useAuth()
  const deleteAccount = useDeleteAccount()

  const mfaEnabled = settings?.mfaEnabled ?? false
  const [otp, setOtp] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [error, setError] = useState('')

  function handleClose() {
    setOtp('')
    setConfirmation('')
    setError('')
    deleteAccount.reset()
    onHide()
  }

  async function handleConfirm() {
    setError('')

    if (mfaEnabled) {
      if (!otp || otp.length !== 6) {
        setError(t('otpInvalid'))
        return
      }
    } else {
      if (!confirmation || confirmation !== user?.email) {
        setError(t('emailMismatch'))
        return
      }
    }

    const body = mfaEnabled ? { otp } : { confirmation }

    deleteAccount.mutate(body, {
      onSuccess: () => {
        logout()
        window.location.href = '/'
      },
      onError: (err) => {
        setError(err?.message || t('deleteFailed'))
      },
    })
  }

  if (!show) return null

  return (
    <div
      className="app-modal-backdrop"
      onClick={e => { if (e.target === e.currentTarget) handleClose() }}
    >
      <div className="app-modal">
        <div className="app-modal-header">
          <span className="app-modal-title" style={{ color: 'var(--danger)' }}>
            {t('deleteAccount')}
          </span>
          <button className="app-modal-close" onClick={handleClose} aria-label={t('common:close')}>
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>close</span>
          </button>
        </div>

        <div className="app-modal-body">
          <div className="app-alert error" style={{ marginBottom: 20 }}>
            <strong>{t('deleteIrreversible')}</strong> {t('deleteWarning')}
          </div>

          {(error || deleteAccount.isError) && (
            <div className="app-alert error">
              {error || deleteAccount.error?.message || t('errorOccurred')}
            </div>
          )}

          {mfaEnabled ? (
            <div className="app-field">
              <label className="app-label">{t('enterOtp')}</label>
              <input
                className="app-input"
                type="text"
                inputMode="numeric"
                maxLength={6}
                value={otp}
                onChange={e => setOtp(e.target.value.replace(/\D/g, ''))}
                placeholder="000000"
                style={{ letterSpacing: '0.3em', maxWidth: 180 }}
                autoFocus
              />
            </div>
          ) : (
            <div className="app-field">
              <label className="app-label">
                {t('typeEmailToConfirm')} <strong style={{ color: 'var(--text)' }}>{user?.email}</strong>
              </label>
              <input
                className="app-input"
                type="email"
                value={confirmation}
                onChange={e => setConfirmation(e.target.value)}
                placeholder={user?.email}
                autoFocus
              />
            </div>
          )}
        </div>

        <div className="app-modal-footer">
          <button
            className="app-btn secondary"
            onClick={handleClose}
            disabled={deleteAccount.isPending}
          >
            {t('common:cancel')}
          </button>
          <button
            className="app-btn danger"
            onClick={handleConfirm}
            disabled={deleteAccount.isPending}
          >
            {deleteAccount.isPending
              ? <><Spinner />{t('deleting')}</>
              : t('deleteMyAccount')}
          </button>
        </div>
      </div>
    </div>
  )
}
