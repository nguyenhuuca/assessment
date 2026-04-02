import React, { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { authApi } from '../api/auth.js'

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

  // Validate session on mount if JWT present
  useEffect(() => {
    if (!initial.jwt) return
    authApi.me()
      .then(() => setLoading(false))
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
    localStorage.setItem('jwt', newJwt)
    localStorage.setItem('user', JSON.stringify(newUser))
    setJwt(newJwt)
    setUser(newUser)
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
