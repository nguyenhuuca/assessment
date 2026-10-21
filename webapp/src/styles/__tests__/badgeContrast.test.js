import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, it, expect } from 'vitest'

const css = readFileSync(resolve(__dirname, '../index.css'), 'utf8')

const SURFACES = ['#ffffff', '#f8f9fa']
const SELECTORS = ['pending', 'missing_local', 'missing_db', 'size_mismatch']

function luminance(hex) {
  const [r, g, b] = [1, 3, 5]
    .map((i) => parseInt(hex.slice(i, i + 2), 16) / 255)
    .map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4))
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

function contrast(a, b) {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (hi + 0.05) / (lo + 0.05)
}

// Finds the color declared by a rule whose selector list contains the light-theme override.
function lightBadgeColor(name) {
  const selector = `\\[data-theme="light"\\]\\s*\\.admin-badge\\.${name}`
  const rule = new RegExp(`(?:^|\\})\\s*([^{}]*${selector}[^{}]*)\\{([^}]*)\\}`, 'm').exec(css)
  if (!rule) return null
  const color = /(?:^|[;\s])color\s*:\s*(#[0-9a-fA-F]{6})/.exec(rule[2])
  return color ? color[1] : null
}

describe('light theme admin badge contrast', () => {
  it.each(SELECTORS)('.admin-badge.%s text is readable on light surfaces', (name) => {
    const color = lightBadgeColor(name)
    expect(color, `missing [data-theme="light"] override for .${name}`).not.toBeNull()
    for (const surface of SURFACES) {
      expect(contrast(color, surface)).toBeGreaterThanOrEqual(4.5)
    }
  })
})
