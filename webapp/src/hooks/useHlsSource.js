import { useEffect, useState } from 'react'

const HLS_MIME = 'application/vnd.apple.mpegurl'

/** True when the browser plays HLS itself (Safari, iOS). */
function supportsNativeHls() {
  if (typeof document === 'undefined') return false
  return !!document.createElement('video').canPlayType(HLS_MIME)
}

/**
 * Picks what the single <video> plays (ADR-0031).
 *  - no hlsUrl: the Range URL, exactly as before
 *  - hlsUrl + native HLS: the HLS URL as plain src
 *  - hlsUrl + hls.js (loaded on demand): hls.js attaches MSE, so no src is rendered
 *  - any HLS failure (fatal hls.js error, media error, import failure, unsupported): destroy hls.js,
 *    switch to the Range URL and resume at the last position with the same play state (EC-12)
 * Returns the `src` to render on the <video> (undefined while hls.js owns the element).
 */
export function useHlsSource(videoRef, { hlsUrl, rangeUrl }) {
  const [failedUrl, setFailedUrl] = useState(null)
  const [native] = useState(supportsNativeHls)
  const useHls = !!hlsUrl && failedUrl !== hlsUrl

  useEffect(() => {
    if (!useHls) return
    const v = videoRef.current
    if (!v) return
    let cancelled = false
    let hls = null

    function fallback() {
      if (cancelled) return
      cancelled = true
      const resumeAt = v.currentTime
      const wasPlaying = !v.paused
      hls?.destroy()
      hls = null
      v.removeEventListener('error', fallback)
      if (resumeAt > 0 || wasPlaying) {
        v.addEventListener('loadedmetadata', () => {
          if (resumeAt > 0) v.currentTime = resumeAt
          if (wasPlaying) v.play()?.catch?.(() => {})
        }, { once: true })
      }
      setFailedUrl(hlsUrl)
    }

    v.addEventListener('error', fallback)

    if (!native) {
      import('hls.js').then(({ default: Hls }) => {
        if (cancelled) return
        if (!Hls.isSupported()) { fallback(); return }
        hls = new Hls()
        hls.on(Hls.Events.ERROR, (_evt, data) => { if (data?.fatal) fallback() })
        hls.loadSource(hlsUrl)
        hls.attachMedia(v)
      }).catch(fallback)
    }

    return () => {
      cancelled = true
      v.removeEventListener('error', fallback)
      hls?.destroy()
      hls = null
    }
  }, [useHls, native, hlsUrl, videoRef])

  if (!useHls) return rangeUrl
  return native ? hlsUrl : undefined
}
