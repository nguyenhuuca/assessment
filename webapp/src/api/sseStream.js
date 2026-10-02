/**
 * Minimal SSE client over fetch (EventSource cannot send an Authorization
 * header, and tokens must never be put in URLs).
 *
 * Resolves when the stream ends; throws { status } on a non-2xx response.
 * Aborting via `signal` rejects with the fetch AbortError.
 */

/** Parse one raw SSE block (without the trailing blank line). Returns null if it has no data. */
function parseBlock(block) {
  let event = 'message'
  const data = []
  for (const line of block.split('\n')) {
    if (!line || line.startsWith(':')) continue
    const idx = line.indexOf(':')
    const field = idx === -1 ? line : line.slice(0, idx)
    let value = idx === -1 ? '' : line.slice(idx + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') event = value
    else if (field === 'data') data.push(value)
  }
  if (data.length === 0) return null
  return { event, data: data.join('\n') }
}

/** Split a buffer on blank lines; returns the complete blocks and the unfinished remainder. */
function drain(buffer) {
  const normalized = buffer.replace(/\r\n/g, '\n').replace(/\r(?!$)/g, '\n')
  const parts = normalized.split('\n\n')
  const rest = parts.pop()
  return { blocks: parts, rest }
}

function textStream(body) {
  if (typeof TextDecoderStream !== 'undefined' && typeof body.pipeThrough === 'function') {
    return body.pipeThrough(new TextDecoderStream()).getReader()
  }
  const reader = body.getReader()
  const decoder = new TextDecoder()
  return {
    async read() {
      const { done, value } = await reader.read()
      return done ? { done, value: decoder.decode() } : { done, value: decoder.decode(value, { stream: true }) }
    },
    cancel: (reason) => reader.cancel(reason),
  }
}

export async function openStream({ url, headers = {}, signal, onEvent }) {
  const res = await fetch(url, {
    method: 'GET',
    headers: { Accept: 'text/event-stream', ...headers },
    signal,
  })
  if (!res.ok) throw { message: `HTTP ${res.status}`, status: res.status }
  if (!res.body) return

  const reader = textStream(res.body)
  let buffer = ''
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (value) buffer += value
      const { blocks, rest } = drain(buffer)
      buffer = rest
      for (const block of blocks) {
        const evt = parseBlock(block)
        if (evt) onEvent?.(evt)
      }
      if (done) break
    }
  } finally {
    try { await reader.cancel() } catch { /* already closed */ }
  }
}
