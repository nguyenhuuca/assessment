import '@testing-library/jest-dom'
import i18n from './i18n/index.js'

// Existing tests assert Vietnamese text; jsdom's navigator is en-US.
// changeLanguage is synchronous here because all resources are bundled.
beforeEach(() => {
  i18n.changeLanguage('vi')
  localStorage.removeItem('lang') // changeLanguage caches into localStorage; keep tests isolated
})
i18n.changeLanguage('vi')

// jsdom doesn't implement matchMedia
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation(query => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
})
