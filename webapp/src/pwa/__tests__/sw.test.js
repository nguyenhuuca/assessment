import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const SOURCE = readFileSync(resolve(__dirname, '../../../public/sw.js'), 'utf8')
const ORIGIN = 'https://funny.test'

function makeCaches(initial = {}) {
  const stores = new Map(Object.entries(initial).map(([name, entries]) => [name, new Map(entries)]))
  const keyOf = r => (typeof r === 'string' ? r : r.url)
  const open = async name => {
    if (!stores.has(name)) stores.set(name, new Map())
    const store = stores.get(name)
    return {
      put: async (r, res) => { store.set(keyOf(r), res) },
      delete: async r => store.delete(keyOf(r)),
      keys: async () => [...store.keys()].map(k => ({ url: k.startsWith('http') ? k : ORIGIN + k })),
    }
  }
  return {
    stores,
    open,
    keys: async () => [...stores.keys()],
    delete: async name => stores.delete(name),
    match: vi.fn(async r => {
      const k = keyOf(r)
      for (const store of stores.values()) if (store.has(k)) return store.get(k)
      return undefined
    }),
  }
}

function load(cachesImpl, fetchImpl) {
  const listeners = {}
  const self = {
    location: { origin: ORIGIN },
    skipWaiting: vi.fn(),
    clients: { claim: vi.fn().mockResolvedValue() },
    addEventListener: (type, fn) => { listeners[type] = fn },
  }
  new Function('self', 'caches', 'fetch', SOURCE)(self, cachesImpl, fetchImpl)
  return { self, listeners }
}

function req(path, { method = 'GET', mode = 'cors', headers = {}, origin = ORIGIN } = {}) {
  const names = new Set(Object.keys(headers).map(h => h.toLowerCase()))
  return { url: origin + path, method, mode, headers: { has: name => names.has(name.toLowerCase()) } }
}

function resp({ ok = true, redirected = false, type = 'application/javascript' } = {}) {
  return { ok, redirected, headers: new Headers({ 'content-type': type }), clone() { return this } }
}

function dispatch(listeners, request) {
  let promise
  listeners.fetch({ request, respondWith: p => { promise = p } })
  return promise
}

describe('service worker', () => {
  it('skips waiting on install', () => {
    const { self, listeners } = load(makeCaches(), vi.fn())
    listeners.install()
    expect(self.skipWaiting).toHaveBeenCalled()
  })

  it('deletes other caches on activate and claims clients', async () => {
    const caches = makeCaches({ 'fm-shell-v1': [], old: [], other: [] })
    const { self, listeners } = load(caches, vi.fn())
    let p
    listeners.activate({ waitUntil: x => { p = x } })
    await p
    expect([...caches.stores.keys()]).toEqual(['fm-shell-v1'])
    expect(self.clients.claim).toHaveBeenCalled()
  })

  describe('not intercepted', () => {
    const cases = {
      'same-origin api': req('/api/x'),
      'cross-origin api': req('/api/v1/x', { origin: 'https://canh-labs.com' }),
      'video stream': req('/video-stream/1'),
      'POST': req('/assets/a.js', { method: 'POST' }),
      'Range request': req('/assets/a.js', { headers: { Range: 'bytes=0-1' } }),
      'version.json': req('/version.json?t=1'),
    }
    for (const [name, request] of Object.entries(cases)) {
      it(name, () => {
        const fetchMock = vi.fn()
        const { listeners } = load(makeCaches(), fetchMock)
        expect(dispatch(listeners, request)).toBeUndefined()
        expect(fetchMock).not.toHaveBeenCalled()
      })
    }
  })

  describe('assets', () => {
    it('serves from cache first', async () => {
      const cached = resp()
      const caches = makeCaches({ 'fm-shell-v1': [[ORIGIN + '/assets/a.js', cached]] })
      const fetchMock = vi.fn()
      const { listeners } = load(caches, fetchMock)
      expect(await dispatch(listeners, req('/assets/a.js'))).toBe(cached)
      expect(fetchMock).not.toHaveBeenCalled()
    })

    it('fetches and stores a miss', async () => {
      const caches = makeCaches()
      const network = resp()
      const { listeners } = load(caches, vi.fn().mockResolvedValue(network))
      expect(await dispatch(listeners, req('/assets/a.js'))).toBe(network)
      expect(caches.stores.get('fm-shell-v1').get(ORIGIN + '/assets/a.js')).toBe(network)
    })

    it('does not store an HTML fallback response', async () => {
      const caches = makeCaches()
      const { listeners } = load(caches, vi.fn().mockResolvedValue(resp({ type: 'text/html; charset=utf-8' })))
      await dispatch(listeners, req('/assets/missing.js'))
      expect(caches.stores.size).toBe(0)
    })

    it('does not store non-ok responses', async () => {
      const caches = makeCaches()
      const { listeners } = load(caches, vi.fn().mockResolvedValue(resp({ ok: false })))
      await dispatch(listeners, req('/assets/a.js'))
      expect(caches.stores.size).toBe(0)
    })

    it('trims the oldest assets above 60 entries and keeps the shell', async () => {
      const entries = [['/', resp()]]
      for (let i = 0; i < 60; i++) entries.push([`${ORIGIN}/assets/old${i}.js`, resp()])
      const caches = makeCaches({ 'fm-shell-v1': entries })
      const { listeners } = load(caches, vi.fn().mockResolvedValue(resp()))
      await dispatch(listeners, req('/assets/new.js'))
      const store = caches.stores.get('fm-shell-v1')
      expect(store.size).toBe(60)
      expect(store.has('/')).toBe(true)
      expect(store.has(ORIGIN + '/assets/new.js')).toBe(true)
      expect(store.has(ORIGIN + '/assets/old0.js')).toBe(false)
      expect(store.has(ORIGIN + '/assets/old1.js')).toBe(false)
    })
  })

  describe('navigation', () => {
    it('fetches with no-cache and stores under the fixed shell key only', async () => {
      const caches = makeCaches()
      const network = resp({ type: 'text/html' })
      const fetchMock = vi.fn().mockResolvedValue(network)
      const { listeners } = load(caches, fetchMock)
      const request = req('/?token=abc', { mode: 'navigate' })
      expect(await dispatch(listeners, request)).toBe(network)
      expect(fetchMock).toHaveBeenCalledWith(request, { cache: 'no-cache' })
      expect([...caches.stores.get('fm-shell-v1').keys()]).toEqual(['/'])
    })

    it('falls back to the cached shell offline', async () => {
      const shell = resp({ type: 'text/html' })
      const caches = makeCaches({ 'fm-shell-v1': [['/', shell]] })
      const { listeners } = load(caches, vi.fn().mockRejectedValue(new Error('offline')))
      expect(await dispatch(listeners, req('/library', { mode: 'navigate' }))).toBe(shell)
      expect(caches.match).toHaveBeenCalledWith('/')
    })

    it('rejects offline when no shell is cached', async () => {
      const { listeners } = load(makeCaches(), vi.fn().mockRejectedValue(new Error('offline')))
      await expect(dispatch(listeners, req('/', { mode: 'navigate' }))).rejects.toThrow('offline')
    })

    it('does not store redirected, non-ok or non-HTML responses', async () => {
      for (const odd of [{ redirected: true, type: 'text/html' }, { ok: false, type: 'text/html' }, { type: 'application/json' }]) {
        const caches = makeCaches()
        const { listeners } = load(caches, vi.fn().mockResolvedValue(resp(odd)))
        await dispatch(listeners, req('/', { mode: 'navigate' }))
        expect(caches.stores.size).toBe(0)
      }
    })
  })
})
