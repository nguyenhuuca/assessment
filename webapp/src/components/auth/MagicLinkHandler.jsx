import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { authApi } from '../../api/auth.js'
import { useAuth } from '../../hooks/useAuth.js'

// This component checks for ?token= on mount and handles magic link verification.
// Renders nothing — side-effects only.
export default function MagicLinkHandler({ onMfaRequired, onError }) {
  const { login } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const token = params.get('token')
    if (!token) return

    // Clean the token from URL immediately
    window.history.replaceState({}, document.title, window.location.pathname)

    authApi.verifyMagic(token)
      .then((res) => {
        const data = res.data
        if (data.action === 'MFA_REQUIRED') {
          localStorage.setItem('user', JSON.stringify(data.user))
          onMfaRequired?.(data)
        } else {
          login(data.jwt, data.user)
          navigate('/', { replace: true })
        }
      })
      .catch((err) => {
        onError?.(err.message || 'Magic link verification failed')
      })
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  return null
}
