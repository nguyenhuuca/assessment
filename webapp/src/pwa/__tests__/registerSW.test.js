import { registerSW } from '../registerSW.js'

describe('registerSW', () => {
  let register
  beforeEach(() => {
    register = vi.fn().mockResolvedValue({})
    Object.defineProperty(navigator, 'serviceWorker', { value: { register }, configurable: true })
  })
  afterEach(() => {
    vi.unstubAllEnvs()
    delete navigator.serviceWorker
  })

  it('registers /sw.js with root scope in PROD', () => {
    vi.stubEnv('PROD', true)
    registerSW()
    expect(register).toHaveBeenCalledWith('/sw.js', { scope: '/', updateViaCache: 'none' })
  })

  it('does nothing outside PROD', () => {
    vi.stubEnv('PROD', false)
    registerSW()
    expect(register).not.toHaveBeenCalled()
  })

  it('does nothing without service worker support', () => {
    vi.stubEnv('PROD', true)
    delete navigator.serviceWorker
    expect(() => registerSW()).not.toThrow()
    expect(register).not.toHaveBeenCalled()
  })
})
