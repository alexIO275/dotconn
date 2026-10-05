import { defineConfig } from 'vite';
import { serviceOrigin } from './electron/policy.cts';
const backend = serviceOrigin(process.env.MICROCREW_BACKEND_URL || 'http://127.0.0.1:8080');
export default defineConfig({
  server: {
    proxy: {
      '/api': { target: backend, changeOrigin: true,
        configure: proxy => { proxy.on('proxyReq', request => request.removeHeader('origin')); } },
      '/ws': { target: backend.replace(/^http/, 'ws'), ws: true, changeOrigin: true,
        configure: proxy => { proxy.on('proxyReqWs', request => request.removeHeader('origin')); } },
    },
  },
});
