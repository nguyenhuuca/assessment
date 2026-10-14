import React, { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { useTranslation } from 'react-i18next'
import { authApi } from '../../api/auth.js'
import PasswordInput from '../common/PasswordInput.jsx'

/**
 * Email + password sign-in dialog. Rendered through a portal so it is not clipped by the
 * header / mobile popover. `onSuccess(data)` receives the login payload (JWT or MFA_REQUIRED);
 * `onForgot(email)` hands the typed email back to the magic-link form.
 */
export default function PasswordLoginModal({ show, initialEmail = '', onHide, onSuccess, onForgot }) {
  const { t } = useTranslation(['auth', 'common'])
  const [email,    setEmail]    = useState(initialEmail)
  const [password, setPassword] = useState('')
  const [loading,  setLoading]  = useState(false)
  const [error,    setError]    = useState(null)

  useEffect(() => {
    if (show) {
      setEmail(initialEmail)
      setPassword('')
      setError(null)
    }
  }, [show, initialEmail])

  useEffect(() => {
    if (!show) return undefined
    const onKey = e => { if (e.key === 'Escape') onHide() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [show, onHide])

  if (!show) return null

  async function handleSubmit(e) {
    e.preventDefault()
    if (!email.trim() || !password) return
    setLoading(true)
    setError(null)
    try {
      const res = await authApi.login(email.trim(), password)
      setPassword('')
      onSuccess(res.data)
    } catch (err) {
      // Never reveal whether the email exists or the password was wrong
      setError(err?.status === 429 ? t('rateLimited') : t('invalidCredentials'))
    } finally {
      setLoading(false)
    }
  }

  return createPortal(
    <div className="app-modal-backdrop" onClick={e => { if (e.target === e.currentTarget) onHide() }}>
      <div className="app-modal" role="dialog" aria-modal="true" aria-labelledby="pw-login-title" style={{ width: 400 }}>
        <div className="app-modal-header">
          <span className="app-modal-title" id="pw-login-title">{t('passwordLogin')}</span>
          <button type="button" className="app-modal-close" onClick={onHide} aria-label={t('common:close')}>
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>close</span>
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="app-modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {error && <div className="app-alert error" role="alert">{error}</div>}
            <div className="app-field" style={{ marginBottom: 0 }}>
              <label className="app-label" htmlFor="pw-login-email">{t('emailLabel')}</label>
              <input
                id="pw-login-email"
                className="app-input"
                type="email"
                autoComplete="email"
                value={email}
                onChange={e => setEmail(e.target.value)}
                required
                autoFocus={!initialEmail}
                style={{ width: '100%', boxSizing: 'border-box' }}
              />
            </div>
            <PasswordInput
              label={t('passwordLabel')}
              value={password}
              onChange={e => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
            <button
              type="button"
              onClick={() => onForgot(email.trim())}
              style={{
                alignSelf: 'flex-start', background: 'none', border: 'none', padding: 0, cursor: 'pointer',
                color: 'var(--accent-cyan)', fontSize: 12, fontWeight: 600,
              }}
            >
              {t('forgotPassword')}
            </button>
            <p style={{ margin: 0, fontSize: 12, color: 'var(--text-muted)' }}>
              {t('noPasswordYet')}
            </p>
          </div>

          <div className="app-modal-footer">
            <button type="button" className="app-btn secondary" onClick={onHide}>{t('common:cancel')}</button>
            <button type="submit" className="app-btn primary" disabled={loading}>
              {loading ? t('loggingIn') : t('common:login')}
            </button>
          </div>
        </form>
      </div>
    </div>,
    document.body,
  )
}
