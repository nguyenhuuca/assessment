import React, { useState } from 'react'
import { authApi } from '../../api/auth.js'
import { useAuth } from '../../hooks/useAuth.js'

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

export default function ProfileModal({ show, onHide }) {
  const { user, updateUser } = useAuth()
  const [activeTab, setActiveTab] = useState('info')
  const [mfaLoading, setMfaLoading] = useState(false)
  const [mfaMsg, setMfaMsg] = useState(null) // { text, type: 'error'|'success' }
  const [qrCode, setQrCode] = useState(null)
  const [secret, setSecret] = useState(null)
  const [setupCode, setSetupCode] = useState('')
  const [disableCode, setDisableCode] = useState('')

  async function handleSetupMFA() {
    setMfaLoading(true)
    setMfaMsg(null)
    try {
      const res = await authApi.mfa.setup(user?.email)
      setQrCode(res.data.qrCode)
      setSecret(res.data.secret)
    } catch (err) {
      setMfaMsg({ text: err.message, type: 'error' })
    } finally {
      setMfaLoading(false)
    }
  }

  async function handleEnableMFA() {
    if (!setupCode || setupCode.length !== 6) {
      setMfaMsg({ text: 'Please enter a valid 6-digit code', type: 'error' })
      return
    }
    setMfaLoading(true)
    setMfaMsg(null)
    try {
      await authApi.mfa.enable(setupCode, user?.email, secret)
      updateUser({ ...user, mfaEnabled: true })
      setMfaMsg({ text: 'MFA enabled successfully!', type: 'success' })
      setQrCode(null)
      setSetupCode('')
    } catch (err) {
      setMfaMsg({ text: err.message, type: 'error' })
    } finally {
      setMfaLoading(false)
    }
  }

  async function handleDisableMFA() {
    if (!disableCode || disableCode.length !== 6) {
      setMfaMsg({ text: 'Please enter a valid 6-digit code', type: 'error' })
      return
    }
    setMfaLoading(true)
    setMfaMsg(null)
    try {
      await authApi.mfa.disable(disableCode, user?.email)
      updateUser({ ...user, mfaEnabled: false })
      setMfaMsg({ text: 'MFA disabled successfully!', type: 'success' })
      setDisableCode('')
    } catch (err) {
      setMfaMsg({ text: err.message, type: 'error' })
    } finally {
      setMfaLoading(false)
    }
  }

  function handleClose() {
    setMfaMsg(null)
    setQrCode(null)
    setSecret(null)
    setSetupCode('')
    setDisableCode('')
    onHide()
  }

  if (!show) return null

  return (
    <div className="app-modal-backdrop" onClick={e => { if (e.target === e.currentTarget) handleClose() }}>
      <div className="app-modal lg">
        <div className="app-modal-header">
          <span className="app-modal-title">Profile</span>
          <button className="app-modal-close" onClick={handleClose}>
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>close</span>
          </button>
        </div>

        <div className="app-modal-body">
          {/* Tabs */}
          <div className="app-tabs">
            <button
              className={`app-tab${activeTab === 'info' ? ' active' : ''}`}
              onClick={() => setActiveTab('info')}
            >
              User Info
            </button>
            <button
              className={`app-tab${activeTab === 'mfa' ? ' active' : ''}`}
              onClick={() => setActiveTab('mfa')}
            >
              MFA Settings
            </button>
          </div>

          {/* User Info tab */}
          {activeTab === 'info' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <InfoRow label="Email" value={user?.email} />
              <InfoRow label="Member Since" value={new Date().toLocaleDateString()} />
              <InfoRow label="Last Login" value={new Date().toLocaleDateString()} />
            </div>
          )}

          {/* MFA tab */}
          {activeTab === 'mfa' && (
            <div>
              {mfaMsg && (
                <div className={`app-alert ${mfaMsg.type}`}>{mfaMsg.text}</div>
              )}

              <div style={{
                display: 'flex', alignItems: 'center', gap: 8,
                marginBottom: 20, padding: '10px 14px',
                background: 'var(--bg-elevated)', borderRadius: 8,
              }}>
                <span className="material-symbols-outlined" style={{
                  fontSize: 18,
                  color: user?.mfaEnabled ? 'var(--accent-cyan)' : 'var(--text-muted)',
                }}>
                  {user?.mfaEnabled ? 'gpp_good' : 'gpp_maybe'}
                </span>
                <span style={{ fontSize: 13, color: 'var(--text-muted)' }}>
                  MFA Status:{' '}
                  <strong style={{ color: user?.mfaEnabled ? 'var(--accent-cyan)' : 'var(--text)' }}>
                    {user?.mfaEnabled ? 'Enabled' : 'Disabled'}
                  </strong>
                </span>
              </div>

              {!user?.mfaEnabled ? (
                !qrCode ? (
                  <button className="app-btn primary" onClick={handleSetupMFA} disabled={mfaLoading}>
                    {mfaLoading ? <><Spinner />Setting up…</> : 'Setup MFA'}
                  </button>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                    <p style={{ fontSize: 13, color: 'var(--text-muted)' }}>
                      Scan this QR code with your authenticator app:
                    </p>
                    <img
                      src={`data:image/png;base64,${qrCode}`}
                      alt="MFA QR Code"
                      style={{ borderRadius: 8, maxWidth: 180 }}
                    />
                    <div className="app-field" style={{ marginBottom: 0 }}>
                      <label className="app-label">Verification code</label>
                      <input
                        className="app-input"
                        type="text"
                        maxLength={6}
                        value={setupCode}
                        onChange={e => setSetupCode(e.target.value.replace(/\D/g, ''))}
                        placeholder="000000"
                        style={{ letterSpacing: '0.3em', maxWidth: 160 }}
                      />
                    </div>
                    <button className="app-btn success" onClick={handleEnableMFA} disabled={mfaLoading}>
                      {mfaLoading ? <><Spinner />Enabling…</> : 'Enable MFA'}
                    </button>
                  </div>
                )
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  <div className="app-field" style={{ marginBottom: 0 }}>
                    <label className="app-label">Enter code to disable MFA</label>
                    <input
                      className="app-input"
                      type="text"
                      maxLength={6}
                      value={disableCode}
                      onChange={e => setDisableCode(e.target.value.replace(/\D/g, ''))}
                      placeholder="000000"
                      style={{ letterSpacing: '0.3em', maxWidth: 160 }}
                    />
                  </div>
                  <button className="app-btn danger" onClick={handleDisableMFA} disabled={mfaLoading}>
                    {mfaLoading ? <><Spinner />Disabling…</> : 'Disable MFA'}
                  </button>
                </div>
              )}
            </div>
          )}
        </div>

        <div className="app-modal-footer">
          <button className="app-btn secondary" onClick={handleClose}>Close</button>
        </div>
      </div>
    </div>
  )
}

function InfoRow({ label, value }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 12,
      padding: '10px 14px',
      background: 'var(--bg-elevated)', borderRadius: 8,
    }}>
      <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-muted)', minWidth: 100, textTransform: 'uppercase', letterSpacing: '0.04em' }}>
        {label}
      </span>
      <span style={{ fontSize: 13, color: 'var(--text)' }}>{value}</span>
    </div>
  )
}
