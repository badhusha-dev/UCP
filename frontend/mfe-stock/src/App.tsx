import React, { useState } from 'react';
import { Routes, Route, useNavigate, useLocation } from 'react-router-dom';
import { Box, Tabs, Tab, Paper, Container } from '@mui/material';
import { Inventory2Outlined, LockClockOutlined } from '@mui/icons-material';
import { StockAdminPage } from './pages/StockAdminPage';
import { ReservationsInspector } from './pages/ReservationsInspector';

export function StockNavigation() {
  const navigate = useNavigate();
  const location = useLocation();

  const currentTab = location.pathname.includes('reservations') ? 1 : 0;

  const handleTabChange = (_: React.SyntheticEvent, newValue: number) => {
    if (newValue === 0) {
      navigate('/stock');
    } else {
      navigate('/stock/reservations');
    }
  };

  return (
    <Paper sx={{ mb: 3, borderRadius: 3, px: 2, pt: 1, boxShadow: '0 2px 10px rgba(15, 23, 42, 0.04)' }}>
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
          },
        }}
      >
        <Tab icon={<Inventory2Outlined sx={{ mr: 1 }} />} iconPosition="start" label="Stock Levels & Movements" />
        <Tab icon={<LockClockOutlined sx={{ mr: 1 }} />} iconPosition="start" label="Active Reservations & Holds" />
      </Tabs>
    </Paper>
  );
}

export default function App() {
  return (
    <Container maxWidth="xl" sx={{ py: 2 }}>
      <StockNavigation />
      <Routes>
        <Route path="/" element={<StockAdminPage />} />
        <Route path="/reservations" element={<ReservationsInspector />} />
        <Route path="/stock/reservations" element={<ReservationsInspector />} />
        <Route path="/stock" element={<StockAdminPage />} />
        <Route path="*" element={<StockAdminPage />} />
      </Routes>
    </Container>
  );
}
