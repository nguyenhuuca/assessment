import { createContext } from 'react'

// Context object only: the provider lives in AuthContext.jsx and the hook in hooks/useAuth.js,
// so the component file exports components only (React Fast Refresh).
export const AuthContext = createContext(null)
