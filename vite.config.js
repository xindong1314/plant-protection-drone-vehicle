import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  // 开发服务器配置
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/dev-api': {
        target: 'http://172.28.176.1:8080',  // 后端 PPDVapi 地址（Windows 宿主机）
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/dev-api/, '')  // 剥掉 /dev-api 前缀
      }
    }
  }
})
