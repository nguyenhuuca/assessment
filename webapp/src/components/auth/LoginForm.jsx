import React, { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { authApi } from '../../api/auth.js'
import { useAuth } from '../../hooks/useAuth.js'
import PasswordInput from '../common/PasswordInput.jsx'

const STATUS = {
  MFA_REQUIRED: 'MFA_REQUIRED',
  INVITED_SEND: 'INVITED_SEND',
}

const TAB_LINK = 'link'
const TAB_PASSWORD = 'password'

const MSG_INVALID = 'Email hoặc mật khẩu không đúng'
const MSG_RATE_LIMIT = 'Bạn thử quá nhiều lần, vui lòng đợi'

const inputStyle = {
  background: 'var(--bg-high)',
  border: '1px solid var(--border-subtle)',
  borderRadius: 8,
  color: 'var(--text)',
  fontSize: 13,
  padding: '6px 12px',
  width: 200,
  outline: 'none',
  transition: 'border-color 0.15s ease',
}

function tabStyle(active) {
  return {
    background: 'none',
    border: 'none',
    borderBottom: `2px solid ${active ? 'var(--accent-cyan)' : 'transparent'}`,
    color: active ? 'var(--accent-cyan)' : 'var(--text-muted)',
    fontSize: 11,
    fontWeight: 700,
    padding: '2px 6px',
    cursor: 'pointer',
    whiteSpace: 'nowrap',
  }
}

export default function LoginForm({ onMfaRequired }) {
  const { login } = useAuth()
  const [tab,      setTab]      = useState(TAB_LINK)
  const [email,    setEmail]    = useState('')
  const [password, setPassword] = useState('')
  const [loading,  setLoading]  = useState(false)
  const [message,  setMessage]  = useState(null) // { text, type: 'error'|'success' }

  const { data: options } = useQuery({
    queryKey: ['auth-options'],
    queryFn: async () => {
      const res = await authApi.authOptions()
      return res?.data ?? res
    },
    staleTime: Infinity,
    retry: false,
  })
  const passwordAvailable = options?.passwordLoginAvailable === true
  const activeTab = passwordAvailable ? tab : TAB_LINK

  function handleAuthResult(data) {
    if (data.action === STATUS.MFA_REQUIRED) {
      localStorage.setItem('user', JSON.stringify(data.user))
      onMfaRequired?.(data)
    } else {
      login(data.jwt, data.user)
    }
  }

  async function handleLinkSubmit() {
    const res = await authApi.join(email.trim())
    const data = res.data
    if (data.action === STATUS.INVITED_SEND) {
      setMessage({ text: 'Check your inbox!', type: 'success' })
    } else {
      handleAuthResult(data)
    }
  }

  async function handlePasswordSubmit() {
    try {
      const res = await authApi.login(email.trim(), password)
      handleAuthResult(res.data)
    } catch (err) {
      if (err?.status === 429) {
        setMessage({ text: MSG_RATE_LIMIT, type: 'error' })
      } else {
        // Never reveal whether the email exists or the password was wrong
        setMessage({ text: MSG_INVALID, type: 'error' })
      }
    }
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!email.trim()) return
    if (activeTab === TAB_PASSWORD && !password) return
    setLoading(true)
    setMessage(null)
    try {
      if (activeTab === TAB_PASSWORD) {
        await handlePasswordSubmit()
      } else {
        await handleLinkSubmit()
      }
    } catch (err) {
      setMessage({ text: err.message || 'Login failed', type: 'error' })
    } finally {
      setLoading(false)
    }
  }

  function switchTab(next) {
    setTab(next)
    setMessage(null)
    setPassword('')
  }

  return (
    <div style={{ position: 'relative' }}>
      {passwordAvailable && (
        <div role="tablist" style={{ display: 'flex', gap: 4, marginBottom: 2 }}>
          <button
            type="button" role="tab" aria-selected={activeTab === TAB_LINK}
            style={tabStyle(activeTab === TAB_LINK)}
            onClick={() => switchTab(TAB_LINK)}
          >
            Link đăng nhập
          </button>
          <button
            type="button" role="tab" aria-selected={activeTab === TAB_PASSWORD}
            style={tabStyle(activeTab === TAB_PASSWORD)}
            onClick={() => switchTab(TAB_PASSWORD)}
          >
            Mật khẩu
          </button>
        </div>
      )}

      <form onSubmit={handleSubmit} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <input
          type="email"
          placeholder="Enter your email"
          aria-label="Email"
          autoComplete="email"
          value={email}
          onChange={e => setEmail(e.target.value)}
          required
          style={{ ...inputStyle, width: activeTab === TAB_PASSWORD ? 160 : 200 }}
          onFocus={e => e.target.style.borderColor = 'var(--accent-cyan)'}
          onBlur={e => e.target.style.borderColor = 'var(--border-subtle)'}
        />
        {activeTab === TAB_PASSWORD && (
          <div style={{ width: 160 }}>
            <PasswordInput
              ariaLabel="Mật khẩu"
              value={password}
              onChange={e => setPassword(e.target.value)}
              autoComplete="current-password"
              placeholder="Mật khẩu"
              required
            />
          </div>
        )}
        <button
          type="submit"
          disabled={loading}
          style={{
            background: 'var(--accent-cyan)',
            color: '#003035',
            border: 'none',
            borderRadius: 8,
            fontSize: 13,
            fontWeight: 700,
            padding: '6px 16px',
            width: 70,
            justifyContent: 'center',
            cursor: loading ? 'not-allowed' : 'pointer',
            opacity: loading ? 0.7 : 1,
            whiteSpace: 'nowrap',
            transition: 'opacity 0.15s ease, transform 0.12s ease',
            display: 'flex', alignItems: 'center', gap: 6,
          }}
          onMouseEnter={e => { if (!loading) e.currentTarget.style.opacity = '0.85' }}
          onMouseLeave={e => { e.currentTarget.style.opacity = loading ? '0.7' : '1' }}
        >
          {loading
            ? <span style={{ width: 14, height: 14, border: '2px solid rgba(0,48,53,0.3)', borderTopColor: '#003035', borderRadius: '50%', display: 'inline-block', animation: 'spin 0.7s linear infinite' }} />
            : 'Login'}
        </button>
      </form>

      {/* Messages float below without shifting the form */}
      {message && (
        <span role="alert" style={{
          position: 'absolute', top: '100%', right: 0,
          marginTop: 4,
          fontSize: 11, fontWeight: 600,
          color: message.type === 'error' ? '#ff6b6b' : 'var(--accent-cyan)',
          whiteSpace: 'nowrap',
          pointerEvents: 'none',
        }}>
          {message.type === 'success' ? '✓ ' : '⚠ '}{message.text}
        </span>
      )}

      {activeTab === TAB_PASSWORD && (
        <div style={{
          position: 'absolute', top: '100%', right: 0,
          marginTop: message ? 22 : 4,
          maxWidth: 300, fontSize: 11, color: 'var(--text-muted)',
          display: 'flex', flexDirection: 'column', gap: 2, alignItems: 'flex-end',
          textAlign: 'right',
        }}>
          <button
            type="button"
            onClick={() => switchTab(TAB_LINK)}
            style={{
              background: 'none', border: 'none', padding: 0, cursor: 'pointer',
              color: 'var(--accent-cyan)', fontSize: 11, fontWeight: 600,
            }}
          >
            Quên mật khẩu? Gửi link đăng nhập
          </button>
          <span>Chưa có mật khẩu? Đăng nhập bằng link rồi đặt mật khẩu trong Cài đặt</span>
        </div>
      )}
    </div>
  )
}
