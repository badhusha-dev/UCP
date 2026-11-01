# Frontend Microfrontends Specification (Vite Module Federation)

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Microfrontend Architecture & Principles

The frontend tier is implemented using **Vite Module Federation** (`@originjs/vite-plugin-federation`). Instead of maintaining a monolithic client-side application, UI modules are isolated into separate packages:

```
┌────────────────────────────────────────────────────────┐
│             Host Shell Container (:3000)               │
│  - Navigation Header, Theme, Global Redux & Router     │
└──────────┬──────────────────┬──────────────────┬───────┘
           │                  │                  │
           ▼                  ▼                  ▼
┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
│   Product MFE    │ │    Order MFE     │ │     User MFE     │
│     (:3001)      │ │     (:3002)      │ │     (:3003)      │
│ - ProductCatalog │ │ - CartDrawer     │ │ - AuthModal      │
│ - ProductDetail  │ │ - CheckoutModal  │ │ - UserProfile    │
│ - CategoryFilter │ │ - OrderHistory   │ │ - AddressManager │
└──────────────────┘ └──────────────────┘ └──────────────────┘
```

---

## 2. Remote Module Federation Configurations

### 2.1 Shell Application (`shell-app/vite.config.ts`)
The Shell acts as the host and configures remotes:
```typescript
federation({
  name: 'shell_app',
  remotes: {
    mfe_product: 'http://localhost:3001/assets/remoteEntry.js',
    mfe_order: 'http://localhost:3002/assets/remoteEntry.js',
    mfe_user: 'http://localhost:3003/assets/remoteEntry.js',
  },
  shared: ['react', 'react-dom', 'react-router-dom', '@reduxjs/toolkit', 'react-redux'],
})
```

### 2.2 Remote Expositions
- **`mfe-product`** exposes `./ProductCatalog` and `./ProductDetail`.
- **`mfe-order`** exposes `./CartDrawer`, `./CheckoutModal`, and `./OrderHistory`.
- **`mfe-user`** exposes `./AuthModal`, `./UserProfile`, and `./AddressManager`.

---

## 3. Shared Dependency Strategy & React 18 Pinned Overrides

To prevent runtime crashes such as React error #31 (`Symbol(react.transitional.element)`) or multiple dispatcher collisions:
1. Root `frontend/package.json` pins `react` and `react-dom` to `18.3.1` using `overrides`.
2. Each Vite federation configuration designates `react` and `react-dom` as shared singletons (`singleton: true`).
3. Microfrontends share a unified `@emotion` theme and Material UI 6 components without duplicate bundle overhead.

---

## 4. Cross-MFE State Management & Communication

State synchronization between microfrontends uses two complementary channels:
1. **Redux Store Distribution**: Auth tokens (`jwtToken`, `currentUser`) stored in the Shell Redux store and passed via React context and props into mounted remote components.
2. **Custom Event Bus (DOM Events)**:
   - When an item is added to the cart in `mfe-product`, it dispatches a window event:
     ```javascript
     window.dispatchEvent(new CustomEvent('ucp:cart:add', { detail: { product, quantity: 1 } }));
     ```
   - `mfe-order` listens for `ucp:cart:add`, updates cart state in local storage, and updates the badge counter in real-time.
