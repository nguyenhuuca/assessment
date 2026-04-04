import React, { useState } from 'react'
import { Alert, Button, Modal, Spinner } from 'react-bootstrap'
import { useQueryClient } from '@tanstack/react-query'
import { videosApi } from '../../api/videos.js'

export default function DeleteConfirmModal({ show, video, onHide, onDeleted }) {
  const queryClient = useQueryClient()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function handleConfirm() {
    if (!video?.id) return
    setLoading(true)
    setError('')
    try {
      await videosApi.delete(video.id)
      // Remove from all video query caches
      queryClient.setQueriesData({ queryKey: ['videos'] }, (old) => {
        if (!Array.isArray(old)) return old
        return old.filter(v => v.id !== video.id)
      })
      onDeleted?.(video)
      handleClose()
    } catch (err) {
      setError(err.message || 'Failed to delete video')
    } finally {
      setLoading(false)
    }
  }

  function handleClose() {
    setError('')
    onHide()
  }

  return (
    <Modal show={show} onHide={handleClose} centered size="sm">
      <Modal.Header closeButton>
        <Modal.Title>Delete Video</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {error && <Alert variant="danger">{error}</Alert>}
        <p>Are you sure you want to delete <strong>{video?.title || 'this video'}</strong>?</p>
        <p className="text-muted" style={{ fontSize: '0.85rem' }}>This action cannot be undone.</p>
      </Modal.Body>
      <Modal.Footer>
        <Button variant="secondary" onClick={handleClose} disabled={loading}>Cancel</Button>
        <Button variant="danger" onClick={handleConfirm} disabled={loading}>
          {loading ? <Spinner size="sm" animation="border" /> : 'Delete'}
        </Button>
      </Modal.Footer>
    </Modal>
  )
}
