import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  // 相对路径：既适合部署到任意子目录，也适合将来用 Capacitor 以本地文件方式加载
  base: './',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '0.0.0.0',
    port: 5174,
    open: false,
    // Windows 上 fs.watch 偶发 EBUSY（编辑器原子写），改用轮询
    watch: {
      usePolling: true,
      interval: 300,
      ignored: ['**/node_modules/**', '**/dist/**', '**/.git/**']
    },
    proxy: {
      // 后端真实端口是 8088，路由前缀 /v1，不带 /api
      '/api': {
        // 默认打本机 8088；临时验证别的实例可用 VITE_API_TARGET 覆盖
        target: process.env.VITE_API_TARGET || 'http://localhost:8088',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false,
    chunkSizeWarningLimit: 1600
  }
})
