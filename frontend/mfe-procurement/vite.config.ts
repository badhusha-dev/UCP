import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import federation from '@originjs/vite-plugin-federation';

export default defineConfig({
  plugins: [react(), federation({ name: 'procurement_mfe', filename: 'remoteEntry.js', exposes: { './App': './src/App.tsx' }, shared: ['react', 'react-dom'] })],
  build: { modulePreload: false, target: 'esnext', minify: false, cssCodeSplit: false, outDir: 'dist' },
  server: { port: 3012, cors: true },
  preview: { port: 3012, cors: true },
});
