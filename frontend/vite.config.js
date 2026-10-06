import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  define: {
    global: 'window'
  },
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      },
      '/ws': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        ws: true
      },
      '/uploads': {
        target: 'http://localhost:8081',
        changeOrigin: true
      },
      '/stream': {
        target: 'http://localhost:8083',
        changeOrigin: true
      }
    }
  }
})
