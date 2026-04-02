import React, { useState } from 'react'
import { Alert, Button, Form, Modal, Spinner } from 'react-bootstrap'
import { useQueryClient } from '@tanstack/react-query'
import { videosApi } from '../../api/videos.js'

const EMPTY_FORM = { url: '', title: '', description: '', isPrivate: false }

export default function ShareModal({ show, onHide }) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState(EMPTY_FORM)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  function handleChange(e) {
    const { name, value, type, checked } = e.target
    setForm(f => ({ ...f, [name]: type === 'checkbox' ? checked : value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!form.url.trim()) return
    setLoading(true)
    setError('')
    try {
      await videosApi.share({
        url: form.url.trim(),
        title: form.title.trim(),
        description: form.description.trim(),
        isPrivate: form.isPrivate,
      })
      // Invalidate all video queries so feeds refresh
      queryClient.invalidateQueries({ queryKey: ['videos'] })
      handleClose()
    } catch (err) {
      setError(err.message || 'Failed to share video')
    } finally {
      setLoading(false)
    }
  }

  function handleClose() {
    setForm(EMPTY_FORM)
    setError('')
    onHide()
  }

  return (
    <Modal show={show} onHide={handleClose} centered>
      <Modal.Header closeButton>
        <Modal.Title>Share a Video</Modal.Title>
      </Modal.Header>
      <Form onSubmit={handleSubmit}>
        <Modal.Body>
          {error && <Alert variant="danger">{error}</Alert>}
          <Form.Group className="mb-3">
            <Form.Label>YouTube URL *</Form.Label>
            <Form.Control
              type="url"
              name="url"
              value={form.url}
              onChange={handleChange}
              placeholder="https://www.youtube.com/watch?v=..."
              required
            />
          </Form.Group>
          <Form.Group className="mb-3">
            <Form.Label>Title</Form.Label>
            <Form.Control
              type="text"
              name="title"
              value={form.title}
              onChange={handleChange}
              placeholder="Video title"
            />
          </Form.Group>
          <Form.Group className="mb-3">
            <Form.Label>Description</Form.Label>
            <Form.Control
              as="textarea"
              rows={3}
              name="description"
              value={form.description}
              onChange={handleChange}
              placeholder="Optional description"
            />
          </Form.Group>
          <Form.Check
            type="checkbox"
            name="isPrivate"
            id="isPrivate"
            label="Private video"
            checked={form.isPrivate}
            onChange={handleChange}
          />
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={handleClose} disabled={loading}>Cancel</Button>
          <Button type="submit" variant="primary" disabled={loading}>
            {loading ? <><Spinner size="sm" animation="border" className="me-2" />Sharing...</> : 'Share'}
          </Button>
        </Modal.Footer>
      </Form>
    </Modal>
  )
}
