import { defineConfig } from 'vite';
import tailwindcss from '@tailwindcss/vite';

const target = process.env.API_PROXY_TARGET || 'http://localhost:8080';

export default defineConfig({
  plugins: [tailwindcss()],
  server: {
    port: 5173,
    strictPort: true,
    watch: { usePolling: true },
    proxy: {
      '/api': { target, changeOrigin: true },
      '/actuator/health': { target, changeOrigin: true },
    },
  },
});
