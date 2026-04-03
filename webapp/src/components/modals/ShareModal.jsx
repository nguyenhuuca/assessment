import React, { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { videosApi } from '../../api/videos.js'

const EMPTY_FORM = { url: '', title: '', description: '', isPrivate: false }

const Spinner = () => (
  <span style={{
    width: 14, height: 14, flexShrink: 0,
    border: '2px solid rgba(0,48,53,0.3)',
    borderTopColor: '#003035',
    borderRadius: '50%',
    display: 'inline-block',
    animation: 'spin 0.7s linear infinite',
  }} />
)

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

  if (!show) return null

  return (
    <div className="app-modal-backdrop" onClick={e => { if (e.target === e.currentTarget) handleClose() }}>
      <div className="app-modal">
        <div className="app-modal-header">
          <span className="app-modal-title">Share a Video</span>
          <button className="app-modal-close" onClick={handleClose}>
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>close</span>
          </button>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'contents' }}>
          <div className="app-modal-body">
            {error && <div className="app-alert error">{error}</div>}

            <div className="app-field">
              <label className="app-label">YouTube URL *</label>
              <input
                className="app-input"
                type="url"
                name="url"
                value={form.url}
                onChange={handleChange}
                placeholder="https://www.youtube.com/watch?v=..."
                required
              />
            </div>

            <div className="app-field">
              <label className="app-label">Title</label>
              <input
                className="app-input"
                type="text"
                name="title"
                value={form.title}
                onChange={handleChange}
                placeholder="Video title"
              />
            </div>

            <div className="app-field">
              <label className="app-label">Description</label>
              <textarea
                className="app-input"
                rows={3}
                name="description"
                value={form.description}
                onChange={handleChange}
                placeholder="Optional description"
              />
            </div>

            <label className="app-checkbox-wrap">
              <input
                type="checkbox"
                name="isPrivate"
                checked={form.isPrivate}
                onChange={handleChange}
              />
              <span>Private video</span>
            </label>
          </div>

          <div className="app-modal-footer">
            <button type="button" className="app-btn secondary" onClick={handleClose} disabled={loading}>
              Cancel
            </button>
            <button type="submit" className="app-btn primary" disabled={loading}>
              {loading ? <><Spinner />Sharing…</> : 'Share'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
