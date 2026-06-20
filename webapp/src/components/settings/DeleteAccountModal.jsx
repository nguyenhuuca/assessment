import React, { useState } from 'react'
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
        setError('Please enter a valid 6-digit OTP code.')
        return
      }
    } else {
      if (!confirmation || confirmation !== user?.email) {
        setError('The email you entered does not match your account email.')
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
        setError(err?.message || 'Failed to delete account. Please try again.')
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
            Delete Account
          </span>
          <button className="app-modal-close" onClick={handleClose}>
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>close</span>
          </button>
        </div>

        <div className="app-modal-body">
          <div className="app-alert error" style={{ marginBottom: 20 }}>
            <strong>This action is irreversible.</strong> Your account will be deactivated and your personal data will be anonymized.
          </div>

          {(error || deleteAccount.isError) && (
            <div className="app-alert error">
              {error || deleteAccount.error?.message || 'An error occurred.'}
            </div>
          )}

          {mfaEnabled ? (
            <div className="app-field">
              <label className="app-label">Enter your 6-digit authenticator code</label>
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
                Type your email address to confirm: <strong style={{ color: 'var(--text)' }}>{user?.email}</strong>
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
            Cancel
          </button>
          <button
            className="app-btn danger"
            onClick={handleConfirm}
            disabled={deleteAccount.isPending}
          >
            {deleteAccount.isPending
              ? <><Spinner />Deleting…</>
              : 'Delete My Account'}
          </button>
        </div>
      </div>
    </div>
  )
}
