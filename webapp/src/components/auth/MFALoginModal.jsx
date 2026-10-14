import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, Form, Modal, Spinner, Alert } from 'react-bootstrap'
import { authApi } from '../../api/auth.js'
import { useAuth } from '../../hooks/useAuth.js'

export default function MFALoginModal({ show, pendingData, onHide }) {
  const { t } = useTranslation(['auth', 'common'])
  const { login } = useAuth()
  const [code, setCode] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function handleVerify() {
    if (!code || code.length !== 6) {
      setError(t('mfaCodeInvalid'))
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
      setError(err.message || t('verifyFailed'))
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
      <Modal.Header closeButton closeLabel={t('common:close')}>
        <Modal.Title>{t('mfaTitle')}</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {error && <Alert variant="danger">{error}</Alert>}
        <Form.Group>
          <Form.Label>{t('mfaCodeLabel')}</Form.Label>
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
        <Button variant="secondary" onClick={handleClose}>{t('common:cancel')}</Button>
        <Button variant="primary" onClick={handleVerify} disabled={loading}>
          {loading ? <Spinner size="sm" animation="border" /> : t('verify')}
        </Button>
      </Modal.Footer>
    </Modal>
  )
}
