import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import federation from '@originjs/vite-plugin-federation'

export default defineConfig({
  plugins: [
    react(),
    federation({
      name: 'user_mfe',
      filename: 'remoteEntry.js',
      exposes: {
        './App': './src/App',
      },
      shared: ['react', 'react-dom', 'react-redux', 'react-router-dom']
    })
  ],
  server: {
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
    port: 3003,
    cors: true,
  },
  preview: {
    port: 3003,
    cors: true,
  },
  build: {
    modulePreload: false,
    target: 'esnext',
    minify: false,
    cssCodeSplit: false
  }
})
