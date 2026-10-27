import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The dev server proxies API calls to Spring Boot so the browser sees one origin.
// In production Nginx performs the same forwarding (deploy/nginx/default.conf).
export default defineConfig({
  plugins: [react()],
  server: {
    // Bind explicitly: Node resolves "localhost" to ::1 on this machine, which makes the
    // dev server unreachable over 127.0.0.1 and lets another project squat the IPv4 port.
    host: '127.0.0.1',
    port: 5175,
    // Fail loudly instead of silently moving to the next port, which makes a stray
    // dev server from another project impossible to tell apart from this one.
    strictPort: true,
    proxy: {
      '/api': { target: 'http://localhost:8081', changeOrigin: true },
      '/actuator': { target: 'http://localhost:8081', changeOrigin: true },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
})
