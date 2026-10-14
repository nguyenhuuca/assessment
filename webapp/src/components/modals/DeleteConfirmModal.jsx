import React, { useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { videosApi } from '../../api/videos.js'
import ConfirmModal from '../common/ConfirmModal.jsx'

export default function DeleteConfirmModal({ show, video, onHide, onDeleted }) {
  const { t } = useTranslation(['shell', 'common'])
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
      setError(err.message || t('deleteFailed'))
    } finally {
      setLoading(false)
    }
  }

  function handleClose() {
    setError('')
    onHide()
  }

  if (!show) return null

  return (
    <ConfirmModal
      title={t('deleteTitle')}
      message={<Trans t={t} i18nKey="deleteMessage" values={{ title: video?.title || t('deleteThisVideo') }} components={{ b: <strong /> }} />}
      confirmLabel={t('common:delete')}
      danger
      pending={loading}
      onCancel={handleClose}
      onConfirm={handleConfirm}
    >
      {error && <div className="app-alert error" role="alert">{error}</div>}
    </ConfirmModal>
  )
}
