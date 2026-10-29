import { createContext } from 'react'

// Context object only: the provider lives in ThemeContext.jsx and the hook in hooks/useTheme.js,
// so the component file exports components only (React Fast Refresh).
export const ThemeContext = createContext(null)
