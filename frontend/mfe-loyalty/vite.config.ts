import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import federation from '@originjs/vite-plugin-federation';

export default defineConfig({
  plugins: [
    react(),
    federation({
      name: 'loyalty_mfe',
      filename: 'remoteEntry.js',
      exposes: {
        './App': './src/App',
        './LoyaltyDashboardPage': './src/pages/LoyaltyDashboardPage',
        './LoyaltyAdminPage': './src/pages/LoyaltyAdminPage',
      },
      shared: ['react', 'react-dom', 'react-router-dom'],
    }),
  ],
  build: {
    modulePreload: false,
    target: 'esnext',
    minify: false,
    cssCodeSplit: false,
    outDir: 'dist',
  },
  server: {
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
    port: 3006,
    cors: true,
  },
});
