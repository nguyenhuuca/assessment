import React, { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { authApi } from '../../api/auth.js'
import { useAuth } from '../../hooks/useAuth.js'
import { useSettings } from '../../hooks/useSettings.js'
import PasswordInput from '../common/PasswordInput.jsx'

const MIN_LENGTH = 10
const MAX_BYTES = 72

const ERROR_MESSAGES = {
  CURRENT_PASSWORD_INVALID: 'Mật khẩu hiện tại không đúng',
  PASSWORD_TOO_SHORT: `Mật khẩu phải có ít nhất ${MIN_LENGTH} ký tự`,
  PASSWORD_TOO_LONG: `Mật khẩu quá dài (tối đa ${MAX_BYTES} byte)`,
  PASSWORD_CONTAINS_EMAIL: 'Mật khẩu không được chứa phần tên của email',
  PASSWORD_TOO_COMMON: 'Mật khẩu quá phổ biến, vui lòng chọn mật khẩu khác',
}

function mapPasswordError(err) {
  return ERROR_MESSAGES[err?.message] || err?.message || 'Đã có lỗi xảy ra, vui lòng thử lại'
}

function byteLength(s) {
  return new TextEncoder().encode(s).length
}

function passwordHints(password, email) {
  const local = (email || '').split('@')[0].toLowerCase()
  return [
    { key: 'len', text: `Ít nhất ${MIN_LENGTH} ký tự`, ok: password.length >= MIN_LENGTH },
    { key: 'max', text: `Tối đa ${MAX_BYTES} byte`, ok: byteLength(password) <= MAX_BYTES },
    {
      key: 'email',
      text: 'Không chứa phần tên của email',
      ok: !local || !password.toLowerCase().includes(local),
    },
  ]
}

function OtpField({ value, onChange, id }) {
  return (
    <div className="app-field" style={{ marginBottom: 0 }}>
      <label className="app-label" htmlFor={id}>Mã xác thực MFA (6 chữ số)</label>
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
  if (!password) return null
  return (
    <ul style={{ listStyle: 'none', margin: 0, padding: 0, fontSize: 12 }}>
      {passwordHints(password, email).map(h => (
        <li key={h.key} style={{ color: h.ok ? 'var(--accent-cyan)' : 'var(--text-muted)' }}>
          {h.ok ? '✓' : '•'} {h.text}
        </li>
      ))}
    </ul>
  )
}

export default function SignInPanel() {
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
      setMsg({ text: 'Vui lòng nhập mật khẩu hiện tại', type: 'error' })
      return
    }
    if (!newPassword) {
      setMsg({ text: 'Vui lòng nhập mật khẩu mới', type: 'error' })
      return
    }
    if (newPassword !== confirmPassword) {
      setMsg({ text: 'Mật khẩu xác nhận không khớp', type: 'error' })
      return
    }
    if (mfaEnabled && otp.length !== 6) {
      setMsg({ text: 'Vui lòng nhập mã xác thực gồm 6 chữ số', type: 'error' })
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
      setMsg({ text: 'Đã cập nhật mật khẩu. Các thiết bị khác đã được đăng xuất.', type: 'success' })
    } catch (err) {
      setMsg({ text: mapPasswordError(err), type: 'error' })
    } finally {
      setLoading(false)
    }
  }

  async function handleRemove(e) {
    e.preventDefault()
    setMsg(null)
    if (!removeCurrent) {
      setMsg({ text: 'Vui lòng nhập mật khẩu hiện tại', type: 'error' })
      return
    }
    if (mfaEnabled && removeOtp.length !== 6) {
      setMsg({ text: 'Vui lòng nhập mã xác thực gồm 6 chữ số', type: 'error' })
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
      setMsg({ text: 'Đã gỡ mật khẩu. Bạn vẫn có thể đăng nhập bằng link.', type: 'success' })
    } catch (err) {
      setMsg({ text: mapPasswordError(err), type: 'error' })
    } finally {
      setLoading(false)
    }
  }

  if (featureDisabled) {
    return (
      <p style={{ fontSize: 13, color: 'var(--text-muted)' }}>
        Đăng nhập bằng mật khẩu hiện chưa được bật.
      </p>
    )
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      {msg && <div className={`app-alert ${msg.type}`} role="alert">{msg.text}</div>}

      <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <h3 style={{ fontSize: 14, margin: 0 }}>
          {passwordEnabled ? 'Đổi mật khẩu' : 'Đặt mật khẩu'}
        </h3>
        {passwordEnabled && (
          <PasswordInput
            label="Mật khẩu hiện tại"
            value={currentPassword}
            onChange={e => setCurrentPassword(e.target.value)}
            autoComplete="current-password"
          />
        )}
        <PasswordInput
          label="Mật khẩu mới"
          value={newPassword}
          onChange={e => setNewPassword(e.target.value)}
          autoComplete="new-password"
        />
        <Hints password={newPassword} email={email} />
        <PasswordInput
          label="Nhập lại mật khẩu mới"
          value={confirmPassword}
          onChange={e => setConfirmPassword(e.target.value)}
          autoComplete="new-password"
        />
        {mfaEnabled && <OtpField id="pw-save-otp" value={otp} onChange={setOtp} />}
        <button type="submit" className="app-btn primary" disabled={loading} style={{ alignSelf: 'flex-start' }}>
          {passwordEnabled ? 'Đổi mật khẩu' : 'Đặt mật khẩu'}
        </button>
      </form>

      {passwordEnabled && (
        <form onSubmit={handleRemove} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <h3 style={{ fontSize: 14, margin: 0 }}>Gỡ mật khẩu</h3>
          <p style={{ fontSize: 12, color: 'var(--text-muted)', margin: 0 }}>
            Sau khi gỡ, bạn chỉ đăng nhập bằng link gửi qua email.
          </p>
          <PasswordInput
            label="Mật khẩu hiện tại để gỡ"
            value={removeCurrent}
            onChange={e => setRemoveCurrent(e.target.value)}
            autoComplete="current-password"
          />
          {mfaEnabled && <OtpField id="pw-remove-otp" value={removeOtp} onChange={setRemoveOtp} />}
          <button type="submit" className="app-btn danger" disabled={loading} style={{ alignSelf: 'flex-start' }}>
            Gỡ mật khẩu
          </button>
        </form>
      )}
    </div>
  )
}
