import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { buildStreamUrl, mapApiVideo } from '../videoModel.js'

describe('mapApiVideo hlsUrl', () => {
  it('keeps hlsUrl when the API sends one', () => {
    expect(mapApiVideo({ id: 1, hlsUrl: 'https://x/hls/1/index.m3u8' }).hlsUrl).toBe('https://x/hls/1/index.m3u8')
  })

  it('defaults hlsUrl to null when absent or null', () => {
    expect(mapApiVideo({ id: 1 }).hlsUrl).toBeNull()
    expect(mapApiVideo({ id: 1, hlsUrl: null }).hlsUrl).toBeNull()
  })
})

const SRC = 'https://canh-labs.com/api/v1/funny-app/video-stream/stream/abc123'

describe('buildStreamUrl', () => {
  beforeEach(() => {
    localStorage.setItem('jwt', 'header.payload.signature')
    localStorage.setItem('guestToken', 'guest-123')
  })
  afterEach(() => localStorage.clear())

  it('never adds the JWT or guest token to the URL', () => {
    const url = buildStreamUrl(SRC)
    expect(url).toBe(SRC)
    expect(url).not.toContain('token')
  })

  it('strips token params already present in the source URL', () => {
    const url = buildStreamUrl(`${SRC}?token=leaked&guestToken=g&quality=hd`)
    expect(url).toBe(`${SRC}?quality=hd`)
  })

  it('returns falsy and non-URL inputs unchanged', () => {
    expect(buildStreamUrl(null)).toBeNull()
    expect(buildStreamUrl('')).toBe('')
    expect(buildStreamUrl('/relative/path.mp4')).toBe('/relative/path.mp4')
  })
})
