import React, { useState } from 'react'
import { Alert, Button, Form, Modal, Nav, Spinner, Tab } from 'react-bootstrap'
import { authApi } from '../../api/auth.js'
import { useAuth } from '../../hooks/useAuth.js'

export default function ProfileModal({ show, onHide }) {
  const { user, updateUser } = useAuth()
  const [mfaLoading, setMfaLoading] = useState(false)
  const [mfaMsg, setMfaMsg] = useState(null) // { text, type }
  const [qrCode, setQrCode] = useState(null)
  const [secret, setSecret] = useState(null)
  const [setupCode, setSetupCode] = useState('')
  const [disableCode, setDisableCode] = useState('')

  async function handleSetupMFA() {
    setMfaLoading(true)
    setMfaMsg(null)
    try {
      const res = await authApi.mfa.setup(user?.email)
      setQrCode(res.data.qrCode)
      setSecret(res.data.secret)
    } catch (err) {
      setMfaMsg({ text: err.message, type: 'danger' })
    } finally {
      setMfaLoading(false)
    }
  }

  async function handleEnableMFA() {
    if (!setupCode || setupCode.length !== 6) {
      setMfaMsg({ text: 'Please enter a valid 6-digit code', type: 'danger' })
      return
    }
    setMfaLoading(true)
    setMfaMsg(null)
    try {
      await authApi.mfa.enable(setupCode, user?.email, secret)
      const updated = { ...user, mfaEnabled: true }
      updateUser(updated)
      setMfaMsg({ text: 'MFA has been enabled successfully!', type: 'success' })
      setQrCode(null)
      setSetupCode('')
    } catch (err) {
      setMfaMsg({ text: err.message, type: 'danger' })
    } finally {
      setMfaLoading(false)
    }
  }

  async function handleDisableMFA() {
    if (!disableCode || disableCode.length !== 6) {
      setMfaMsg({ text: 'Please enter a valid 6-digit code', type: 'danger' })
      return
    }
    setMfaLoading(true)
    setMfaMsg(null)
    try {
      await authApi.mfa.disable(disableCode, user?.email)
      const updated = { ...user, mfaEnabled: false }
      updateUser(updated)
      setMfaMsg({ text: 'MFA has been disabled successfully!', type: 'success' })
      setDisableCode('')
    } catch (err) {
      setMfaMsg({ text: err.message, type: 'danger' })
    } finally {
      setMfaLoading(false)
    }
  }

  function handleClose() {
    setMfaMsg(null)
    setQrCode(null)
    setSecret(null)
    setSetupCode('')
    setDisableCode('')
    onHide()
  }

  return (
    <Modal show={show} onHide={handleClose} size="lg" centered>
      <Modal.Header closeButton>
        <Modal.Title>Profile</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <Tab.Container defaultActiveKey="info">
          <Nav variant="tabs" className="mb-3">
            <Nav.Item><Nav.Link eventKey="info">User Info</Nav.Link></Nav.Item>
            <Nav.Item><Nav.Link eventKey="mfa">MFA Settings</Nav.Link></Nav.Item>
          </Nav>
          <Tab.Content>
            <Tab.Pane eventKey="info">
              <p><strong>Email:</strong> {user?.email}</p>
              <p><strong>Member Since:</strong> {new Date().toLocaleDateString()}</p>
              <p><strong>Last Login:</strong> {new Date().toLocaleDateString()}</p>
            </Tab.Pane>
            <Tab.Pane eventKey="mfa">
              {mfaMsg && <Alert variant={mfaMsg.type}>{mfaMsg.text}</Alert>}
              <p>MFA Status: <strong>{user?.mfaEnabled ? 'Enabled' : 'Disabled'}</strong></p>
              {!user?.mfaEnabled ? (
                <div>
                  {!qrCode ? (
                    <Button variant="primary" onClick={handleSetupMFA} disabled={mfaLoading}>
                      {mfaLoading ? <Spinner size="sm" animation="border" /> : 'Setup MFA'}
                    </Button>
                  ) : (
                    <div>
                      <p>Scan this QR code with your authenticator app:</p>
                      <img src={`data:image/png;base64,${qrCode}`} alt="MFA QR Code" />
                      <Form.Group className="mt-3">
                        <Form.Label>Enter verification code to enable</Form.Label>
                        <Form.Control
                          type="text"
                          maxLength={6}
                          value={setupCode}
                          onChange={e => setSetupCode(e.target.value.replace(/\D/g, ''))}
                          placeholder="000000"
                        />
                      </Form.Group>
                      <Button className="mt-2" variant="success" onClick={handleEnableMFA} disabled={mfaLoading}>
                        {mfaLoading ? <Spinner size="sm" animation="border" /> : 'Enable MFA'}
                      </Button>
                    </div>
                  )}
                </div>
              ) : (
                <div>
                  <Form.Group>
                    <Form.Label>Enter code to disable MFA</Form.Label>
                    <Form.Control
                      type="text"
                      maxLength={6}
                      value={disableCode}
                      onChange={e => setDisableCode(e.target.value.replace(/\D/g, ''))}
                      placeholder="000000"
                    />
                  </Form.Group>
                  <Button className="mt-2" variant="danger" onClick={handleDisableMFA} disabled={mfaLoading}>
                    {mfaLoading ? <Spinner size="sm" animation="border" /> : 'Disable MFA'}
                  </Button>
                </div>
              )}
            </Tab.Pane>
          </Tab.Content>
        </Tab.Container>
      </Modal.Body>
      <Modal.Footer>
        <Button variant="secondary" onClick={handleClose}>Close</Button>
      </Modal.Footer>
    </Modal>
  )
}
