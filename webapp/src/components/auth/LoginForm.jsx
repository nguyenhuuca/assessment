import React, { useState } from 'react'
import { authApi } from '../../api/auth.js'
import { useAuth } from '../../hooks/useAuth.js'

const STATUS = {
  MFA_REQUIRED: 'MFA_REQUIRED',
  INVITED_SEND: 'INVITED_SEND',
}

export default function LoginForm({ onMfaRequired }) {
  const { login } = useAuth()
  const [email,   setEmail]   = useState('')
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState(null) // { text, type: 'error'|'success' }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!email.trim()) return
    setLoading(true)
    setMessage(null)
    try {
      const res = await authApi.join(email.trim())
      const data = res.data
      if (data.action === STATUS.INVITED_SEND) {
        setMessage({ text: "Check your inbox!", type: 'success' })
      } else if (data.action === STATUS.MFA_REQUIRED) {
        localStorage.setItem('user', JSON.stringify(data.user))
        onMfaRequired?.(data)
      } else {
        login(data.jwt, data.user)
      }
    } catch (err) {
      setMessage({ text: err.message || 'Login failed', type: 'error' })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ position: 'relative' }}>
      <form onSubmit={handleSubmit} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <input
          type="email"
          placeholder="Enter your email"
          value={email}
          onChange={e => setEmail(e.target.value)}
          required
          style={{
            background: 'var(--bg-high)',
            border: '1px solid var(--border-subtle)',
            borderRadius: 8,
            color: 'var(--text)',
            fontSize: 13,
            padding: '6px 12px',
            width: 200,
            outline: 'none',
            transition: 'border-color 0.15s ease',
          }}
          onFocus={e => e.target.style.borderColor = 'var(--accent-cyan)'}
          onBlur={e => e.target.style.borderColor = 'var(--border-subtle)'}
        />
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

      {/* Message floats below without shifting the form */}
      {message && (
        <span style={{
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
    </div>
  )
}
