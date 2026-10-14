import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { formatDate } from '../../i18n/format.js'
import LanguageSwitcher from '../common/LanguageSwitcher.jsx'
import { useAuth } from '../../hooks/useAuth.js'
import { useSettings, useUpdateSettings } from '../../hooks/useSettings.js'
import ProfileModal from '../auth/ProfileModal.jsx'
import DeleteAccountModal from './DeleteAccountModal.jsx'

const QUALITY_OPTIONS = [
  { value: 'AUTO',  labelKey: 'qualityAuto' },
  { value: '1080P', label: '1080p' },
  { value: '4K',    label: '4K' },
]

const Spinner = () => {
  const { t } = useTranslation('settings')
  return (
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
    {t('loading')}
  </div>
  )
}

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
  const { t } = useTranslation('settings')
  const { user } = useAuth()
  const { data: settings, isLoading, isError, error: loadError } = useSettings()
  const updateSettings = useUpdateSettings()

  const [profileOpen, setProfileOpen] = useState(false)
  const [profileTab, setProfileTab] = useState('info')

  function openProfile(tab) {
    setProfileTab(tab)
    setProfileOpen(true)
  }
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [patchError, setPatchError] = useState(null)

  function handleToggle(field, value) {
    setPatchError(null)
    updateSettings.mutate(
      { [field]: value },
      {
        onError: (err) => {
          setPatchError(err?.message || t('saveFailed'))
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
          setPatchError(err?.message || t('saveQualityFailed'))
        },
      }
    )
  }

  if (isLoading) return <div className="settings-root"><Spinner /></div>

  if (isError) {
    return (
      <div className="settings-root">
        <div className="app-alert error" style={{ marginTop: 20 }}>
          {loadError?.message || t('loadFailed')}
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
        <h1 className="settings-title">{t('title')}</h1>
        <p className="settings-subtitle">{t('subtitle')}</p>
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
            <h2 className="settings-card-title">{t('accountIdentity')}</h2>
          </div>

          <div className="settings-identity-fields">
            <div>
              <label className="app-label">{t('emailAddress')}</label>
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
                {passwordEnabled ? t('password') : t('signInMethod')}
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
                  {t('magicLink')}
                </div>
              )}
            </div>
          </div>

          <div className="settings-identity-action">
            {passwordEnabled && (
              <button
                className="app-btn secondary"
                onClick={() => openProfile('signin')}
              >
                <span className="material-symbols-outlined" style={{ fontSize: 16 }}>lock</span>
                {t('changePassword')}
              </button>
            )}
            {!passwordEnabled && (
              <button
                className="app-btn secondary"
                onClick={() => openProfile('signin')}
              >
                <span className="material-symbols-outlined" style={{ fontSize: 16 }}>link</span>
                {t('manageSignIn')}
              </button>
            )}
            <button
              className="app-btn secondary"
              onClick={() => openProfile('mfa')}
            >
              <span className="material-symbols-outlined" style={{ fontSize: 16 }}>
                {mfaEnabled ? 'gpp_good' : 'gpp_maybe'}
              </span>
              {mfaEnabled ? t('mfaEnabled') : t('setUpMfa')}
            </button>
          </div>
        </section>

        {/* ── Account Status (conditional) ── */}
        {accountStatus ? (
          <aside className="settings-status-card settings-col-4">
            <p className="settings-status-label">{t('accountStatus')}</p>
            <div className="settings-status-plan">
              <span className="settings-status-dot" />
              {accountStatus.plan || t('planFree')}
            </div>
            {accountStatus.renewsAt ? (
              <p className="settings-status-renew">
                {t('renewsOn')}{' '}
                <strong style={{ color: 'var(--text)' }}>
                  {formatDate(accountStatus.renewsAt)}
                </strong>.
              </p>
            ) : (
              <p className="settings-status-renew">{t('noRenewal')}</p>
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
            <h2 className="settings-card-title">{t('notifications')}</h2>
          </div>

          <div className="settings-row">
            <div>
              <p className="settings-row-label">{t('newContentAlerts')}</p>
              <p className="settings-row-desc">{t('newContentDesc')}</p>
            </div>
            <Toggle
              value={notifyNewContent}
              onChange={v => handleToggle('notifyNewContent', v)}
              disabled={isMutating}
            />
          </div>

          <div className="settings-row">
            <div>
              <p className="settings-row-label">{t('emailNotifications')}</p>
              <p className="settings-row-desc">{t('emailNotificationsDesc')}</p>
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
            <h2 className="settings-card-title">{t('playback')}</h2>
          </div>

          <div style={{ marginBottom: 16 }}>
            <label className="app-label" style={{ marginBottom: 10 }}>{t('defaultQuality')}</label>
            <div className="settings-segment">
              {QUALITY_OPTIONS.map(opt => (
                <button
                  key={opt.value}
                  type="button"
                  className={`settings-segment-btn${defaultQuality === opt.value ? ' active' : ''}`}
                  onClick={() => handleQuality(opt.value)}
                  disabled={isMutating}
                >
                  {opt.labelKey ? t(opt.labelKey) : opt.label}
                </button>
              ))}
            </div>
          </div>
        </section>

        {/* ── Language ── */}
        <section className="settings-card settings-col-12">
          <div className="settings-card-header">
            <div className="settings-card-icon cyan">
              <span className="material-symbols-outlined" style={{ fontSize: 22 }}>language</span>
            </div>
            <h2 className="settings-card-title">{t('language')}</h2>
          </div>
          <div className="settings-row">
            <div>
              <p className="settings-row-label">{t('languageRow')}</p>
              <p className="settings-row-desc">{t('languageDesc')}</p>
            </div>
            <LanguageSwitcher />
          </div>
        </section>

        {/* ── Privacy ── */}
        <section className="settings-card settings-col-12">
          <div className="settings-card-header">
            <div className="settings-card-icon cyan">
              <span className="material-symbols-outlined" style={{ fontSize: 22 }}>security</span>
            </div>
            <h2 className="settings-card-title">{t('privacy')}</h2>
          </div>

          <div className="settings-privacy-cards">
            <div className="settings-privacy-item">
              <div>
                <p className="settings-privacy-item-label">{t('incognito')}</p>
                <p className="settings-privacy-item-desc">{t('incognitoDesc')}</p>
              </div>
              <Toggle
                value={incognitoEnabled}
                onChange={v => handleToggle('incognitoEnabled', v)}
                disabled={isMutating}
              />
            </div>

            <div className="settings-privacy-item">
              <div>
                <p className="settings-privacy-item-label">{t('privateProfile')}</p>
                <p className="settings-privacy-item-desc">{t('privateProfileDesc')}</p>
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
              <p className="settings-danger-title">{t('dangerZone')}</p>
              <p className="settings-danger-desc">
                {t('dangerDesc')}
              </p>
            </div>
            <button
              className="app-btn danger-outline"
              onClick={() => setDeleteOpen(true)}
              style={{ whiteSpace: 'nowrap' }}
            >
              <span className="material-symbols-outlined" style={{ fontSize: 16 }}>delete_forever</span>
              {t('deleteAccount')}
            </button>
          </div>
        </div>

      </div>

      {/* ── Modals ── */}
      <ProfileModal show={profileOpen} initialTab={profileTab} onHide={() => setProfileOpen(false)} />
      <DeleteAccountModal
        show={deleteOpen}
        onHide={() => setDeleteOpen(false)}
        settings={settings}
      />
    </div>
  )
}
