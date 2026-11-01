import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import federation from '@originjs/vite-plugin-federation'

export default defineConfig({
  plugins: [
    react(),
    federation({
      name: 'cart_mfe',
      filename: 'remoteEntry.js',
      exposes: {
        './App': './src/App',
        './CartPage': './src/pages/CartPage',
        './CartDrawer': './src/components/CartDrawer',
        './AbandonedCartsAdminPage': './src/pages/AbandonedCartsAdminPage'
      },
      shared: {
        'react': { singleton: true, requiredVersion: false },
        'react-dom': { singleton: true, requiredVersion: false },
        'react-redux': { singleton: true, requiredVersion: false },
        'react-router-dom': { singleton: true, requiredVersion: false },
        '@ecommerce/shared-library': { singleton: true, requiredVersion: false },
      }
    })
  ],
  server: {
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
    port: 3005,
    cors: true,
  },
  preview: {
    port: 3005,
    cors: true,
  },
  build: {
    modulePreload: false,
    target: 'esnext',
    minify: false,
    cssCodeSplit: false
  }
})
