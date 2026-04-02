import React, { useState } from 'react'
import { Button, Form, Modal, Spinner, Alert } from 'react-bootstrap'
import { authApi } from '../../api/auth.js'
import { useAuth } from '../../hooks/useAuth.js'

export default function MFALoginModal({ show, pendingData, onHide }) {
  const { login } = useAuth()
  const [code, setCode] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function handleVerify() {
    if (!code || code.length !== 6) {
      setError('Please enter a valid 6-digit code')
      return
    }
    setLoading(true)
    setError('')
    try {
      const res = await authApi.mfa.verify(
        code,
        pendingData?.user?.email,
        pendingData?.sessionToken
      )
      login(res.data.jwt, res.data.user)
      handleClose()
    } catch (err) {
      setError(err.message || 'Verification failed')
    } finally {
      setLoading(false)
    }
  }

  function handleClose() {
    setCode('')
    setError('')
    onHide()
  }

  return (
    <Modal show={show} onHide={handleClose} centered>
      <Modal.Header closeButton>
        <Modal.Title>Two-Factor Authentication</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {error && <Alert variant="danger">{error}</Alert>}
        <Form.Group>
          <Form.Label>Enter 6-digit verification code</Form.Label>
          <Form.Control
            type="text"
            maxLength={6}
            value={code}
            onChange={e => setCode(e.target.value.replace(/\D/g, ''))}
            placeholder="000000"
            autoFocus
          />
        </Form.Group>
      </Modal.Body>
      <Modal.Footer>
        <Button variant="secondary" onClick={handleClose}>Cancel</Button>
        <Button variant="primary" onClick={handleVerify} disabled={loading}>
          {loading ? <Spinner size="sm" animation="border" /> : 'Verify'}
        </Button>
      </Modal.Footer>
    </Modal>
  )
}
