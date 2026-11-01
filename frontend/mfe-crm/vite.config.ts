import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import federation from '@originjs/vite-plugin-federation';

export default defineConfig({
  plugins: [react(), federation({ name: 'crm_mfe', filename: 'remoteEntry.js', exposes: { './App': './src/App' }, shared: ['react', 'react-dom', 'react-router-dom'] })],
  build: { modulePreload: false, target: 'esnext', minify: false, cssCodeSplit: false, outDir: 'dist' },
  server: { port: 3011, cors: true },
  preview: { port: 3011, cors: true },
});
