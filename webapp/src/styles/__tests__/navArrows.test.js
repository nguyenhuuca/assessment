import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, it, expect } from 'vitest'

const css = readFileSync(resolve(__dirname, '../index.css'), 'utf8')

describe('prev/next arrows on touch screens', () => {
  it('hides .nav-arrows inside a (pointer: coarse) media query', () => {
    expect(css).toMatch(/@media\s*\(\s*pointer\s*:\s*coarse\s*\)\s*\{\s*\.nav-arrows\s*\{\s*display\s*:\s*none\s*;?\s*\}\s*\}/)
  })
})
