import { describe, it, expect } from 'vitest'

const modules = import.meta.glob('../locales/*/*.json', { eager: true })

const byNs = {}
for (const [path, mod] of Object.entries(modules)) {
  const [, lng, ns] = path.match(/locales\/([^/]+)\/([^/]+)\.json$/)
  ;(byNs[ns] ||= {})[lng] = mod.default ?? mod
}

// Flatten to dotted keys + leaf values
function flatten(obj, prefix = '', out = {}) {
  for (const [k, v] of Object.entries(obj)) {
    const key = prefix ? `${prefix}.${k}` : k
    if (v && typeof v === 'object') flatten(v, key, out)
    else out[key] = v
  }
  return out
}

// Plural keys differ per language by CLDR suffix (en: _one/_other, vi: _other only).
const PLURAL = /_(zero|one|two|few|many|other)$/
const base = (k) => k.replace(PLURAL, '')

describe('i18n resource parity', () => {
  it('has the same namespaces in vi and en', () => {
    for (const [ns, langs] of Object.entries(byNs)) {
      expect(Object.keys(langs).sort(), `namespace ${ns}`).toEqual(['en', 'vi'])
    }
  })

  for (const [ns, { vi, en }] of Object.entries(byNs)) {
    describe(ns, () => {
      const fvi = flatten(vi ?? {})
      const fen = flatten(en ?? {})

      it('has identical keys (plural suffixes aside)', () => {
        const bvi = [...new Set(Object.keys(fvi).map(base))].sort()
        const ben = [...new Set(Object.keys(fen).map(base))].sort()
        expect(bvi).toEqual(ben)
        // every plural group must at least define _other
        for (const f of [fvi, fen]) {
          for (const k of Object.keys(f)) {
            if (PLURAL.test(k)) expect(f[base(k) + '_other'], k).toBeDefined()
          }
        }
        // non-plural keys must match exactly
        const np = (f) => Object.keys(f).filter((k) => !PLURAL.test(k)).sort()
        expect(np(fvi)).toEqual(np(fen))
      })

      it('has no empty values', () => {
        for (const f of [fvi, fen]) {
          for (const [k, v] of Object.entries(f)) {
            expect(typeof v === 'string' && v.trim() !== '', k).toBe(true)
          }
        }
      })
    })
  }
})
