import React from 'react';
import { Routes, Route, useNavigate, useLocation } from 'react-router-dom';
import { Box, Tabs, Tab, Paper, Container } from '@mui/material';
import { ShoppingCartOutlined, MarkEmailReadRounded } from '@mui/icons-material';
import { RoleGuard } from '@ecommerce/shared-library';
import CartPage from './pages/CartPage';
import AbandonedCartsAdminPage from './pages/AbandonedCartsAdminPage';

export function CartNavigation() {
  const navigate = useNavigate();
  const location = useLocation();

  const isAbandoned = location.pathname.includes('abandoned');
  const currentTab = isAbandoned ? 1 : 0;

  const handleTabChange = (_: React.SyntheticEvent, newValue: number) => {
    if (newValue === 0) {
      navigate('/cart');
    } else {
      navigate('/cart/admin/abandoned');
    }
  };

  return (
    <Paper
      sx={{
        mb: 3,
        borderRadius: 3,
        px: 2,
        pt: 1,
        bgcolor: '#111827',
        border: '1px solid #1E293B',
      }}
    >
      <Tabs
        value={currentTab}
        onChange={handleTabChange}
        textColor="primary"
        indicatorColor="primary"
        sx={{
          '& .MuiTab-root': {
            fontWeight: 700,
            textTransform: 'none',
            fontSize: '0.95rem',
            minHeight: 48,
            color: '#94A3B8',
            '&.Mui-selected': { color: '#06B6D4' },
          },
          '& .MuiTabs-indicator': { bgcolor: '#06B6D4' },
        }}
      >
        <Tab icon={<ShoppingCartOutlined sx={{ mr: 1 }} />} iconPosition="start" label="Customer Basket & Sync" />
        <Tab icon={<MarkEmailReadRounded sx={{ mr: 1 }} />} iconPosition="start" label="Abandoned Cart Recovery (Staff)" />
      </Tabs>
    </Paper>
  );
}

export default function App() {
  return (
    <Container maxWidth="xl" sx={{ py: 2 }}>
      <CartNavigation />
      <Routes>
        <Route path="/" element={<CartPage />} />
        <Route path="/cart" element={<CartPage />} />
        <Route
          path="/admin/abandoned"
          element={
            <RoleGuard allowedRoles={['MANAGER', 'ADMIN']}>
              <AbandonedCartsAdminPage />
            </RoleGuard>
          }
        />
        <Route
          path="/cart/admin/abandoned"
          element={
            <RoleGuard allowedRoles={['MANAGER', 'ADMIN']}>
              <AbandonedCartsAdminPage />
            </RoleGuard>
          }
        />
        <Route path="*" element={<CartPage />} />
      </Routes>
    </Container>
  );
}
