// Classic service worker: makes the app installable and keeps the shell and hashed
// assets available offline. It never reloads pages; new builds are announced by the app
// itself (see src/pwa/useNewVersion.js).
const CACHE = 'fm-shell-v1'
const SHELL_KEY = '/'
const MAX_ENTRIES = 60

self.addEventListener('install', () => {
  self.skipWaiting()
})

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(key => key !== CACHE).map(key => caches.delete(key))))
      .then(() => self.clients.claim()),
  )
})

async function trimAssets(cache) {
  const keys = await cache.keys()
  if (keys.length <= MAX_ENTRIES) return
  const removable = keys.filter(req => new URL(req.url).pathname !== SHELL_KEY)
  const excess = keys.length - MAX_ENTRIES
  await Promise.all(removable.slice(0, excess).map(req => cache.delete(req)))
}

async function handleNavigation(request) {
  try {
    const response = await fetch(request, { cache: 'no-cache' })
    if (response.ok && !response.redirected) {
      const cache = await caches.open(CACHE)
      await cache.put(SHELL_KEY, response.clone())
    }
    return response
  } catch (err) {
    const cached = await caches.match(SHELL_KEY)
    if (cached) return cached
    throw err
  }
}

async function handleAsset(request) {
  const cached = await caches.match(request)
  if (cached) return cached
  const response = await fetch(request)
  const type = response.headers.get('content-type') || ''
  if (response.ok && !type.includes('text/html')) {
    const cache = await caches.open(CACHE)
    await cache.put(request, response.clone())
    await trimAssets(cache)
  }
  return response
}

self.addEventListener('fetch', event => {
  const { request } = event
  if (request.method !== 'GET' || request.headers.has('range')) return
  const url = new URL(request.url)
  if (url.origin !== self.location.origin) return
  if (request.mode === 'navigate') {
    event.respondWith(handleNavigation(request))
  } else if (url.pathname.startsWith('/assets/')) {
    event.respondWith(handleAsset(request))
  }
})
