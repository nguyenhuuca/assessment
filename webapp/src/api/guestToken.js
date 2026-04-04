const GUEST_TOKEN_KEY = 'guestToken'

export const guestTokenApi = {
  get: () => localStorage.getItem(GUEST_TOKEN_KEY),
  set: (token) => localStorage.setItem(GUEST_TOKEN_KEY, token),
  clear: () => localStorage.removeItem(GUEST_TOKEN_KEY),
}
