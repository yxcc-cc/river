import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 后端部署在阿里云（47.111.116.134:8081），视频流由本机 vision_detector.py 提供（8083）。
// 浏览器只跟 localhost:5173 打交道，图片走 /stream 代理，
// 这样就不会触发「公网页面加载本机资源」的 Private Network Access 拦截。
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
        target: 'http://47.111.116.134:8081',
        changeOrigin: true
      },
      '/ws': {
        target: 'http://47.111.116.134:8081',
        changeOrigin: true,
        ws: true
      },
      '/uploads': {
        target: 'http://47.111.116.134:8081',
        changeOrigin: true
      },
      '/stream': {
        target: 'http://localhost:8083',
        changeOrigin: true
      }
    }
  }
})
