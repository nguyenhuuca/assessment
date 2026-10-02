import { describe, it, expect, vi, beforeEach } from 'vitest'
import { openStream } from '../sseStream.js'

const mockFetch = vi.fn()
global.fetch = mockFetch

const enc = new TextEncoder()

function streamOf(chunks) {
  return new ReadableStream({
    start(controller) {
      for (const c of chunks) controller.enqueue(typeof c === 'string' ? enc.encode(c) : c)
      controller.close()
    },
  })
}

async function collect(chunks) {
  mockFetch.mockResolvedValueOnce({ ok: true, status: 200, body: streamOf(chunks) })
  const events = []
  await openStream({ url: 'http://x/stream', onEvent: e => events.push(e) })
  return events
}

beforeEach(() => mockFetch.mockReset())

describe('openStream', () => {
  it('parses a single event', async () => {
    expect(await collect(['event: unread\ndata: {"unread": 3}\n\n']))
      .toEqual([{ event: 'unread', data: '{"unread": 3}' }])
  })

  it('reassembles an event split across chunks (even mid-line)', async () => {
    const events = await collect(['event: unr', 'ead\ndata: {"unre', 'ad": 7}\n', '\n'])
    expect(events).toEqual([{ event: 'unread', data: '{"unread": 7}' }])
  })

  it('emits multiple events from one chunk', async () => {
    const events = await collect([
      'event: unread\ndata: {"unread": 1}\n\nevent: unread\ndata: {"unread": 2}\n\n',
    ])
    expect(events.map(e => e.data)).toEqual(['{"unread": 1}', '{"unread": 2}'])
  })

  it('handles CRLF line endings, including a CRLF split across chunks', async () => {
    const events = await collect(['event: unread\r\ndata: {"unread": 4}\r', '\n\r\n'])
    expect(events).toEqual([{ event: 'unread', data: '{"unread": 4}' }])
  })

  it('joins multi-line data with newlines', async () => {
    const events = await collect(['data: line1\ndata: line2\n\n'])
    expect(events).toEqual([{ event: 'message', data: 'line1\nline2' }])
  })

  it('ignores comment/heartbeat lines and comment-only blocks', async () => {
    const events = await collect([': ping\n\n', ': hi\nevent: unread\ndata: {"unread": 5}\n\n', ': ping\n\n'])
    expect(events).toEqual([{ event: 'unread', data: '{"unread": 5}' }])
  })

  it('does not emit an unterminated trailing event', async () => {
    expect(await collect(['event: unread\ndata: {"unread": 5}\n'])).toEqual([])
  })

  it('decodes multi-byte characters split across chunks', async () => {
    const bytes = enc.encode('data: Xin chào ✓\n\n')
    const cut = bytes.length - 5
    const events = await collect([bytes.slice(0, cut), bytes.slice(cut)])
    expect(events).toEqual([{ event: 'message', data: 'Xin chào ✓' }])
  })

  it('throws { status } on non-2xx', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 401, body: null })
    await expect(openStream({ url: 'http://x/stream' })).rejects.toMatchObject({ status: 401 })
  })

  it('sends headers and signal to fetch', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, status: 200, body: streamOf([]) })
    const ac = new AbortController()
    await openStream({ url: 'http://x/stream', headers: { Authorization: 'jwt1' }, signal: ac.signal })
    const [url, opts] = mockFetch.mock.calls[0]
    expect(url).toBe('http://x/stream')
    expect(opts.headers.Authorization).toBe('jwt1')
    expect(opts.headers.Accept).toBe('text/event-stream')
    expect(opts.signal).toBe(ac.signal)
  })
})

describe('openStream without TextDecoderStream', () => {
  it('falls back to TextDecoder', async () => {
    vi.stubGlobal('TextDecoderStream', undefined)
    try {
      const events = await collect(['event: unread\ndata: {"unread": 2}\n', '\n'])
      expect(events).toEqual([{ event: 'unread', data: '{"unread": 2}' }])
    } finally {
      vi.unstubAllGlobals()
    }
  })
})
