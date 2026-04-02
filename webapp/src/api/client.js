const getBaseUrl = () => import.meta.env.VITE_API_BASE_URL || 'https://canh-labs.com/api/v1/funny-app'

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
  const data = await res.json()
  if (!res.ok) {
    const msg = data?.error?.message || `HTTP ${res.status}`
    throw { message: msg, status: res.status }
  }
  return data
}

export const api = {
  get: (path) => request('GET', path),
  post: (path, body) => request('POST', path, body),
  delete: (path) => request('DELETE', path),
}
