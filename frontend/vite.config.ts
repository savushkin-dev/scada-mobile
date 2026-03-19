import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  const useNgrokHmr = env.VITE_NGROK_HMR === 'true';
  const buildId = env.VITE_BUILD_ID ?? new Date().toISOString();

  return {
    plugins: [react()],
    define: {
      __BUILD_ID__: JSON.stringify(buildId),
    },
    server: {
      host: true,
      port: 5500,
      allowedHosts: ['.ngrok-free.dev', '.ngrok-free.app'],
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
        '/ws': {
          target: 'ws://localhost:8080',
          ws: true,
          changeOrigin: true,
        },
      },
      hmr: useNgrokHmr
        ? {
            protocol: 'wss',
            clientPort: 443,
          }
        : undefined,
    },
  };
});
