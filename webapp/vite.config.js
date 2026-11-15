import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  root: '.',
  base: './',
  resolve: {
    alias: { '@': path.resolve(__dirname, 'src') },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true },
      '/ws': { target: 'ws://127.0.0.1:8080', ws: true },
    },
  },
  build: {
    outDir: path.resolve(__dirname, 'dist'),
    emptyOutDir: true,
    sourcemap: false,
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-vue': ['vue'],
          'vendor-naive': ['naive-ui'],
          'vendor-editor': [
            'codemirror',
            '@codemirror/state',
            '@codemirror/view',
            '@codemirror/lang-json',
            '@codemirror/lang-xml',
            '@codemirror/theme-one-dark',
          ],
          'vendor-terminal': ['@xterm/xterm', '@xterm/addon-fit'],
          'vendor-icons': ['lucide-vue-next'],
        },
      },
    },
  },
})
