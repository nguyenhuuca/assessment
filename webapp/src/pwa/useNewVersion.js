import { useCallback, useEffect, useState } from 'react'

const POLL_MS = 15 * 60 * 1000

// Detects a newer deploy by comparing /version.json with the build id baked into this bundle.
// Independent of the service worker. Production only. Any mismatch counts, so a rollback
// deploy also shows the banner.
export function useNewVersion() {
  const [latestBuild, setLatestBuild] = useState(null)

  const check = useCallback(async () => {
    try {
      const res = await fetch(`/version.json?t=${Date.now()}`, { cache: 'no-store' })
      if (!res.ok) return
      const { build } = await res.json()
      if (typeof build === 'string' && build !== __APP_BUILD__) setLatestBuild(build)
    } catch {
      // offline or malformed response: try again at the next trigger
    }
  }, [])

  useEffect(() => {
    if (!import.meta.env.PROD) return undefined
    const onVisible = () => {
      if (document.visibilityState === 'visible') check()
    }
    document.addEventListener('visibilitychange', onVisible)
    const timer = setInterval(check, POLL_MS)
    return () => {
      document.removeEventListener('visibilitychange', onVisible)
      clearInterval(timer)
    }
  }, [check])

  return { updateAvailable: latestBuild !== null, latestBuild }
}
