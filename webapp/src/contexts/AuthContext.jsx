import React, { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { authApi } from '../api/auth.js'
import { AUTH_REVOKED_EVENT } from '../api/client.js'

const AuthContext = createContext(null)

function readStorage() {
  try {
    return {
      jwt: localStorage.getItem('jwt') || null,
      user: JSON.parse(localStorage.getItem('user') || 'null'),
      guestToken: localStorage.getItem('guestToken') || null,
    }
  } catch {
    return { jwt: null, user: null, guestToken: null }
  }
}

export function AuthProvider({ children }) {
  const initial = readStorage()
  const [jwt, setJwt] = useState(initial.jwt)
  const [user, setUser] = useState(initial.user)
  const [guestToken, setGuestTokenState] = useState(initial.guestToken)
  const [loading, setLoading] = useState(!!initial.jwt)
  const [sessionNotice, setSessionNotice] = useState(null)

  // Server revoked this token (e.g. password changed on another device)
  useEffect(() => {
    function onRevoked() {
      localStorage.removeItem('jwt')
      localStorage.removeItem('user')
      setJwt(null)
      setUser(null)
      setSessionNotice('Phiên đăng nhập đã hết hiệu lực, vui lòng đăng nhập lại')
    }
    window.addEventListener(AUTH_REVOKED_EVENT, onRevoked)
    return () => window.removeEventListener(AUTH_REVOKED_EVENT, onRevoked)
  }, [])

  // Validate session on mount if JWT present
  useEffect(() => {
    if (!initial.jwt) return
    authApi.me()
      .then((data) => {
        const userData = data?.data ?? data
        if (userData?.role !== undefined) {
          setUser(prev => prev ? { ...prev, isAdmin: userData.role === 'ADMIN' } : prev)
        }
        setLoading(false)
      })
      .catch((err) => {
        if (err?.status === 401 || err?.status === 403) {
          localStorage.removeItem('jwt')
          localStorage.removeItem('user')
          setJwt(null)
          setUser(null)
        }
        setLoading(false)
      })
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const login = useCallback((newJwt, newUser) => {
    const userWithAdmin = {
      ...newUser,
      isAdmin: newUser?.isAdmin ?? (newUser?.role === 'ADMIN'),
    }
    localStorage.setItem('jwt', newJwt)
    localStorage.setItem('user', JSON.stringify(userWithAdmin))
    setJwt(newJwt)
    setUser(userWithAdmin)
    // Older login payloads carry no role: resolve it now so the admin menu shows without a reload
    if (newUser?.role === undefined && newUser?.isAdmin === undefined) {
      authApi.me()
        .then((data) => {
          const role = (data?.data ?? data)?.role
          if (role === undefined) return
          setUser(prev => {
            if (!prev) return prev
            const next = { ...prev, role, isAdmin: role === 'ADMIN' }
            localStorage.setItem('user', JSON.stringify(next))
            return next
          })
        })
        .catch(() => {})
    }
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem('jwt')
    localStorage.removeItem('user')
    setJwt(null)
    setUser(null)
  }, [])

  const setGuestToken = useCallback((token) => {
    if (token) {
      localStorage.setItem('guestToken', token)
    } else {
      localStorage.removeItem('guestToken')
    }
    setGuestTokenState(token)
  }, [])

  const updateUser = useCallback((updatedUser) => {
    localStorage.setItem('user', JSON.stringify(updatedUser))
    setUser(updatedUser)
  }, [])

  const clearSessionNotice = useCallback(() => setSessionNotice(null), [])

  return (
    <AuthContext.Provider value={{
      user,
      jwt,
      guestToken,
      isLoggedIn: !!jwt,
      loading,
      login,
      logout,
      setGuestToken,
      updateUser,
      sessionNotice,
      clearSessionNotice,
    }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
