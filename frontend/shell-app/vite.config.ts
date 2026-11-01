import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import federation from '@originjs/vite-plugin-federation'

export default defineConfig({
  plugins: [
    react(),
    federation({
      name: 'shell_app',
      remotes: {
        product_mfe: 'http://localhost:3001/assets/remoteEntry.js',
        order_mfe: 'http://localhost:3002/assets/remoteEntry.js',
        user_mfe: 'http://localhost:3003/assets/remoteEntry.js',
        inventory_mfe: 'http://localhost:3004/assets/remoteEntry.js',
        stock_mfe: 'http://localhost:3009/assets/remoteEntry.js',
        cart_mfe: 'http://localhost:3005/assets/remoteEntry.js',
        loyalty_mfe: 'http://localhost:3006/assets/remoteEntry.js',
        accounting_mfe: 'http://localhost:3007/assets/remoteEntry.js',
        analytics_mfe: 'http://localhost:3008/assets/remoteEntry.js',
        error_stack_mfe: 'http://localhost:3010/assets/remoteEntry.js',
        crm_mfe: 'http://localhost:3011/assets/remoteEntry.js',
        procurement_mfe: 'http://localhost:3012/assets/remoteEntry.js',
      },
      shared: {
        'react': { singleton: true, requiredVersion: false },
        'react-dom': { singleton: true, requiredVersion: false },
        'react-redux': { singleton: true, requiredVersion: false },
        'react-router-dom': { singleton: true, requiredVersion: false },
        '@ecommerce/shared-library': { singleton: true, requiredVersion: false, eager: true },
      }
    })
  ],
  server: {
    port: 3000,
    cors: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  preview: {
    port: 3000,
    cors: true,
  },
  build: {
    modulePreload: false,
    target: 'esnext',
    minify: false,
    cssCodeSplit: false
  }
})
