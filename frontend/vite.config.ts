import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The build goes straight into the jar's static folder; the dev server sends /api to the Spring app.
export default defineConfig({
  plugins: [react()],
  build: { outDir: '../target/classes/static', emptyOutDir: true },
  server: { proxy: { '/api': 'http://127.0.0.1:8090' } },
})
