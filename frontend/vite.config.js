import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发模式默认走 Vite 代理转发 /api 到后端（同源，无跨域问题）；
// 如需浏览器直连后端，设置环境变量 VITE_API_BASE=http://127.0.0.1:19091（后端已放通 CORS）
export default defineConfig({
  plugins: [vue()],
  server: {
    host: true,
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:19091',
        changeOrigin: true
      }
    }
  }
})
