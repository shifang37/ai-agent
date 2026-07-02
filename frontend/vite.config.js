import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    open: true
    // 说明：后端已开启全局 CORS（allowedOriginPatterns("*")），
    // 因此前端可直接跨域请求 http://localhost:8123/api，无需代理。
    // 如果你的环境有跨域限制，可改用下面的代理，并把 .env 中的
    // VITE_API_BASE_URL 改为 /api ：
    //
    // proxy: {
    //   '/api': {
    //     target: 'http://localhost:8123',
    //     changeOrigin: true
    //   }
    // }
  }
})
