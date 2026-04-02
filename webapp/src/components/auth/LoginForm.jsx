import React, { useState } from 'react'
import { Button, Form, Spinner } from 'react-bootstrap'
import { authApi } from '../../api/auth.js'
import { useAuth } from '../../hooks/useAuth.js'

const STATUS = {
  MFA_REQUIRED: 'MFA_REQUIRED',
  INVITED_SEND: 'INVITED_SEND',
}

export default function LoginForm({ onMfaRequired }) {
  const { login } = useAuth()
  const [email, setEmail] = useState('')
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
        setMessage({ text: "We've sent you an email. Please check your inbox", type: 'success' })
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
    <Form onSubmit={handleSubmit} className="d-flex align-items-center gap-2">
      <Form.Control
        type="email"
        placeholder="Enter your email"
        value={email}
        onChange={e => setEmail(e.target.value)}
        required
        size="sm"
        style={{ maxWidth: 220 }}
      />
      <Button type="submit" variant="primary" size="sm" disabled={loading}>
        {loading ? <Spinner size="sm" animation="border" /> : 'Login'}
      </Button>
      {message && (
        <span className={message.type === 'error' ? 'text-danger' : 'text-success'} style={{ fontSize: '0.85rem' }}>
          {message.text}
        </span>
      )}
    </Form>
  )
}
