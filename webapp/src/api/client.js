export const getBaseUrl = () => import.meta.env.VITE_API_BASE_URL || 'https://canh-labs.com/api/v1/funny-app'

export const AUTH_REVOKED_EVENT = 'auth:revoked'

function createHeaders(extra = {}) {
  const headers = { 'Content-Type': 'application/json', ...extra }
  const jwt = localStorage.getItem('jwt')
  const guestToken = localStorage.getItem('guestToken')
  if (jwt) headers['Authorization'] = jwt
  if (guestToken) headers['X-Guest-Token'] = guestToken
  return headers
}

async function request(method, path, body) {
  const res = await fetch(`${getBaseUrl()}${path}`, {
    method,
    headers: createHeaders(),
    body: body ? JSON.stringify(body) : undefined,
  })
  // 204 No Content (e.g. PATCH .../read) has an empty body
  if (res.status === 204) return null
  const data = await res.json()
  if (!res.ok) {
    const msg = data?.error?.message || `HTTP ${res.status}`
    if (res.status === 401 && msg === 'TOKEN_REVOKED') {
      localStorage.removeItem('jwt')
      localStorage.removeItem('user')
      window.dispatchEvent(new Event(AUTH_REVOKED_EVENT))
    }
    throw { message: msg, status: res.status }
  }
  return data
}

export const api = {
  get: (path) => request('GET', path),
  post: (path, body) => request('POST', path, body),
  put: (path, body) => request('PUT', path, body),
  patch: (path, body) => request('PATCH', path, body),
  delete: (path, body) => request('DELETE', path, body),
}
