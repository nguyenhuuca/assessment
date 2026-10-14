import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { authApi } from '../../api/auth.js'
import { useAuth } from '../../hooks/useAuth.js'
import { useSettings } from '../../hooks/useSettings.js'
import PasswordInput from '../common/PasswordInput.jsx'

const MIN_LENGTH = 10
const MAX_BYTES = 72

// Server error code (or legacy code-in-message) -> translated text; falls back to the server text.
function mapPasswordError(err, t, i18n) {
  const code = err?.code || err?.message
  if (code && i18n.exists(`auth:errCodes.${code}`)) {
    return t(`errCodes.${code}`, { min: MIN_LENGTH, max: MAX_BYTES })
  }
  return err?.message || t('genericError')
}

function byteLength(s) {
  return new TextEncoder().encode(s).length
}

function passwordHints(password, email, t) {
  const local = (email || '').split('@')[0].toLowerCase()
  return [
    { key: 'len', text: t('hintMinLength', { min: MIN_LENGTH }), ok: password.length >= MIN_LENGTH },
    { key: 'max', text: t('hintMaxBytes', { max: MAX_BYTES }), ok: byteLength(password) <= MAX_BYTES },
    {
      key: 'email',
      text: t('hintNoEmail'),
      ok: !local || !password.toLowerCase().includes(local),
    },
  ]
}

function OtpField({ value, onChange, id }) {
  const { t } = useTranslation('auth')
  return (
    <div className="app-field" style={{ marginBottom: 0 }}>
      <label className="app-label" htmlFor={id}>{t('otpLabel')}</label>
      <input
        id={id}
        className="app-input"
        type="text"
        inputMode="numeric"
        autoComplete="one-time-code"
        maxLength={6}
        value={value}
        onChange={e => onChange(e.target.value.replace(/\D/g, ''))}
        placeholder="000000"
        style={{ letterSpacing: '0.3em', maxWidth: 160 }}
      />
    </div>
  )
}

function Hints({ password, email }) {
  const { t } = useTranslation('auth')
  if (!password) return null
  return (
    <ul style={{ listStyle: 'none', margin: 0, padding: 0, fontSize: 12 }}>
      {passwordHints(password, email, t).map(h => (
        <li key={h.key} style={{ color: h.ok ? 'var(--accent-cyan)' : 'var(--text-muted)' }}>
          {h.ok ? '✓' : '•'} {h.text}
        </li>
      ))}
    </ul>
  )
}

export default function SignInPanel() {
  const { t, i18n } = useTranslation('auth')
  const { user, login, updateUser } = useAuth()
  const { data: settings } = useSettings()
  const queryClient = useQueryClient()

  const passwordEnabled = settings?.passwordEnabled ?? user?.passwordEnabled ?? false
  const mfaEnabled = settings?.mfaEnabled ?? user?.mfaEnabled ?? false
  const featureDisabled = settings?.passwordLoginAvailable === false
  const email = user?.email || settings?.email || ''

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [otp, setOtp] = useState('')
  const [removeCurrent, setRemoveCurrent] = useState('')
  const [removeOtp, setRemoveOtp] = useState('')
  const [loading, setLoading] = useState(false)
  const [msg, setMsg] = useState(null) // { text, type }

  function patchSettings(passwordEnabledValue) {
    queryClient.setQueryData(['settings'], old => (old ? { ...old, passwordEnabled: passwordEnabledValue } : old))
    queryClient.invalidateQueries({ queryKey: ['settings'] })
  }

  async function handleSave(e) {
    e.preventDefault()
    setMsg(null)
    if (passwordEnabled && !currentPassword) {
      setMsg({ text: t('currentRequired'), type: 'error' })
      return
    }
    if (!newPassword) {
      setMsg({ text: t('newRequired'), type: 'error' })
      return
    }
    if (newPassword !== confirmPassword) {
      setMsg({ text: t('confirmMismatch'), type: 'error' })
      return
    }
    if (mfaEnabled && otp.length !== 6) {
      setMsg({ text: t('otpRequired'), type: 'error' })
      return
    }
    const body = { newPassword }
    if (passwordEnabled) body.currentPassword = currentPassword
    if (mfaEnabled) body.otp = otp

    setLoading(true)
    try {
      const res = await authApi.setPassword(body)
      const newJwt = res?.data?.jwt
      if (newJwt) login(newJwt, { ...user, passwordEnabled: true })
      else updateUser({ ...user, passwordEnabled: true })
      patchSettings(true)
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      setOtp('')
      setMsg({ text: t('passwordUpdated'), type: 'success' })
    } catch (err) {
      setMsg({ text: mapPasswordError(err, t, i18n), type: 'error' })
    } finally {
      setLoading(false)
    }
  }

  async function handleRemove(e) {
    e.preventDefault()
    setMsg(null)
    if (!removeCurrent) {
      setMsg({ text: t('currentRequired'), type: 'error' })
      return
    }
    if (mfaEnabled && removeOtp.length !== 6) {
      setMsg({ text: t('otpRequired'), type: 'error' })
      return
    }
    const body = { currentPassword: removeCurrent }
    if (mfaEnabled) body.otp = removeOtp

    setLoading(true)
    try {
      // Removing the password bumps credentials_version: the server returns a fresh JWT for this
      // session; without storing it the next request fails with TOKEN_REVOKED.
      const res = await authApi.removePassword(body)
      const newJwt = res?.data?.jwt
      if (newJwt) login(newJwt, { ...user, passwordEnabled: false })
      else updateUser({ ...user, passwordEnabled: false })
      patchSettings(false)
      setRemoveCurrent('')
      setRemoveOtp('')
      setMsg({ text: t('passwordRemoved'), type: 'success' })
    } catch (err) {
      setMsg({ text: mapPasswordError(err, t, i18n), type: 'error' })
    } finally {
      setLoading(false)
    }
  }

  if (featureDisabled) {
    return (
      <p style={{ fontSize: 13, color: 'var(--text-muted)' }}>
        {t('featureDisabled')}
      </p>
    )
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      {msg && <div className={`app-alert ${msg.type}`} role="alert">{msg.text}</div>}

      <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <h3 style={{ fontSize: 14, margin: 0 }}>
          {passwordEnabled ? t('changePassword') : t('setPassword')}
        </h3>
        {passwordEnabled && (
          <PasswordInput
            label={t('currentPassword')}
            value={currentPassword}
            onChange={e => setCurrentPassword(e.target.value)}
            autoComplete="current-password"
          />
        )}
        <PasswordInput
          label={t('newPassword')}
          value={newPassword}
          onChange={e => setNewPassword(e.target.value)}
          autoComplete="new-password"
        />
        <Hints password={newPassword} email={email} />
        <PasswordInput
          label={t('confirmPassword')}
          value={confirmPassword}
          onChange={e => setConfirmPassword(e.target.value)}
          autoComplete="new-password"
        />
        {mfaEnabled && <OtpField id="pw-save-otp" value={otp} onChange={setOtp} />}
        <button type="submit" className="app-btn primary" disabled={loading} aria-busy={loading} style={{ alignSelf: 'flex-start' }}>
          {passwordEnabled ? t('changePassword') : t('setPassword')}
        </button>
      </form>

      {passwordEnabled && (
        <form onSubmit={handleRemove} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <h3 style={{ fontSize: 14, margin: 0 }}>{t('removePassword')}</h3>
          <p style={{ fontSize: 12, color: 'var(--text-muted)', margin: 0 }}>
            {t('removeHint')}
          </p>
          <PasswordInput
            label={t('currentToRemove')}
            value={removeCurrent}
            onChange={e => setRemoveCurrent(e.target.value)}
            autoComplete="current-password"
          />
          {mfaEnabled && <OtpField id="pw-remove-otp" value={removeOtp} onChange={setRemoveOtp} />}
          <button type="submit" className="app-btn danger" disabled={loading} aria-busy={loading} style={{ alignSelf: 'flex-start' }}>
            {t('removePassword')}
          </button>
        </form>
      )}
    </div>
  )
}
