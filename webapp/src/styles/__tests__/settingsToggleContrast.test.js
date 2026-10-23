import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, it, expect } from 'vitest'

const css = readFileSync(resolve(__dirname, '../index.css'), 'utf8')

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

// Body of the block opened by exactly this selector line (not the `.scrim` overrides).
function block(selector) {
  const start = css.indexOf(`\n${selector} {`)
  expect(start, `missing ${selector} block`).toBeGreaterThan(-1)
  return css.slice(start, css.indexOf('}', start))
}

function token(body, name) {
  const m = new RegExp(`${name}:\\s*(#[0-9a-fA-F]{6})`).exec(body)
  expect(m, `missing ${name}`).not.toBeNull()
  return m[1]
}

const rule = block('.settings-toggle')
const knob = block('.settings-toggle-knob')

describe('settings toggle off state', () => {
  it('track uses --bg-high with a --text-muted ring, knob uses --text-muted', () => {
    expect(rule).toMatch(/background:\s*var\(--bg-high\)/)
    expect(rule).toMatch(/box-shadow:\s*inset 0 0 0 1px var\(--text-muted\)/)
    expect(knob).toMatch(/background:\s*var\(--text-muted\)/)
  })

  it.each([
    ['dark', ':root'],
    ['light', '[data-theme="light"]'],
  ])('ring and knob stand out from the track in %s theme', (_, selector) => {
    const body = block(selector)
    const muted = token(body, '--text-muted')
    const high = token(body, '--bg-high')
    expect(contrast(muted, high)).toBeGreaterThanOrEqual(3)
  })
})
