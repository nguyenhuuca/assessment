import React, { useCallback, useState } from 'react'
import ConfirmModal from '../components/common/ConfirmModal.jsx'

/**
 * Promise-based confirm: `if (await confirm({ title, message, danger })) doIt()`.
 * Render the returned `dialog` once in the component.
 */
export function useConfirm() {
  const [request, setRequest] = useState(null)

  const confirm = useCallback(options => new Promise(resolve => {
    setRequest({ ...options, resolve })
  }), [])

  const close = useCallback(result => {
    setRequest(current => {
      current?.resolve(result)
      return null
    })
  }, [])

  const onCancel = useCallback(() => close(false), [close])
  const onConfirm = useCallback(() => close(true), [close])

  const { resolve, ...props } = request ?? {}
  const dialog = request ? <ConfirmModal {...props} onCancel={onCancel} onConfirm={onConfirm} /> : null

  return [confirm, dialog]
}
