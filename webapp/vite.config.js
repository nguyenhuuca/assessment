import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Short commit id shown in report context; 'dev' outside CI
const APP_BUILD = (process.env.GITHUB_SHA || 'dev').slice(0, 7)

// Emits /version.json so open tabs can detect a newer deploy (see src/pwa/useNewVersion.js)
const versionFile = {
  name: 'version-file',
  generateBundle() {
    this.emitFile({ type: 'asset', fileName: 'version.json', source: JSON.stringify({ build: APP_BUILD }) })
  },
}

export default defineConfig({
  plugins: [react(), versionFile],
  define: {
    __APP_BUILD__: JSON.stringify(APP_BUILD),
  },
  server: {
    port: 3000,
  },
  build: {
    outDir: 'dist',
  },
})
