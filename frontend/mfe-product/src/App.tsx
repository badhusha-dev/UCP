import { Provider } from 'react-redux';
import { store } from '@ecommerce/shared-library';
import ProductPage from './pages/ProductPage';
import ProductAdminPage from './pages/ProductAdminPage';
import PricingPromotionsPage from './pricing/PricingPromotionsPage';
import StorefrontManagementPage from './storefront/StorefrontManagementPage';
import { useLocation } from 'react-router-dom';

function App() {
  const location = useLocation();
  const path = location.pathname;
  return (
    <Provider store={store}>
      {path.startsWith('/products/storefront') ? <StorefrontManagementPage />
        : path.startsWith('/products/pricing') ? <PricingPromotionsPage />
        : path.startsWith('/products/manage') ? <ProductAdminPage /> : <ProductPage />}
    </Provider>
  );
}

export default App;
