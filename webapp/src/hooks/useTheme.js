import { useContext } from 'react'
import { ThemeContext } from '../contexts/themeContext.js'

export function useTheme() {
  const ctx = useContext(ThemeContext)
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider')
  return ctx
}
