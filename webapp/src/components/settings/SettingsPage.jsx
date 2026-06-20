import React, { useState } from 'react'
import { useAuth } from '../../hooks/useAuth.js'
import { useSettings, useUpdateSettings } from '../../hooks/useSettings.js'
import ProfileModal from '../auth/ProfileModal.jsx'
import DeleteAccountModal from './DeleteAccountModal.jsx'

const QUALITY_OPTIONS = [
  { value: 'AUTO',  label: 'Auto' },
  { value: '1080P', label: '1080p' },
  { value: '4K',    label: '4K' },
]

const Spinner = () => (
  <div style={{
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    height: 200, color: 'var(--text-muted)', fontSize: 14,
    gap: 12,
  }}>
    <span style={{
      width: 20, height: 20,
      border: '2px solid rgba(255,255,255,0.15)',
      borderTopColor: 'var(--accent-cyan)',
      borderRadius: '50%',
      display: 'inline-block',
      animation: 'spin 0.75s linear infinite',
    }} />
    Loading settings…
  </div>
)

function Toggle({ value, onChange, disabled }) {
  return (
    <button
      type="button"
      className={`settings-toggle${value ? ' on' : ''}`}
      onClick={() => !disabled && onChange(!value)}
      disabled={disabled}
      aria-checked={value}
      role="switch"
    >
      <span className="settings-toggle-knob" />
    </button>
  )
}

export default function SettingsPage() {
  const { user } = useAuth()
  const { data: settings, isLoading, isError, error: loadError } = useSettings()
  const updateSettings = useUpdateSettings()

  const [profileOpen, setProfileOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [patchError, setPatchError] = useState(null)

  function handleToggle(field, value) {
    setPatchError(null)
    updateSettings.mutate(
      { [field]: value },
      {
        onError: (err) => {
          setPatchError(err?.message || 'Failed to save setting. Please try again.')
        },
      }
    )
  }

  function handleQuality(value) {
    setPatchError(null)
    updateSettings.mutate(
      { defaultQuality: value },
      {
        onError: (err) => {
          setPatchError(err?.message || 'Failed to save quality setting.')
        },
      }
    )
  }

  if (isLoading) return <div className="settings-root"><Spinner /></div>

  if (isError) {
    return (
      <div className="settings-root">
        <div className="app-alert error" style={{ marginTop: 20 }}>
          {loadError?.message || 'Failed to load settings.'}
        </div>
      </div>
    )
  }

  const email = settings?.email || user?.email || ''
  const passwordEnabled = settings?.passwordEnabled ?? false
  const mfaEnabled = settings?.mfaEnabled ?? false
  const notifyNewContent = settings?.notifyNewContent ?? true
  const notifyEmail = settings?.notifyEmail ?? true
  const defaultQuality = settings?.defaultQuality ?? 'AUTO'
  const incognitoEnabled = settings?.incognitoEnabled ?? false
  const profilePrivate = settings?.profilePrivate ?? false
  const accountStatus = settings?.accountStatus ?? null

  const isMutating = updateSettings.isPending

  return (
    <div className="settings-root">
      {/* Header */}
      <div className="settings-header">
        <h1 className="settings-title">Settings</h1>
        <p className="settings-subtitle">Manage your cinematic experience and account preferences.</p>
      </div>

      {/* Global patch error */}
      {patchError && (
        <div className="app-alert error" style={{ marginBottom: 20 }}>
          {patchError}
        </div>
      )}

      <div className="settings-grid">

        {/* ── Account Identity ── */}
        <section className="settings-card settings-col-8">
          <div className="settings-card-header">
            <div className="settings-card-icon pink">
              <span className="material-symbols-outlined" style={{ fontSize: 24 }}>account_circle</span>
            </div>
            <h2 className="settings-card-title">Account Identity</h2>
          </div>

          <div className="settings-identity-fields">
            <div>
              <label className="app-label">Email Address</label>
              <input
                className="app-input readonly"
                type="email"
                value={email}
                readOnly
                tabIndex={-1}
              />
            </div>

            <div>
              <label className="app-label">
                {passwordEnabled ? 'Password' : 'Sign-in Method'}
              </label>
              {passwordEnabled ? (
                <div style={{ position: 'relative' }}>
                  <input
                    className="app-input readonly"
                    type="password"
                    value="••••••••••"
                    readOnly
                    tabIndex={-1}
                  />
                </div>
              ) : (
                <div style={{
                  background: 'var(--bg-elevated)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: 8,
                  padding: '9px 12px',
                  fontSize: 13,
                  color: 'var(--accent-cyan)',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                }}>
                  <span className="material-symbols-outlined" style={{ fontSize: 16 }}>link</span>
                  Magic Link (passwordless)
                </div>
              )}
            </div>
          </div>

          <div className="settings-identity-action">
            {passwordEnabled && (
              <button
                className="app-btn secondary"
                onClick={() => setProfileOpen(true)}
              >
                <span className="material-symbols-outlined" style={{ fontSize: 16 }}>lock</span>
                Change Password
              </button>
            )}
            {!passwordEnabled && (
              <button
                className="app-btn secondary"
                onClick={() => setProfileOpen(true)}
              >
                <span className="material-symbols-outlined" style={{ fontSize: 16 }}>link</span>
                Manage Sign-in
              </button>
            )}
            <button
              className="app-btn secondary"
              onClick={() => setProfileOpen(true)}
            >
              <span className="material-symbols-outlined" style={{ fontSize: 16 }}>
                {mfaEnabled ? 'gpp_good' : 'gpp_maybe'}
              </span>
              {mfaEnabled ? 'MFA Enabled' : 'Set Up MFA'}
            </button>
          </div>
        </section>

        {/* ── Account Status (conditional) ── */}
        {accountStatus ? (
          <aside className="settings-status-card settings-col-4">
            <p className="settings-status-label">Account Status</p>
            <div className="settings-status-plan">
              <span className="settings-status-dot" />
              {accountStatus.plan || 'Free'}
            </div>
            {accountStatus.renewsAt ? (
              <p className="settings-status-renew">
                Your subscription renews on{' '}
                <strong style={{ color: 'var(--text)' }}>
                  {new Date(accountStatus.renewsAt).toLocaleDateString()}
                </strong>.
              </p>
            ) : (
              <p className="settings-status-renew">Free plan — no renewal date.</p>
            )}
          </aside>
        ) : (
          /* placeholder to keep grid layout balanced */
          <div className="settings-col-4" style={{ display: 'contents' }} />
        )}

        {/* ── Notifications ── */}
        <section className="settings-card settings-col-6">
          <div className="settings-card-header">
            <div className="settings-card-icon cyan">
              <span className="material-symbols-outlined" style={{ fontSize: 22 }}>notifications_active</span>
            </div>
            <h2 className="settings-card-title">Notifications</h2>
          </div>

          <div className="settings-row">
            <div>
              <p className="settings-row-label">New Content Alerts</p>
              <p className="settings-row-desc">Get notified when new videos are posted</p>
            </div>
            <Toggle
              value={notifyNewContent}
              onChange={v => handleToggle('notifyNewContent', v)}
              disabled={isMutating}
            />
          </div>

          <div className="settings-row">
            <div>
              <p className="settings-row-label">Email Notifications</p>
              <p className="settings-row-desc">Receive updates and alerts via email</p>
            </div>
            <Toggle
              value={notifyEmail}
              onChange={v => handleToggle('notifyEmail', v)}
              disabled={isMutating}
            />
          </div>
        </section>

        {/* ── Playback ── */}
        <section className="settings-card settings-col-6">
          <div className="settings-card-header">
            <div className="settings-card-icon cyan">
              <span className="material-symbols-outlined" style={{ fontSize: 22 }}>speed</span>
            </div>
            <h2 className="settings-card-title">Playback</h2>
          </div>

          <div style={{ marginBottom: 16 }}>
            <label className="app-label" style={{ marginBottom: 10 }}>Default Quality</label>
            <div className="settings-segment">
              {QUALITY_OPTIONS.map(opt => (
                <button
                  key={opt.value}
                  type="button"
                  className={`settings-segment-btn${defaultQuality === opt.value ? ' active' : ''}`}
                  onClick={() => handleQuality(opt.value)}
                  disabled={isMutating}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>
        </section>

        {/* ── Privacy ── */}
        <section className="settings-card settings-col-12">
          <div className="settings-card-header">
            <div className="settings-card-icon cyan">
              <span className="material-symbols-outlined" style={{ fontSize: 22 }}>security</span>
            </div>
            <h2 className="settings-card-title">Privacy &amp; Visibility</h2>
          </div>

          <div className="settings-privacy-cards">
            <div className="settings-privacy-item">
              <div>
                <p className="settings-privacy-item-label">Incognito Mode</p>
                <p className="settings-privacy-item-desc">Watch history won&apos;t be recorded while active</p>
              </div>
              <Toggle
                value={incognitoEnabled}
                onChange={v => handleToggle('incognitoEnabled', v)}
                disabled={isMutating}
              />
            </div>

            <div className="settings-privacy-item">
              <div>
                <p className="settings-privacy-item-label">Private Profile</p>
                <p className="settings-privacy-item-desc">Control who can see your activity and liked videos</p>
              </div>
              <Toggle
                value={profilePrivate}
                onChange={v => handleToggle('profilePrivate', v)}
                disabled={isMutating}
              />
            </div>
          </div>
        </section>

        {/* ── Danger Zone ── */}
        <div className="settings-col-12">
          <div className="settings-danger-zone">
            <div>
              <p className="settings-danger-title">Danger Zone</p>
              <p className="settings-danger-desc">
                Deleting your account is permanent. Your data will be anonymized and you will be logged out.
              </p>
            </div>
            <button
              className="app-btn danger-outline"
              onClick={() => setDeleteOpen(true)}
              style={{ whiteSpace: 'nowrap' }}
            >
              <span className="material-symbols-outlined" style={{ fontSize: 16 }}>delete_forever</span>
              Delete Account
            </button>
          </div>
        </div>

      </div>

      {/* ── Modals ── */}
      <ProfileModal show={profileOpen} onHide={() => setProfileOpen(false)} />
      <DeleteAccountModal
        show={deleteOpen}
        onHide={() => setDeleteOpen(false)}
        settings={settings}
      />
    </div>
  )
}
