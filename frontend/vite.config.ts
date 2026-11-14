import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The build goes straight into the jar's static folder; the dev server sends /api to the Spring app.
export default defineConfig({
  plugins: [react()],
  build: { outDir: '../target/classes/static', emptyOutDir: true },
  // changeOrigin: the app answers /api only for Host 127.0.0.1:8090 or localhost:8090
  server: { proxy: { '/api': { target: 'http://127.0.0.1:8090', changeOrigin: true } } },
})
