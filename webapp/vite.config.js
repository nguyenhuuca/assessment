import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  define: {
    // Short commit id shown in report context; 'dev' outside CI
    __APP_BUILD__: JSON.stringify((process.env.GITHUB_SHA || 'dev').slice(0, 7)),
  },
  server: {
    port: 3000,
  },
  build: {
    outDir: 'dist',
  },
})
